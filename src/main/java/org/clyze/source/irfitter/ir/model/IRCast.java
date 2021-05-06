package org.clyze.source.irfitter.ir.model;

/**
 * A type casting operation in the IR (bytecode's checkcast or check-cast in Dex).
 */
public class IRCast extends IRElement {
    /** The method id. */
    public final String methodId;
    /** The type cast. */
    public final String type;
    /** The source line metadata (if available in the IR). */
    private final Integer sourceLine;

    public IRCast(String id, String methodId, String type, Integer sourceLine) {
        super(id);
        this.methodId = methodId;
        this.type = type;
        this.sourceLine = sourceLine;
    }

    @Override
    public String toString() {
        return getId() + ": (" + type + ")";
    }
}
