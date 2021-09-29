package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.jvm.JvmHeapAllocation;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.ir.model.IRAllocation;

/** An object allocation site in the source code. */
public class JAllocation extends ElementWithPosition<IRAllocation, JvmHeapAllocation>
implements AbstractAllocation, Targetable {
    public final String allocType;
    /** The target of the allocation (such as "x" in {@code x = new T()}). */
    private JVariable target = null;

    /**
     * Create an object allocation.
     * @param srcFile      the source file
     * @param pos          the position of the object creation
     * @param allocType   the simple type of the allocation (no package prefix)
     */
    public JAllocation(SourceFile srcFile, Position pos, String allocType) {
        super(srcFile, pos);
        this.allocType = allocType;
    }

    @Override
    public void initSymbolFromIRElement(IRAllocation irAlloc) {
        if (symbol == null) {
            matchElement = irAlloc;
            symbol = new JvmHeapAllocation(pos, srcFile.getRelativePath(), true,
                    irAlloc.getId(), irAlloc.allocatedTypeDoopId,
                    irAlloc.allocatingMethodDoopId, irAlloc.inIIB, irAlloc.isArray);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String getBareIrType() {
        return Utils.dotsToDollars(allocType);
    }

    @Override
    public String toString() {
        return "new " + allocType + "() " + getLocation() + " [target=" + target + "]";
    }

    @Override
    public void setTarget(JVariable target) {
        this.target = target;
    }

    @Override
    public JVariable getTarget() {
        return this.target;
    }
}
