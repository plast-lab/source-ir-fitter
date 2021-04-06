package org.clyze.source.irfitter.ir.model;

/** A method parameter. */
public class IRParameter extends IRElement {
    /** A simple parameter name (not the original source name). */
    public final String name;
    /** The method that takes this parameter. */
    public final String declaringMethodId;

    public IRParameter(String methodId, int idx) {
        super(methodId + "/@parameter" + idx);
        this.name = "@parameter" + idx;
        this.declaringMethodId = methodId;
    }
}
