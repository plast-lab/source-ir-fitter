package org.clyze.source.irfitter.source.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Map<String, JVariable> cachedLookups;

    public JBlock(String name, JBlock parent) {
        this.id = "block-" + name;
        this.parent = parent;
    }

    /**
     * Crates a block starting at the specified position.
     * @param pos     the starting position of the block
     * @param parent  the parent block
     */
    public JBlock(Position pos, JBlock parent) {
        this("block-" + pos.getStartLine() + ":" + pos.getStartColumn(), parent);
    }

    public void addVariable(JVariable v) {
        if (variables == null)
            variables = new ArrayList<>();
        variables.add(v);
    }

    public List<JVariable> getVariables() {
        return this.variables;
    }

    private JVariable lookupNoCache(String name) {
        if (variables != null)
            for (JVariable variable : variables)
                if (variable.name.equals(name))
                    return variable;
        if (parent != null)
            return parent.lookup(name);
        return null;
    }

    public JVariable lookup(String name) {
        if (name == null)
            return null;
        if (cachedLookups == null)
            cachedLookups = new HashMap<>();
        JVariable returnValue = cachedLookups.get(name);
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
}
