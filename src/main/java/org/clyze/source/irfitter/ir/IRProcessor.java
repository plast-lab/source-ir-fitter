package org.clyze.source.irfitter.ir;

import org.clyze.source.irfitter.ir.bytecode.BytecodeParser;
import org.clyze.source.irfitter.ir.dex.DexParser;
import org.clyze.source.irfitter.ir.model.IRType;

import java.io.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class IRProcessor {
    public static List<IRType> processIR(File irFile, boolean debug) {
        List<IRType> irTypes = new LinkedList<>();
        if (debug)
            System.out.println("Processing IR in: " + irFile.getPath());
        if (irFile.isFile()) {
            String name = irFile.getName().toLowerCase();
            if (name.endsWith(".jar")) {
                processZipArchive(irFile, ".class", debug,
                        is -> new BytecodeParser(debug).processClass(irTypes, is));
            } else if (name.endsWith(".class")) {
                try (InputStream is = new FileInputStream(irFile)) {
                    (new BytecodeParser(debug)).processClass(irTypes, is);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else if (name.endsWith(".apk")) {
                processZipArchive(irFile, ".dex", debug,
                        is -> new DexParser(debug).processDex(irTypes, is));
            }
        } else if (irFile.isDirectory()) {
            for (File f : Objects.requireNonNull(irFile.listFiles()))
                irTypes.addAll(processIR(f, debug));
        }
        return irTypes;
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
