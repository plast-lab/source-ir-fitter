package org.clyze.source.irfitter.base;

/** The information shared by all allocations that helps in their matching. */
public interface AbstractAllocation {
    /**
     * The simple (no package prefix) type of the allocated object.
     * @return  the simple type
     */
    String getSimpleType();
}
