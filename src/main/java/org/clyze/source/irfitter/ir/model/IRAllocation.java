package org.clyze.source.irfitter.ir.model;

import java.util.Collection;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.source.model.Utils;

/** A low-level allocation such as a "new T()" heap allocation. */
public class IRAllocation extends IRElement implements AbstractAllocation {
    /** The fully qualified name of the allocated object type. */
    public final String allocatedTypeDoopId;
    /** The id of the parent method. */
    public final String allocatingMethodDoopId;
    /** True if the allocation happens in an initializer block. */
    public final boolean inIIB;
    /** True if the allocation creates an array object. */
    public final boolean isArray;
    private final Integer sourceLine;
    private String allocatedTypeDoopId_Simple = null;

    public IRAllocation(String id, String allocatedTypeDoopId,
                        String allocatingMethodDoopId, boolean inIIB,
                        boolean isArray, Integer sourceLine) {
        super(id);
        this.allocatedTypeDoopId = allocatedTypeDoopId;
        this.allocatingMethodDoopId = allocatingMethodDoopId;
        this.inIIB = inIIB;
        this.isArray = isArray;
        this.sourceLine = sourceLine;
    }

    @Override
    public String getSimpleType() {
        if (allocatedTypeDoopId_Simple == null) {
            allocatedTypeDoopId_Simple = Utils.getSimpleType(allocatedTypeDoopId);
        }
        return allocatedTypeDoopId_Simple;
    }

    public Integer getSourceLine() {
        return this.sourceLine;
    }

    @Override
    public String toString() {
        return getId() + "@" + getSourceLine();
    }

    @Override
    public void addReferencedTypesTo(Collection<String> target) {
        if (allocatedTypeDoopId != null)
            target.add(allocatedTypeDoopId);
    }
}
