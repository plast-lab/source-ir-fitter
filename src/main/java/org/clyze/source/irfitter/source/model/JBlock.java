package org.clyze.source.irfitter.source.model;

import java.util.ArrayList;
import java.util.List;
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

    public JVariable lookup(String name) {
        if (name == null)
            return null;
        if (variables != null)
            for (JVariable variable : variables)
                if (variable.name.equals(name))
                    return variable;
        else if (parent != null)
            return parent.lookup(name);
        return null;
    }

    @Override
    public String toString() {
        return "BLOCK: " + id + ", parent = [" + parent + "]";
    }
}
