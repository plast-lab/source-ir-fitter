package org.clyze.source.irfitter.ir.model;

/**
 * A method parameter/receiver/local variable. Note that IR "variables" are
 * only defined in relation to an IR that supports such constructs (such as Dex
 * registers or Jimple variables).
 */
public class IRVariable extends IRElement {
    /** A simple parameter name (not the original source name). */
    public final String name;
    /** The method that takes this parameter. */
    public final String declaringMethodId;

    public IRVariable(String id, String name, String declaringMethodId) {
        super(id);
        this.name = name;
        this.declaringMethodId = declaringMethodId;
    }

    /**
     * Create a method parameter.
     * @param methodId   the method
     * @param idx        the position of the formal parameter
     */
    public static IRVariable newParam(String methodId, int idx) {
        return new IRVariable(methodId + "/@parameter" + idx, "@parameter" + idx, methodId);
    }

    public static IRVariable newThis(String methodId) {
        return new IRVariable(methodId + "/@this", "@this", methodId);
    }
}
