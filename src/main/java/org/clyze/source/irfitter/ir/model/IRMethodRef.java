package org.clyze.source.irfitter.ir.model;

/** A method reference in bytecode. */
public class IRMethodRef extends IRElement {
    /** The method id. */
    public final String methodId;
    /** The method name. */
    public final String name;
    /** The source line metadata (if available in the IR). */
    private final Integer sourceLine;

    /**
     * Create an IR method reference.
     * @param id         the unqiue element id
     * @param methodId   the method id
     * @param name       the simple name of the method
     * @param sourceLine the source line (or null if the IR lacks this metadata)
     */
    public IRMethodRef(String id, String methodId, String name, Integer sourceLine) {
        super(id);
        this.methodId = methodId;
        this.name = name;
        this.sourceLine = sourceLine;
    }

    @Override
    public String toString() {
        return "METHOD-REFERENCE: [" + id + "]: " + name + "@" + sourceLine + "(" + methodId + ")";
    }
}
