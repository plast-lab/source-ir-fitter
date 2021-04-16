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

    /**
     * Base constructor.
     * @param id                     the unique id of the variable
     * @param name                   the name of the variable
     * @param declaringMethodId      the method declaring the variable
     */
    public IRVariable(String id, String name, String declaringMethodId) {
        super(id);
        this.name = name;
        this.declaringMethodId = declaringMethodId;
    }

    /**
     * Create a method parameter.
     * @param methodId   the method
     * @param idx        the position of the formal parameter
     * @return           the parameter object
     */
    public static IRVariable newParam(String methodId, int idx) {
        return new IRVariable(methodId + "/@parameter" + idx, "@parameter" + idx, methodId);
    }

    /**
     * Create a "this" receiver variable.
     * @param methodId  the instance method
     * @return          the receiver variable of the instance method
     */
    public static IRVariable newThis(String methodId) {
        return new IRVariable(methodId + "/@this", "@this", methodId);
    }
}
