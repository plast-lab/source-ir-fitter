package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;
import org.clyze.persistent.model.jvm.JvmHeapAllocation;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.ir.model.IRAllocation;

/** An object allocation site in the source code. */
public class JAllocation extends NamedElementWithPosition<IRAllocation>
implements AbstractAllocation, UsageGen {
    public final String simpleType;

    /**
     * Create an object allocation.
     * @param srcFile      the source file
     * @param pos          the position of the object creation
     * @param simpleType   the simple type of the allocation (no package prefix)
     */
    public JAllocation(SourceFile srcFile, Position pos, String simpleType) {
        super(srcFile, pos);
        this.simpleType = simpleType;
    }

    @Override
    public void initSymbolFromIRElement(IRAllocation irAlloc) {
        if (symbol == null) {
            matchElement = irAlloc;
            symbol = new JvmHeapAllocation(pos, srcFile.getRelativePath(),
                    irAlloc.getId(), irAlloc.allocatedTypeDoopId,
                    irAlloc.allocatingMethodDoopId, irAlloc.inIIB, irAlloc.isArray);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String getSimpleType() {
        return simpleType;
    }

    @Override
    public String toString() {
        return "new " + simpleType + "()";
    }

    @Override
    public Usage getUsage() {
        if (symbol == null)
            return null;
        JvmHeapAllocation alloc = (JvmHeapAllocation) symbol;
        return new Usage(symbol.getPosition(), symbol.getSourceFileName(), alloc.getAllocatedTypeId(), UsageKind.TYPE);
    }
}
