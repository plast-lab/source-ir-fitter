package org.clyze.source.irfitter;

/**
 * The results of a run.
 */
public class RunResult {
    /** The number of unmatched elements. */
    public final int unmatched;

    public RunResult(int unmatched) {
        this.unmatched = unmatched;
    }
}
