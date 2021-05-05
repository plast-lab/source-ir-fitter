package org.clyze.source.irfitter.source;

import com.google.common.collect.ImmutableSet;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

import org.clyze.source.irfitter.RunResult;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.source.irfitter.matcher.Aliaser;
import org.clyze.source.irfitter.matcher.DoopMatcher;
import org.clyze.source.irfitter.source.groovy.GroovyProcessor;
import org.clyze.source.irfitter.source.java.JavaProcessor;
import org.clyze.source.irfitter.source.kotlin.KotlinProcessor;
import org.clyze.source.irfitter.source.model.*;
import org.clyze.persistent.metadata.Configuration;
import org.clyze.persistent.metadata.FileInfo;
import org.clyze.persistent.metadata.Printer;
import org.clyze.persistent.metadata.jvm.JvmFileReporter;
import org.clyze.persistent.metadata.jvm.JvmMetadata;
import org.clyze.persistent.model.*;
import org.clyze.persistent.model.jvm.*;
import org.zeroturnaround.zip.ZipUtil;

/**
 * The main driver of the processing stages.
 */
public class Driver {
    /**
     * Special types that may not appear in program text but may still be used
     * due to autoboxing.
     */
    private static final Set<String> BOXED_REPRESENTATIONS = ImmutableSet.of("java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Double", "java.lang.Float", "java.lang.Integer", "java.lang.Long", "java.lang.Short");

    private final File out;
    private final File db;
    /** If true, enable debug reports. */
    private final boolean debug;
    private final Set<String> varargIrMethods;
    private final IdMapper idMapper = new IdMapper();

    /**
     * Create a new driver / processing pipeline.
     * @param out          the output directory
     * @param db           the database (used in SARIF mode)
     * @param debug        debug mode
     * @param vaIrMethods  the vararg methods found in the IR
     */
    public Driver(File out, File db, boolean debug, Set<String> vaIrMethods) {
        this.varargIrMethods = vaIrMethods;
        this.db = db;
        this.out = out;
        this.debug = debug;
    }

