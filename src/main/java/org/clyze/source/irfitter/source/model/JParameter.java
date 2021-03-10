package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;

/** A named parameter (with an optional type). Used in method declarations. */
public class JParameter {
    /** The parameter name. */
    public final String name;
    /** The parameter type (may be null). */
    public final String type;
    /** The position of the parameter. */
    public final Position position;

    /**
     * Create a method parameter.
     * @param name      the name of the parameter
     * @param type      the type of the parameter (may be null)
     * @param position  the position of the parameter
     */
    public JParameter(String name, String type, Position position) {
        this.name = name;
        this.type = type;
        this.position = position;
    }

    @Override
    public String toString() {
        return (type == null ? "*" : type) + " " + name;
    }
}
