package org.clyze.source.irfitter.source.model;

/**
 * Expressions that involve assignment to a target variable.
 */
public interface Targetable {
    /**
     * Set the target variable.
     * @param v  the target assignment variable
     */
    void setTarget(JVariable v);

    /**
     * Get the target variable.
     * @return  the target assignment variable
     */
    JVariable getTarget();
}
