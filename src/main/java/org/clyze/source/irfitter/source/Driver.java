package org.clyze.source.irfitter.source;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.clyze.doop.sarif.SARIFGenerator;
import org.clyze.sarif.model.Result;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.source.irfitter.source.groovy.GroovyProcessor;
import org.clyze.source.irfitter.source.java.JavaProcessor;
import org.clyze.source.irfitter.source.kotlin.KotlinProcessor;
import org.clyze.source.irfitter.source.model.*;
import org.clyze.persistent.metadata.Configuration;
import org.clyze.persistent.metadata.FileInfo;
import org.clyze.persistent.metadata.FileReporter;
import org.clyze.persistent.metadata.Printer;
import org.clyze.persistent.model.*;
import org.zeroturnaround.zip.ZipUtil;

public class Driver {
    private final SARIFGenerator sarifGenerator;
    private final File out;

    public Driver(File db, File out, String version, boolean standalone) {
        this.sarifGenerator = new SARIFGenerator(db, out, version, standalone);
        this.out = out;
    }

    public static Collection<SourceFile> processSources(File topDir, File srcFile,
                                                        boolean debug,
                                                        boolean synthesizeTypes) {
        Collection<SourceFile> sources = new LinkedList<>();
        if (srcFile.isDirectory()) {
            File[] srcFiles = srcFile.listFiles();
            if (srcFiles == null)
                System.err.println("ERROR: could not process source directory " + srcFile.getPath());
            else
                for (File f : srcFiles)
                    sources.addAll(processSources(topDir, f, debug, synthesizeTypes));
        } else {
            String srcName = srcFile.getName().toLowerCase(Locale.ROOT);
            if (srcName.endsWith(".java")) {
                System.out.println("Found Java source: " + srcFile);
                sources.add((new JavaProcessor()).process(topDir, srcFile, debug, synthesizeTypes));
            } else if (srcName.endsWith(".groovy")) {
                System.out.println("Found Groovy source: " + srcFile);
                sources.add((new GroovyProcessor()).process(topDir, srcFile, debug, synthesizeTypes));
            } else if (srcName.endsWith(".kt")) {
                System.out.println("Found Kotlin source: " + srcFile);
                sources.add((new KotlinProcessor()).process(topDir, srcFile, debug, synthesizeTypes));
            } else if (srcName.endsWith(".jar") || srcName.endsWith(".zip")) {
                try {
                    File tmpDir = Files.createTempDirectory("extracted-sources").toFile();
                    tmpDir.deleteOnExit();
                    ZipUtil.unpack(srcFile, tmpDir);
                    sources.addAll(processSources(tmpDir, tmpDir, debug, synthesizeTypes));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sources;
    }

    public void match(Collection<IRType> irTypes, Collection<SourceFile> sources,
                      boolean debug, boolean json, boolean sarif) {
        System.out.println("Matching " + irTypes.size() + " IR types against " + sources.size() + " source files...");
        IdMapper idMapper = new IdMapper();
        int unmatched = 0;
        for (SourceFile sf : sources) {
            System.out.println("==> Matching elements in " + sf.getRelativePath());
            sf.matchTypes(idMapper, irTypes);
            unmatched += sf.reportUmatched(debug);
        }

        if (debug)
            System.out.println("* Performing fuzzy type matching for type references...");
        for (SourceFile sf : sources) {
            BasicMetadata bm = sf.getFileInfo().getElements();
            for (JType jt : sf.jTypes)
                matchTypeUsages(bm, jt, debug);
        }

        System.out.println(unmatched + " elements not matched.");

        Map<String, Collection<? extends NamedElementWithPosition<?>>> flatMapping = idMapper.get();
        process(flatMapping, sarif, debug);
        if (json)
            generateJSON(flatMapping, sources, debug);
    }

    /**
     * Match type usages against the IR types. This may not resolve all such
     * type references, e.g. compile-time-only annotations may be missed.
     * @param bm             the object to use to write the metadata
     * @param jt             the type that contains the unresolved type usages
     * @param debug          if true, show diagnostics
     */
    private void matchTypeUsages(BasicMetadata bm, JType jt, boolean debug) {
        List<TypeUsage> typeUsages = jt.typeUsages;
        if (typeUsages.isEmpty() || jt.matchElement == null)
            return;

        Set<String> irAnnotations = jt.matchElement.mp.getAnnotations();
        Set<String> irClassRefs = jt.matchElement.getTypeReferences();
        for (TypeUsage typeUsage : typeUsages) {
            if (debug)
                System.out.println("Examining type usage: " + typeUsage);
            for (String irTypeId : typeUsage.getIds()) {
                // Match type uses against local annotation uses or the global IR types.
                if (irAnnotations.contains(irTypeId) || irClassRefs.contains(irTypeId)) {
                    if (debug)
                        System.out.println("Matched use for type '" + typeUsage.type + "': " + irTypeId);
                    typeUsage.matchId = irTypeId;
                }
            }
            if (typeUsage.matchId != null)
                registerSymbol(bm, typeUsage.getUsage());
        }
    }

    private void registerSymbol(BasicMetadata bm,
                                SymbolWithDoopId symbol) {
        if (symbol instanceof org.clyze.persistent.model.Class)
            bm.classes.add((org.clyze.persistent.model.Class) symbol);
        else if (symbol instanceof Field)
            bm.fields.add((Field) symbol);
        else if (symbol instanceof Method)
            bm.methods.add((Method) symbol);
        else if (symbol instanceof MethodInvocation)
            bm.invocations.add((MethodInvocation) symbol);
        else if (symbol instanceof HeapAllocation)
            bm.heapAllocations.add((HeapAllocation) symbol);
        else if (symbol instanceof Usage)
            bm.usages.add((Usage) symbol);
        else
            System.out.println("WARNING: cannot handle symbol of type " + symbol.getClass().getName() + ": " + symbol.toJSON());
    }

    public void generateJSON(Map<String, Collection<? extends NamedElementWithPosition<?>>> mapping,
                             Collection<SourceFile> sources, boolean debug) {
        for (Map.Entry<String, Collection<? extends NamedElementWithPosition<?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("Processing id: " + doopId);
            for (NamedElementWithPosition<?> srcElem : entry.getValue()) {
                SymbolWithDoopId symbol = srcElem.getSymbol();
                if (symbol == null) {
                    System.out.println("Source element has no symbol: " + srcElem);
                    continue;
                }
                registerSymbol(srcElem.srcFile.getFileInfo().getElements(), symbol);
            }
        }
        for (SourceFile sf : sources) {
            Set<StringConstant> stringConstants = sf.getFileInfo().getElements().stringConstants;
            for (JStringConstant<?> jStrConstant : sf.stringConstants)
                stringConstants.add(jStrConstant.getStringConstant());
        }

        System.out.println("Generating JSON metadata...");
        createOutDir();

        Configuration configuration = new Configuration(new Printer(debug));
        configuration.setOutDir(out);
        FileReporter reporter = new FileReporter(configuration);
        for (SourceFile sf : sources) {
            FileInfo fileInfo = sf.getFileInfo();
            reporter.createReportFile(fileInfo);
            if (debug)
                reporter.printReportStats(fileInfo);
        }
    }

    private void createOutDir() {
        if (!out.exists())
            if (out.mkdirs())
                System.out.println("Creating new output directory: " + out);
    }

    void process(Map<String, Collection<? extends NamedElementWithPosition<?>>> mapping,
                 boolean sarif, boolean debug) {
        boolean metadataExist = sarifGenerator.metadataExist();
        if (!metadataExist) {
            if (debug)
                System.out.println("No metadata found.");
            return;
        }

        List<Result> results = new LinkedList<>();
        AtomicInteger elements = new AtomicInteger(0);

        for (Map.Entry<String, Collection<? extends NamedElementWithPosition<?>>> entry : mapping.entrySet()) {
            String doopId = entry.getKey();
            if (debug)
                System.out.println("Processing id: " + doopId);
            for (NamedElementWithPosition<?> srcElem : entry.getValue()) {
                SymbolWithDoopId symbol = srcElem.getSymbol();
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
