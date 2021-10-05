package org.clyze.source.irfitter.matcher;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to write results with optional duplicate removal.
 */
class DoopResultsWriter {
    private final boolean unique;
    private final BufferedWriter bw;
    private final Set<String> lines;

    DoopResultsWriter(boolean unique, BufferedWriter bw) {
        this.unique = unique;
        this.lines = unique ? new HashSet<>() : null;
        this.bw = bw;
    }

    public void write(String line) throws IOException {
        if (unique)
            lines.add(line);
        else
            bw.write(line);
    }

    public void flush() throws IOException {
        if (unique)
            for (String line : lines)
                bw.write(line);
    }
}
