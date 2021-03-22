package org.clyze.source.irfitter.source;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.clyze.doop.sarif.SARIFGenerator;
import org.clyze.sarif.model.Result;
import org.clyze.source.irfitter.RunResult;
import org.clyze.source.irfitter.ir.model.IRType;
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

    private final SARIFGenerator sarifGenerator;
    private final File out;
    /** If true, enable debug reports. */
    private final boolean debug;

    /**
     * Create a new driver / processing pipeline.
     * @param out          the output directory
     * @param db           the database (used in SARIF mode)
     * @param version      the results version (used in SARIF mode)
     * @param standalone   false if to be used as a library
     * @param debug        debug mode
     */
    public Driver(File out, File db, String version, boolean standalone, boolean debug) {
        this.sarifGenerator = new SARIFGenerator(db, out, version, standalone);
        this.out = out;
        this.debug = debug;
    }

    /**
     * Main entry point to read sources.
     * @param srcFile             the source file/archive/directory
     * @param debug               debug mode
     * @param synthesizeTypes     if true, attempt to synthesize erased types
     * @param lossy               if true, enable lossy heuristics
     * @return                    the processed source file objects
     */
    public static Collection<SourceFile> readSources(File srcFile, boolean debug,
                                                     boolean synthesizeTypes, boolean lossy) {
        String srcName = getName(srcFile);
        if (!srcFile.isDirectory() && (srcName.endsWith(".jar") || srcName.endsWith(".zip"))) {
            try {
                File tmpDir = Files.createTempDirectory("extracted-sources").toFile();
                tmpDir.deleteOnExit();
                ZipUtil.unpack(srcFile, tmpDir);
                return readSources(tmpDir, tmpDir, debug, synthesizeTypes, lossy);
            } catch (IOException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        } else
            return readSources(srcFile, srcFile, debug, synthesizeTypes, lossy);
    }

    private static Collection<SourceFile> readSources(File topDir, File srcFile,
                                                      boolean debug, boolean synthesizeTypes,
                                                      boolean lossy) {
        Collection<SourceFile> sources = new LinkedList<>();
        if (srcFile.isDirectory()) {
            File[] srcFiles = srcFile.listFiles();
            if (srcFiles == null)
                System.err.println("ERROR: could not process source directory " + srcFile.getPath());
            else
                for (File f : srcFiles)
                    sources.addAll(readSources(topDir, f, debug, synthesizeTypes, lossy));
        } else {
            String srcName = getName(srcFile);
            if (srcName.endsWith(".java")) {
                System.out.println("Found Java source: " + srcFile);
                sources.add((new JavaProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy));
            } else if (srcName.endsWith(".groovy")) {
                System.out.println("Found Groovy source: " + srcFile);
                sources.add((new GroovyProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy));
            } else if (srcName.endsWith(".kt")) {
                System.out.println("Found Kotlin source: " + srcFile);
                sources.add((new KotlinProcessor()).process(topDir, srcFile, debug, synthesizeTypes, lossy));
            }
        }
        return sources;
    }

    private static String getName(File srcFile) {
        return srcFile.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Main entry point that performs IR-vs-source element matching.
     * @param irTypes   the set of all IR types
     * @param sources   the set of source files
     * @param json      if true, generate JSON metadata
     * @param sarif     if true, generate SARIF results
     * @return          the result of the matching operation
     */
    public RunResult match(Collection<IRType> irTypes, Collection<SourceFile> sources,
                           boolean json, boolean sarif) {
        System.out.println("Matching " + irTypes.size() + " IR types against " + sources.size() + " source files...");
        IdMapper idMapper = new IdMapper();
        int unmatched = 0;
        for (SourceFile sf : sources) {
            addImportUsages(sf.getFileInfo().getElements(), sf);
            System.out.println("==> Matching elements in " + sf.getRelativePath());
            sf.matchTypes(idMapper, irTypes);
            unmatched += sf.reportUmatched(debug);
        }

        // Calculate the set of all referenced types.
        Set<String> allIrTypes = new HashSet<>();
        for (IRType irType : irTypes) {
            allIrTypes.add(irType.getId());
            irType.addReferencedTypesTo(allIrTypes);
        }

        if (debug)
            System.out.println("* Performing fuzzy type matching for type/field references...");
        for (SourceFile sf : sources) {
            JvmMetadata bm = sf.getFileInfo().getElements();
            for (JType jt : sf.jTypes) {
                matchTypeUsages(allIrTypes, bm, jt);
            }
        }

        System.out.println(unmatched + " elements not matched.");

        Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> flatMapping = idMapper.get();
        process(flatMapping, sarif);
        if (json)
            generateJSON(flatMapping, sources);

        return new RunResult(unmatched);
    }

    /**
     * Update the metadata with the usages calculated from "import" statements.
     * @param bm   the metadata to update
     * @param sf   the source file
     */
    private void addImportUsages(JvmMetadata bm, SourceFile sf) {
        for (Import imp : sf.imports)
            if (!imp.isAsterisk && !imp.isStatic)
                bm.usages.add(new Usage(imp.pos, sf.getRelativePath(), true, imp.name, UsageKind.TYPE));
    }

    /**
     * Match type usages against the IR types. This may not resolve all such
     * type references, e.g. compile-time-only annotations may be missed.
     * @param allIrTypes     the set of all IR types found
     * @param bm             the object to use to write the metadata
     * @param jt             the type that contains the unresolved type usages
     */
    private void matchTypeUsages(Set<String> allIrTypes, JvmMetadata bm, JType jt) {
        List<TypeUsage> typeUsages = jt.typeUsages;
        if (typeUsages.isEmpty() || jt.matchElement == null)
            return;

        Set<String> irAnnotations = jt.matchElement.mp.getAnnotations();
        Set<String> irTypeRefs = new HashSet<>();
        jt.matchElement.addReferencedTypesTo(irTypeRefs);
        for (TypeUsage typeUsage : typeUsages) {
            if (debug)
                System.out.println("Examining type usage: " + typeUsage);
            Collection<String> irTypeIds = typeUsage.getIds();
            for (String irTypeId : irTypeIds) {
                // Match type uses against local annotation uses or the global IR types.
                if (irAnnotations.contains(irTypeId) || irTypeRefs.contains(irTypeId))
                    matchTypeUsage(typeUsage, irTypeId);
            }
            if (typeUsage.matchId == null) {
                if (debug)
                    System.out.println("Type usage still unresolved, trying slow global matching: " + typeUsage + " with type ids = " + irTypeIds);
                for (String irTypeId : irTypeIds) {
                    if (allIrTypes.contains(irTypeId) || BOXED_REPRESENTATIONS.contains(irTypeId))
                        matchTypeUsage(typeUsage, irTypeId);
                }
            }
            if (typeUsage.matchId != null)
                registerSymbol(bm, typeUsage.getUsage());
            else if (debug)
                System.out.println("Type usage could not be resolved: " + typeUsage);
        }
    }

    private void matchTypeUsage(TypeUsage typeUsage, String irTypeId) {
        if (debug)
            System.out.println("Matched use for type '" + typeUsage.type + "': " + irTypeId);
        typeUsage.matchId = irTypeId;
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
        else if (symbol instanceof Usage)
            bm.usages.add((Usage) symbol);
        else
            System.out.println("WARNING: cannot handle symbol of type " + symbol.getClass().getName() + ": " + symbol.toJSON());
    }

    private void generateJSON(Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> mapping,
                              Collection<SourceFile> sources) {
        for (Map.Entry<String, Collection<? extends NamedElementWithPosition<?, ?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("Processing id: " + doopId);
            for (NamedElementWithPosition<?, ?> srcElem : entry.getValue()) {
                SymbolWithId symbol = srcElem.getSymbol();
                if (symbol == null) {
                    System.out.println("Source element has no symbol: " + srcElem);
                    continue;
                }
                registerSymbol(srcElem.srcFile.getFileInfo().getElements(), symbol);
            }
        }
        for (SourceFile sf : sources) {
            Set<JvmStringConstant> stringConstants = sf.getFileInfo().getElements().jvmStringConstants;
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

    void process(Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> mapping,
                 boolean sarif) {
        boolean metadataExist = sarifGenerator.metadataExist();
        if (!metadataExist) {
            if (debug)
                System.out.println("No metadata found.");
            return;
        }

        List<Result> results = new LinkedList<>();
        AtomicInteger elements = new AtomicInteger(0);

        for (Map.Entry<String, Collection<? extends NamedElementWithPosition<?, ?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("Processing id: " + doopId);
            for (NamedElementWithPosition<?, ?> srcElem : entry.getValue()) {
                SymbolWithId symbol = srcElem.getSymbol();
                if (symbol == null) {
                    System.out.println("Source element has no symbol: " + srcElem);
                    continue;
                }
                if (sarif)
                    sarifGenerator.processElement(metadataExist, results, symbol, elements);
            }
        }
        System.out.println("Elements processed: " + elements);

        if (sarif)
            sarifGenerator.generateSARIF(results);
    }
}
