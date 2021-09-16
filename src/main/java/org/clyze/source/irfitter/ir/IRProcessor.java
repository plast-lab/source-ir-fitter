package org.clyze.source.irfitter.ir;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.clyze.source.irfitter.ir.bytecode.BytecodeParser;
import org.clyze.source.irfitter.ir.dex.DexParser;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.source.irfitter.source.Driver;

public abstract class IRProcessor {
    protected final boolean debug;
    protected final boolean enterMethods;
    protected final Set<String> varArgMethods;

    protected IRProcessor(boolean debug, boolean enterMethods, Set<String> varArgMethods) {
        this.debug = debug;
        this.enterMethods = enterMethods;
        this.varArgMethods = varArgMethods;
    }

    public static List<IRType> processIR(Set<String> varArgMethods, File irFile,
                                         boolean debug, boolean enterMethods) {
        List<IRType> irTypes = new ArrayList<>();
        if (debug)
            System.out.println("Processing IR in: " + irFile.getPath());
        if (irFile.isFile()) {
            String name = irFile.getName().toLowerCase();
            if (name.endsWith(".jar")) {
                processZipArchive(irFile, ".class", debug,
                        is -> new BytecodeParser(debug, enterMethods, varArgMethods).processClass(irTypes, is));
            } else if (name.endsWith(".class")) {
                try (InputStream is = new FileInputStream(irFile)) {
                    (new BytecodeParser(debug, enterMethods, varArgMethods)).processClass(irTypes, is);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else if (name.endsWith(".apk")) {
                processZipArchive(irFile, ".dex", debug,
                        is -> new DexParser(debug, enterMethods, varArgMethods).processDex(irTypes, is));
            } else if (name.endsWith(".war")) {
                try {
                    File tmpDir = Driver.extractZipToTempDir("war-ir", irFile);
                    processIRDir(irTypes, varArgMethods, tmpDir, debug, enterMethods);
                } catch (IOException ex) {
                    System.err.println("ERROR: failed to extract " + name);
                }
            } else
                System.err.println("ERROR: unknown IR file type: " + name);
        } else if (irFile.isDirectory())
            processIRDir(irTypes, varArgMethods, irFile, debug, enterMethods);
        return irTypes;
    }

    private static void processIRDir(List<IRType> irTypes, Set<String> varArgMethods,
                                     File irDir, boolean debug, boolean enterMethods) {
        for (File f : Objects.requireNonNull(irDir.listFiles()))
            irTypes.addAll(processIR(varArgMethods, f, debug, enterMethods));
    }

    private static void processZipArchive(File irFile, String ext, boolean debug,
                                          Consumer<InputStream> processor) {
        try (ZipFile zf = new ZipFile(irFile)) {
            Enumeration<? extends ZipEntry> zfEntries = zf.entries();
            while (zfEntries.hasMoreElements()) {
                ZipEntry entry = zfEntries.nextElement();
                if (entry.getName().endsWith(ext)) {
                    if (debug)
                        System.out.println("Reading " + entry.getName());
                    processor.accept(zf.getInputStream(entry));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
