package org.clyze.source.irfitter.ir.model;

/** A method parameter/receiver. */
public class IRParameter extends IRElement {
    /** A simple parameter name (not the original source name). */
    public final String name;
    /** The method that takes this parameter. */
    public final String declaringMethodId;

    /**
     * Create a method parameter.
     * @param methodId   the method
     * @param idx        the position of the formal parameter
     */
    public IRParameter(String methodId, int idx) {
        super(methodId + "/@parameter" + idx);
        this.name = "@parameter" + idx;
        this.declaringMethodId = methodId;
    }

    public IRParameter(String methodId) {
        super(methodId + "/@this");
        this.name = "@this";
        this.declaringMethodId = methodId;
    }
}