    /**
     * Main entry point to read sources.
     * @param srcFile             the source file/archive/directory
     * @param debug               debug mode
     * @param synthesizeTypes     if true, attempt to synthesize erased types
     * @param lossy               if true, enable lossy heuristics
     * @param aliaser             the symbol aliasing helper
     * @return                    the processed source file objects
     */
    public Collection<SourceFile> readSources(File srcFile, boolean debug,
                                              boolean synthesizeTypes, boolean lossy,
                                              Aliaser aliaser) {
        String srcName = getName(srcFile);
        if (!srcFile.isDirectory() && (srcName.endsWith(".jar") || srcName.endsWith(".zip"))) {
            try {
                File tmpDir = Files.createTempDirectory("extracted-sources").toFile();
                tmpDir.deleteOnExit();
                ZipUtil.unpack(srcFile, tmpDir);
                return readSources(tmpDir, tmpDir, debug, synthesizeTypes, lossy, aliaser);
            } catch (IOException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        } else
            return readSources(srcFile, srcFile, debug, synthesizeTypes, lossy, aliaser);
    }

    private Collection<SourceFile> readSources(File topDir, File srcFile,
                                               boolean debug, boolean synthesizeTypes,
                                               boolean lossy, Aliaser aliaser) {
        Collection<SourceFile> sources = new ArrayList<>();
        if (srcFile.isDirectory()) {
            File[] srcFiles = srcFile.listFiles();
            if (srcFiles == null)
                System.err.println("ERROR: could not process source directory " + srcFile.getPath());
            else
                for (File f : srcFiles)
                    sources.addAll(readSources(topDir, f, debug, synthesizeTypes, lossy, aliaser));
        } else {
            String srcName = getName(srcFile);
            if (srcName.endsWith(".java")) {
                System.out.println("Found Java source: " + srcFile);
                sources.add((new JavaProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy, aliaser, varargIrMethods));
            } else if (srcName.endsWith(".groovy")) {
                System.out.println("Found Groovy source: " + srcFile);
                sources.add((new GroovyProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy, aliaser, varargIrMethods));
            } else if (srcName.endsWith(".kt")) {
                System.out.println("Found Kotlin source: " + srcFile);
                sources.add((new KotlinProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy, aliaser, varargIrMethods));
            }
        }
        return sources;
    }

    private static String getName(File srcFile) {
        return srcFile.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Main entry point that performs IR-vs-source element matching.
     * @param irTypes            the set of all IR types
     * @param sources            the set of source files
     * @param json               if true, generate JSON metadata
     * @param sarif              if true, translate SARIF results
     * @param resolveInvocations if true, resolve invocation targets
     * @param resolveVars        if true, resolve variables from Doop facts
     * @param translateResults   if true, map Doop results to sources
     * @param aliaser            the symbol aliasing helper
     * @param relVars            the column-variable relation spec
     * @return                   the result of the matching operation
     */
    public RunResult match(Collection<IRType> irTypes, Collection<SourceFile> sources,
                           boolean json, boolean sarif,
                           boolean resolveInvocations, boolean resolveVars,
                           boolean translateResults, Aliaser aliaser, String[] relVars) {
        System.out.println("Matching " + irTypes.size() + " IR types against " + sources.size() + " source files...");
        int unmatched = 0;
        for (SourceFile sf : sources) {
            addImportUses(sf.getJvmMetadata(), sf);
            System.out.println("==> Matching elements in " + sf.getRelativePath());
            sf.matcher.matchTypes(idMapper, irTypes);
            unmatched += sf.reportUmatched(debug);
        }

        // Calculate the set of all referenced types.
        Set<String> allIrTypes = new HashSet<>();
        for (IRType irType : irTypes) {
            allIrTypes.add(irType.getId());
            irType.addReferencedTypesTo(allIrTypes);
        }

        if (resolveInvocations) {
            if (debug)
                System.out.println("Trying to (statically) resolve invocation targets...");
            resolveInvocationTargets(sources, irTypes);
        }

        if (debug)
            System.out.println("* Matching type/field/variable references...");
        for (SourceFile sf : sources) {
            JvmMetadata bm = sf.getJvmMetadata();
            for (JType jt : sf.jTypes) {
                matchTypeUses(allIrTypes, bm, jt);
                processElementUses(bm, jt);
            }
        }

        if (resolveVars) {
            DoopMatcher doopMatcher = new DoopMatcher(db, debug, idMapper, aliaser, relVars);
            doopMatcher.resolveDoopVariables();
            if (translateResults)
                doopMatcher.translateResults();

        }

        System.out.println(unmatched + " elements not matched.");

        Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> flatMapping = idMapper.get();
        if (sarif)
            (new DoopSARIFGenerator(db, out, "1.0", false, flatMapping, debug)).process();
        if (json)
            generateJSON(flatMapping, sources);

        if (debug)
            idMapper.printStats(sources);

        return new RunResult(unmatched);
    }

    private void processElementUses(JvmMetadata bm, JType jt) {
        for (JMethod srcMethod : jt.methods)
            for (ElementUse elemUse : srcMethod.elementUses)
                registerSymbol(bm, elemUse.getUse());
    }

    private void resolveInvocationTargets(Collection<SourceFile> sources, Collection<IRType> irTypes) {
        Map<String, IRType> irTypeLookup = new HashMap<>();
        for (IRType irType : irTypes)
            irTypeLookup.put(irType.getId(), irType);
        List<JMethodInvocation> srcInvos = new ArrayList<>();
        for (SourceFile sf : sources) {
            for (JType srcType : sf.jTypes) {
                IRType irType = srcType.matchElement;
                if (irType == null)
                    continue;
                for (JMethod srcMethod : srcType.methods) {
                    for (JMethodInvocation srcInvo : srcMethod.invocations) {
                        IRMethodInvocation irInvo = srcInvo.matchElement;
                        if (irInvo == null)
                            continue;
                        srcInvos.add(srcInvo);
                    }
                }
            }
        }

        for (JMethodInvocation srcInvo : srcInvos) {
            IRMethodInvocation irInvo = srcInvo.matchElement;
            JvmMethodInvocation jvmInvo = srcInvo.symbol;
            String irTypeName = irInvo.targetType;
            String retType = irInvo.targetReturnType;
            String name = irInvo.getMethodName();
            String paramTypes = irInvo.targetParamTypes;
            // If resolution fails, use the original low-level signature.
            if (!resolveTarget(irTypeLookup, jvmInvo, irTypeLookup.get(irTypeName), retType, name, paramTypes)) {
                jvmInvo.targetMethodId = genMethodId(irTypeName, retType, name, paramTypes);
                if (debug)
                    System.out.println("Invocation resolution failed, using: " + jvmInvo.targetMethodId);
            }
            if (debug)
                System.out.println("Resolved: " + irInvo + " => " + jvmInvo.targetMethodId);
        }
    }

    private static boolean resolveTarget(Map<String, IRType> irTypes,
                                         JvmMethodInvocation jvmInvo, IRType irType,
                                         String retType, String name, String paramTypes) {
        if (irType == null)
            return false;
        String irTypeName = irType.getId();
        if (irType.declaresMethod(retType, name, paramTypes)) {
            jvmInvo.targetMethodId = genMethodId(irTypeName, retType, name, paramTypes);
            return true;
        } else if (!irTypeName.equals("java.lang.Object"))
            for (String superType : irType.superTypes) {
                if (resolveTarget(irTypes, jvmInvo, irTypes.get(superType), retType, name, paramTypes))
                    return true;
            }
        return false;
    }

    private static String genMethodId(String type, String retType, String name, String paramTypes) {
        return "<" + type + ": " + retType + " " + name + "(" + paramTypes + ")>";
    }

    /**
     * Update the metadata with the uses calculated from "import" statements.
     * @param bm   the metadata to update
     * @param sf   the source file
     */
    private void addImportUses(JvmMetadata bm, SourceFile sf) {
        for (Import imp : sf.imports)
            if (!imp.isAsterisk && !imp.isStatic)
                bm.usages.add(new Usage(imp.pos, sf.getRelativePath(), true, imp.getUniqueId(sf), imp.name, UsageKind.TYPE));
    }

    /**
     * Match type uses against the IR types. This may not resolve all such
     * type references, e.g. compile-time-only annotations may be missed.
     * @param allIrTypes     the set of all IR types found
     * @param bm             the object to use to write the metadata
     * @param jt             the type that contains the unresolved type uses
     */
    private void matchTypeUses(Set<String> allIrTypes, JvmMetadata bm, JType jt) {
        List<TypeUse> typeUses = jt.typeUses;
        if (typeUses.isEmpty() || jt.matchElement == null)
            return;

        Set<String> irAnnotations = jt.matchElement.mp.getAnnotations();
        Set<String> irTypeRefs = new HashSet<>();
        jt.matchElement.addReferencedTypesTo(irTypeRefs);
        for (TypeUse typeUse : typeUses) {
            if (debug)
                System.out.println("Examining type use: " + typeUse);
            Collection<String> irTypeIds = typeUse.getIds();
            for (String irTypeId : irTypeIds) {
                // Match type uses against local annotation uses or the global IR types.
                if (irAnnotations.contains(irTypeId) || irTypeRefs.contains(irTypeId))
                    matchTypeUse(typeUse, irTypeId);
            }
            if (typeUse.referenceId == null) {
                if (debug)
                    System.out.println("Type use still unresolved, trying slow global matching: " + typeUse + " with type ids = " + irTypeIds);
                for (String irTypeId : irTypeIds) {
                    if (allIrTypes.contains(irTypeId) || BOXED_REPRESENTATIONS.contains(irTypeId))
                        matchTypeUse(typeUse, irTypeId);
                }
            }
            if (typeUse.referenceId != null)
                registerSymbol(bm, typeUse.getUse());
            else if (debug)
                System.out.println("Type use could not be resolved: " + typeUse);
        }
    }

    private void matchTypeUse(TypeUse typeUse, String irTypeId) {
        if (debug)
            System.out.println("Matched use for type '" + typeUse.type + "': " + irTypeId);
        typeUse.referenceId = irTypeId;
    }

    private void registerSymbol(JvmMetadata bm, SymbolWithId symbol) {
        if (symbol instanceof JvmClass)
            bm.jvmClasses.add((JvmClass) symbol);
        else if (symbol instanceof JvmField)
            bm.jvmFields.add((JvmField) symbol);
        else if (symbol instanceof JvmMethod)
            bm.jvmMethods.add((JvmMethod) symbol);
        else if (symbol instanceof JvmMethodInvocation)
            bm.jvmInvocations.add((JvmMethodInvocation) symbol);
        else if (symbol instanceof JvmHeapAllocation)
            bm.jvmHeapAllocations.add((JvmHeapAllocation) symbol);
        else if (symbol instanceof JvmVariable)
            bm.jvmVariables.add((JvmVariable) symbol);
        else if (symbol instanceof Usage)
            bm.usages.add((Usage) symbol);
        else if (symbol instanceof SymbolAlias)
            bm.aliases.add((SymbolAlias) symbol);
        else
            System.out.println("WARNING: cannot handle symbol of type " + symbol.getClass().getName() + ": " + symbol.toJSON());
    }

    private void generateJSON(Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> mapping,
                              Collection<SourceFile> sources) {
        for (Map.Entry<String, Collection<? extends NamedElementWithPosition<?, ?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("[JSON] Processing id: " + doopId);
            for (NamedElementWithPosition<?, ?> srcElem : entry.getValue()) {
                SymbolWithId symbol = srcElem.getSymbol();
                if (symbol == null) {
                    System.out.println("Source element has no symbol: " + srcElem);
                    continue;
                }
                registerSymbol(srcElem.srcFile.getJvmMetadata(), symbol);
            }
        }
        for (SourceFile sf : sources) {
            Set<JvmStringConstant> stringConstants = sf.getJvmMetadata().jvmStringConstants;
            for (JStringConstant<?> jStrConstant : sf.stringConstants)
                stringConstants.add(jStrConstant.getStringConstant());
        }

        System.out.println("Generating JSON metadata...");
        createOutDir();

        Configuration configuration = new Configuration(new Printer(debug));
        configuration.setOutDir(out);
        for (SourceFile sf : sources) {
            FileInfo fileInfo = sf.getFileInfo();
            JvmFileReporter reporter = new JvmFileReporter(configuration, fileInfo);
            reporter.createReportFile(fileInfo.getOutputFilePath());
            if (debug)
                reporter.printReportStats();
        }
    }

    private void createOutDir() {
        if (!out.exists())
            if (out.mkdirs())
                System.out.println("Creating new output directory: " + out);
    }
}
