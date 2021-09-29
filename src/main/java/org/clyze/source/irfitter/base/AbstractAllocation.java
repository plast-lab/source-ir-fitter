package org.clyze.source.irfitter.base;

/** The information shared by all allocations that helps in their matching. */
public interface AbstractAllocation {
    /**
     * The bare (no package prefix) IR type of the allocated object.
     * @return  an IR type suffix
     */
    String getBareIrType();
}
