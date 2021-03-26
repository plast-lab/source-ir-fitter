package org.clyze.source.irfitter.source.model;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.clyze.persistent.metadata.FileInfo;
import org.clyze.persistent.metadata.jvm.JvmMetadata;
import org.clyze.source.irfitter.matcher.Matcher;

/**
 * This is the source code representation that is responsible for matching
 * source elements against IR elements.
 *
 * Many elements are matched inside groups (e.g. field accesses are grouped by
 * name) for more precise matching and to localize matching failures.
 */
public class SourceFile {
    /** A parent directory that should be used to form relative paths. */
    public final File topDir;
    /** The parsed source file. */
    public final File file;
    /** The package name declared in the top of the source file. Default unnamed package is "". */
    public String packageName = "";
    /** The import declarations. */
    public final List<Import> imports = new LinkedList<>();
    /** List of visited types. */
    public final Set<JType> jTypes = new HashSet<>();
    /** List of string constants that may be inlined and should be preserved. */
    public final List<JStringConstant<?>> stringConstants = new LinkedList<>();
    /** Debugging flag, set by command-line option. */
    public final boolean debug;
    /**
     * If true, (partial) source types and (fully-qualified but erased) IR
     * types will be combined into a single more informative representation.
     */
    public final boolean synthesizeTypes;
    private FileInfo cachedFileInfo = null;
    private String cachedRelativePath = null;
    public final Matcher matcher;

    public SourceFile(File topDir, File file, boolean debug, boolean synthesizeTypes, boolean lossy) {
        this.topDir = topDir;
        this.file = file;
        this.debug = debug;
        this.synthesizeTypes = synthesizeTypes;
        this.matcher = new Matcher(this, lossy, debug);
    }

    /**
     * Returns the object needed by the metadata library.
     * @return  the FileInfo representation of this source file
     */
    public FileInfo getFileInfo() {
        if (cachedFileInfo == null) {
            try {
                String inputName = file.getName();
                String inputFilePath = file.getCanonicalPath();
                JvmMetadata elements = new JvmMetadata();
                cachedFileInfo = new FileInfo(packageName == null || "".equals(packageName) ? "" : packageName + ".", inputName, inputFilePath, "", elements);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cachedFileInfo;
    }

    /**
     * Returns the path of this source file, relative to the "top directory".
     * @return the relative path of the source file
     */
    public String getRelativePath() {
        if (cachedRelativePath == null) {
            try {
                String fullPath = file.getCanonicalPath();
                String topPath = topDir.getCanonicalPath();
                if (fullPath.startsWith(topPath) && fullPath.length() > topPath.length())
                    cachedRelativePath = fullPath.substring(topPath.length() + File.separator.length());
                else {
                    System.out.println("WARNING: path " + fullPath + " not under " + topPath);
                    cachedRelativePath = fullPath;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cachedRelativePath;
    }

    public int reportUmatched(boolean debug) {
        int unmatched = 0;
        for (JType jt : jTypes) {
            if (jt.matchId == null) {
                unmatched++;
                if (debug)
                    System.out.println("Unmatched type: " + jt.getFullyQualifiedName(packageName) + " (" + jt + ")");
            }
            for (JField jf : jt.fields)
                if (jf.matchId == null) {
                    unmatched++;
                    if (debug)
                        System.out.println("Unmatched field: " + jf.toString());
                }
            for (JMethod jm : jt.methods)
                // Some methods are optimistically created for sources and
                // may not really exist in IR, so ignore their match failures
                if (jm.matchId == null && !jm.isSpecialInitializer()) {
                    unmatched++;
                    if (debug) {
                        System.out.println("Unmatched method: " + jm.toString());
//                        jm.getIds().forEach(System.out::println);
                    }
                }
        }
        return unmatched;
    }

    @Override
    public String toString() {
        return this.getRelativePath();
    }
}

