package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.AbstractMethodInvocation;

/**
 * A low-level representation of a method invocation site.
 */
public class IRMethodInvocation extends IRElement implements AbstractMethodInvocation {
    public final String invokingMethodId;
    public final String methodName;
    public final int arity;
    /** The method id to use for naming ("A.m") */
    public final String methodId;
    /** The declaring type of the invoked method (before method/override resolution). */
    public final String targetType;
    /** The return type of the invoked method (before method/override resolution). */
    public final String targetReturnType;
    /** The parameter types of the invoked method (before method/override resolution). */
    public final String targetParamTypes;
    final int index;
    final Integer sourceLine;

    IRMethodInvocation(String id, String invokingMethodId, String methodName,
                       int arity, String methodId, String targetType,
                       String targetReturnType, String targetParamTypes,
                       int index, Integer sourceLine) {
        super(id);
        this.invokingMethodId = invokingMethodId;
        this.methodName = methodName;
        this.arity = arity;
        this.methodId = methodId;
        this.targetType = targetType;
        this.targetReturnType = targetReturnType;
        this.targetParamTypes = targetParamTypes;
        this.index = index;
        this.sourceLine = sourceLine;
    }

    public Integer getSourceLine() {
        return this.sourceLine;
    }

    @Override
    public String getMethodName() {
        return this.methodName;
    }

    @Override
    public int getArity() {
        return this.arity;
    }

    @Override
    public String toString() {
        return id + "@" + getSourceLine();
    }
}
