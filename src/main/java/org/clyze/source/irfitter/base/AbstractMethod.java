package org.clyze.source.irfitter.base;

import java.util.List;

/**
 * The information shared by all methods that helps in matching their
 * contents (invocations, allocations).
 */
public interface AbstractMethod {
    List<? extends AbstractMethodInvocation> getInvocations();
}
