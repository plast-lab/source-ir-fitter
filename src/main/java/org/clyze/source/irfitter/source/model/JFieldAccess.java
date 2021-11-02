package org.clyze.source.irfitter.source.model;

import java.util.Locale;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.persistent.model.Usage;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.ir.model.IRFieldAccess;

/** A field read/write in the source code. */
public class JFieldAccess extends ElementWithPosition<IRFieldAccess, Usage> {
    /** The type of the field access (read/write). */
    public final AccessType accessType;
    /** The field name. */
    public final String fieldName;
    /** The source field (optional). */
    public final JField target;

    /**
     * Create a field access representation.
     * @param srcFile     the source file
     * @param pos         the source code position
     * @param accessType  the type of the field access
     * @param fieldName   the field name
     * @param target      the target source field (may be null)
     */
    public JFieldAccess(SourceFile srcFile, Position pos, AccessType accessType,
                        String fieldName, JField target) {
        super(srcFile, pos);
        this.accessType = accessType;
        this.fieldName = fieldName;
        this.target = target;
    }

    @Override
    public void initSymbolFromIRElement(IRFieldAccess irElement) {
        if (symbol == null) {
            matchElement = irElement;
            symbol = getUsageWith(irElement.getId(), irElement.fieldId);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String toString() {
        return "field-" + accessType.name().toLowerCase(Locale.ROOT) + ":: " + fieldName + getLocation();
    }

    private Usage getUsageWith(String id, String fieldId) {
        return new Usage(pos, srcFile.getRelativePath(), true, id, fieldId, accessType.kind);
    }

    @Override
    public SymbolWithId generatePartialMetadata() {
        if (target != null && target.hasBeenMatched()) {
            System.out.println("Generating partial metadata for field access: " + this);
            symbol = getUsageWith("FieldAccess" + getLocation(), target.matchElement.getId());
        } else
            System.out.println("ERROR: Cannot generate partial metadata for field access: " + this);
        return symbol;
    }
}
