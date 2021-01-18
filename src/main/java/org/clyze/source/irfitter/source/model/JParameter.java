package org.clyze.source.irfitter.source.model;

/** A named parameter (with an optional type). Used in method declarations. */
public class JParameter {
    /** The parameter name. */
    public final String name;
    /** The parameter type (may be null). */
    public final String type;

    /**
     * Create a method parameter.
     * @param name  the name of the parameter
     * @param type  the type of the parameter (may be null)
     */
    public JParameter(String name, String type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return (type == null ? "*" : type) + " " + name;
    }
}
