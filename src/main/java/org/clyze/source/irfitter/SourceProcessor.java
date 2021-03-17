package org.clyze.source.irfitter;

import java.io.File;
import org.clyze.source.irfitter.source.model.SourceFile;

/** Source code processor interface. */
public interface SourceProcessor {
    /**
     * The main entry point for source processing.
     * @param topDir    the source file option or top-level directory of the
     *                  sources (so that full paths can be made relative to it)
     * @param srcFile   the Groovy source file to process
     * @param debug     if true, run extra debug code
     * @param synthesizeTypes if true, try to synthesize high-level types from source/IR types
     * @param lossy     if true, enable lossy heuristics
     * @return          the source file object
     */
    SourceFile process(File topDir, File srcFile, boolean debug, boolean synthesizeTypes, boolean lossy);
}
