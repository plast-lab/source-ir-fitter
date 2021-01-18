package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.AbstractMethodInvocation;

public class IRMethodInvocation extends IRElement implements AbstractMethodInvocation {
    public final String invokingMethodId;
    public final String methodName;
    public final int arity;
    public final String methodId;
    final int index;
    final Integer sourceLine;

    IRMethodInvocation(String id, String invokingMethodId, String methodName, int arity, String methodId, int index, Integer sourceLine) {
        super(id);
        this.invokingMethodId = invokingMethodId;
        this.methodName = methodName;
        this.arity = arity;
        this.methodId = methodId;
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
