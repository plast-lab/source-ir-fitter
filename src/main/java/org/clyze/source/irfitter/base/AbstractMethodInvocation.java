package org.clyze.source.irfitter.base;

/** The information shared by all method invocations that helps in their matching. */
public interface AbstractMethodInvocation {
    String getMethodName();
    int getArity();
    String getId();
}
