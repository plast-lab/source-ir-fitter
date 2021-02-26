package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Usage;

/**
 * This interface characterizes code elements that generate usage metadata
 * in addition to identification metadata.
 */
public interface UsageGen {
    /**
     * Generate the Usage metadata.
     * @return  the usage metadata for this element
     */
    Usage getUsage();
}
