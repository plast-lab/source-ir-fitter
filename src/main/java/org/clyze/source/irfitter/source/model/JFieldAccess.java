package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.persistent.model.Usage;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.ir.model.IRField;
import org.clyze.source.irfitter.ir.model.IRFieldAccess;
import org.clyze.source.irfitter.ir.model.IRType;

/** A field read/write in the source code. */
public class JFieldAccess extends ElementWithPosition<IRFieldAccess, Usage> implements FuzzyTypes {
    /** The type of the field access (read/write). */
    public final AccessType accessType;
    /** The type name if this is a static field access. */
    public final String staticTypeName;
    /** The field name. */
    public final String fieldName;
    /** The source field (optional). */
    public final JField target;

    /**
     * Create a field access representation.
     * @param srcFile     the source file
     * @param pos         the source code position
     * @param accessType  the type of the field access
     * @param staticTypeName the name of the type (if this is a static field)
     * @param fieldName   the field name
     * @param target      the target source field (may be null)
     */
    public JFieldAccess(SourceFile srcFile, Position pos, AccessType accessType,
                        String staticTypeName, String fieldName, JField target) {
        super(srcFile, pos);
        this.accessType = accessType;
        this.staticTypeName = staticTypeName;
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
        return "field-" + accessType.name().toLowerCase(Locale.ROOT) + ":: " +
                fieldName + getLocation() +
                " [staticTypeName=" + staticTypeName + ", target=" + target + "]";
    }

    private Usage getUsageWith(String id, String fieldId) {
        return new Usage(pos, srcFile.getRelativePath(), true, srcFile.artifact, id, fieldId, accessType.kind);
    }

    @Override
    public SymbolWithId generatePartialMetadata(Map<String, IRType> irTypeLookup) {
        String targetId;
        if (srcFile.debug)
            System.out.println("Generating partial metadata for field access: " + this);
        targetId = (target != null && target.hasBeenMatched()) ?
                target.matchElement.getId() : tryStaticAccessLookup(irTypeLookup);
        if (targetId != null)
            symbol = getUsageWith("FieldAccess" + getLocation(), targetId);
        else
            System.out.println("ERROR: Cannot generate partial metadata for field access: " + this);
        return symbol;
    }

    /**
     * Heuristic: if this is a static field access and we have a static type name,
     * attempt to resolve it fuzzily. If this works, look up the field in the IR.
     * @param irTypes    the id-to-IR-type mapping to use
     * @return           the IR field id to use (if look-up succeeds)
     */
    private String tryStaticAccessLookup(Map<String, IRType> irTypes) {
        if (staticTypeName != null) {
            if (srcFile.debug)
                System.out.println("Attempting to resolve static type: " + staticTypeName);
            Collection<String> typeIds = resolveType(staticTypeName);
            if (typeIds.size() == 1) {
                IRType irType = irTypes.get(typeIds.iterator().next());
                if (irType != null)
                    try {
                        Optional<IRField> fld0 = irType.fields.stream().filter(fld -> fld.name.equals(fieldName)).findAny();
                        if (fld0.isPresent()) {
                            IRField irField = fld0.get();
                            if (srcFile.debug)
                                System.out.println("Found matching field: " + irField);
                            return irField.getId();
                        }
                    } catch (Exception e) {
                        System.out.println("ERROR: static field access lookup failed.");
                    }
            }
        }
        return null;
    }

    @Override
    public SourceFile getSourceFile() {
        return srcFile;
    }
}
