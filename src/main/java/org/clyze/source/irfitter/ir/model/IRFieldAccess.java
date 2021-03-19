package org.clyze.source.irfitter.ir.model;

public class IRFieldAccess extends IRElement {
    /** If true, this is a field read; otherwise this is a field write. */
    public final boolean read;
    /** The field id. */
    public final String fieldId;
    /** The field name. */
    public final String fieldName;
    /** The field type. */
    public final String fieldType;

    public IRFieldAccess(String id, String fieldId, String fieldName, String fieldType, boolean read) {
        super(id);
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.read = read;
    }

    @Override
    public String toString() {
        return "FIELD-" + (read ? "READ" : "WRITE") + ": " + fieldId;
    }
}
