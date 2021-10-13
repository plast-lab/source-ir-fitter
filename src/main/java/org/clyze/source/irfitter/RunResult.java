package org.clyze.source.irfitter;

import org.clyze.source.irfitter.source.model.IdMapper;

/**
 * The results of a run.
 */
public class RunResult {
    /** The number of unmatched elements. */
    public final int unmatched;
    public final IdMapper idMapper;

    public RunResult(int unmatched, IdMapper idMapper) {
        this.unmatched = unmatched;
        this.idMapper = idMapper;
    }
}
