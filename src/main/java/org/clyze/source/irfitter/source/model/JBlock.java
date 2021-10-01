package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.persistent.model.Position;

/**
 * A source code block such as curly-bracket blocks in Java or parenthesis-blocks
 * in Kotlin.
 */
public class JBlock {
    /** The id of the block. Unique per source file. */
    public final String id;
    /** The parent block (or null). */
    public final JBlock parent;
    private List<JVariable> variables = null;
    /** Cached results of previous variable lookups. */
    private Map<String, Result> cachedLookups;
    /** The type containing this block. */
    public final JType enclosingType;

    /**
     * Crates a named block.
     * @param name           the (unique) name of the block
     * @param parent         the parent block
     * @param enclosingType  the type containing this block (null, unless method top-level block)
     */
    public JBlock(String name, JBlock parent, JType enclosingType) {
        this.id = "block-" + name;
        this.parent = parent;
        this.enclosingType = enclosingType;
    }

    /**
     * Crates a block starting at the specified position.
     * @param pos            the starting position of the block
     * @param parent         the parent block
     * @param enclosingType  the type containing this block (null, unless method top-level block)
     */
    public JBlock(Position pos, JBlock parent, JType enclosingType) {
        this("pos-" + pos.getStartLine() + ":" + pos.getStartColumn(), parent, enclosingType);
    }

    public void addVariable(JVariable v) {
        if (variables == null)
            variables = new ArrayList<>();
        variables.add(v);
    }

    public List<JVariable> getVariables() {
        return this.variables;
    }

    private Result lookupNoCache(String name) {
        if (variables != null)
            for (JVariable variable : variables)
                if (variable.name.equals(name))
                    return new Result(variable);
        if (enclosingType != null) {
            Optional<JField> matchingField = enclosingType.fields.stream().filter(fld -> fld.name.equals(name)).findFirst();
            if (matchingField.isPresent())
                return new Result(matchingField.get());
        }
        if (parent != null)
            return parent.lookup(name);
        return null;
    }

    public Result lookup(String name) {
        if (name == null)
            return null;
        if (cachedLookups == null)
            cachedLookups = new HashMap<>();
        Result returnValue = cachedLookups.get(name);
        if (returnValue == null) {
            returnValue = lookupNoCache(name);
            if (returnValue != null)
                cachedLookups.put(name, returnValue);
        }
        return returnValue;
    }

    @Override
    public String toString() {
        return "BLOCK: " + id + ", parent = [" + parent + "]";
    }

    /**
     * The result of looking up a name in the block. This can be either a
     * local variable or a field.
     */
    public static class Result {
        /** If not null, this is a field result. */
        public final JField field;
        /** If not null, this is a variable result. */
        public final JVariable variable;

        public Result(JVariable v) {
            this.field = null;
            this.variable = v;
        }

        public Result(JField f) {
            this.field = f;
            this.variable = null;
        }
    }
}
