package org.clyze.source.irfitter.base;

import org.clyze.persistent.model.UsageKind;

/** The type of access to a field (read/write). */
public enum AccessType {
    READ(UsageKind.DATA_READ, "read-field-"),
    WRITE(UsageKind.DATA_WRITE, "write-field-");

    public final UsageKind kind;
    public final String fieldAccessId;

    AccessType(UsageKind kind, String fieldAccessId) {
        this.kind = kind;
        this.fieldAccessId = fieldAccessId;
    }

    static AccessType fromUsageKind(UsageKind kind) {
        switch (kind) {
            case DATA_READ:
                return READ;
            case DATA_WRITE:
                return WRITE;
            default:
                throw new RuntimeException("Internal error: usage kind does not correspond to an access type");
        }
    }
}
