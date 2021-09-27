package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.ir.model.IRFieldAccess;

/** A field read/write in the source code. */
public class JFieldAccess extends ElementWithPosition<IRFieldAccess, Usage> {
    /** The type of the field access (read/write). */
    public final AccessType accessType;
    /** The field name. */
    public final String fieldName;

    /**
     * Create a field access representation.
     * @param srcFile     the source file
     * @param pos         the source code position
     * @param accessType  the type of the field access
     * @param fieldName   the field name
     */
    public JFieldAccess(SourceFile srcFile, Position pos, AccessType accessType, String fieldName) {
        super(srcFile, pos);
        this.accessType = accessType;
        this.fieldName = fieldName;
    }

    @Override
    public void initSymbolFromIRElement(IRFieldAccess irElement) {
        if (symbol == null) {
            matchElement = irElement;
            symbol = new Usage(pos, srcFile.getRelativePath(), true, irElement.getId(),
                    irElement.fieldId, accessType.kind);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String toString() {
        return "FIELD-" + accessType.name() + ": " + fieldName + getLocation();
    }
}
