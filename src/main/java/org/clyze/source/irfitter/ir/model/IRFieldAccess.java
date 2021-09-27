package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.AccessType;

public class IRFieldAccess extends IRElement {
    /** If true, this is a field read; otherwise this is a field write. */
    public final AccessType accessType;
    /** The field id. */
    public final String fieldId;
    /** The field name. */
    public final String fieldName;
    /** The field type. */
    public final String fieldType;

    public IRFieldAccess(String id, String fieldId, String fieldName, String fieldType, AccessType accessType) {
        super(id);
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.accessType = accessType;
    }

    @Override
    public String toString() {
        return "FIELD-" + accessType.name() + ": " + fieldId;
    }
}
