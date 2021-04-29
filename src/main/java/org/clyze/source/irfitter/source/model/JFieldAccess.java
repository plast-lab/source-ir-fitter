package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;
import org.clyze.source.irfitter.ir.model.IRFieldAccess;

/** A field read/write in the source code. */
public class JFieldAccess extends NamedElementWithPosition<IRFieldAccess, Usage> {
    /** If true, this is a field read; otherwise this is a field write. */
    public final boolean read;
    /** The field name. */
    public final String fieldName;

    /**
     * Create a field access representation.
     * @param srcFile     the source file
     * @param pos         the source code position
     * @param read        if true, the field is read, otherwise it is written
     * @param fieldName   the field name
     */
    public JFieldAccess(SourceFile srcFile, Position pos, boolean read, String fieldName) {
        super(srcFile, pos);
        this.read = read;
        this.fieldName = fieldName;
    }

    @Override
    public void initSymbolFromIRElement(IRFieldAccess irElement) {
        if (symbol == null) {
            matchElement = irElement;
            String useId = this.toString();
            symbol = new Usage(pos, srcFile.getRelativePath(), true, useId,
                    irElement.fieldId, read ? UsageKind.DATA_READ : UsageKind.DATA_WRITE);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String toString() {
        return "FIELD-" + (read ? "READ" : "WRITE") + ": " + fieldName + getLocation();
    }
}
