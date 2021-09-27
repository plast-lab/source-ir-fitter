package org.clyze.source.irfitter.ir.model;

/**
 * A low-level lambda. This records the bootstrap/invokedynamic/metafactory
 * machinery used for the implementation of Java lambdas.
 */
public class IRLambda extends IRElement {
    /** The IR method that corresponds to the body of the lambda (signature). */
    public final String implementation;
    /** The IR method that corresponds to the body of the lambda (IR method object). */
    public IRMethod implMethod = null;

    public IRLambda(String id, String implementation) {
        super(id);
        this.implementation = implementation;
    }

    @Override
    public String toString() {
        return getId() + " implemented by " + implementation;
    }
}
