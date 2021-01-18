package org.clyze.source.irfitter.source.model;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.clyze.source.irfitter.ir.model.IRField;
import org.clyze.persistent.model.Field;
import org.clyze.persistent.model.Position;

/**
 * A source field that can be mapped to an IR field.
 */
public class JField extends TypedNamedElementWithPosition<IRField> {
    /** The type of the field (sources, not necessarily qualified). */
    public final String type;
    /** The name of the field. */
    public final String name;
    public final JType parent;
    private Collection<String> cachedIds = null;

    public JField(SourceFile srcFile, String type, String name,
                  Position pos, JType parent) {
        super(srcFile, pos);
        this.type = type;
        this.name = name;
        this.parent = parent;
    }

    @Override
    public Collection<String> getIds() {
        if (cachedIds == null) {
            cachedIds = new LinkedList<>();
            for (String t : resolveType(type))
                cachedIds.add("<" + parent.getFullyQualifiedName(srcFile.packageName) + ": " + t + " " + name + ">");
        }
        return cachedIds;
    }

    @Override
    public String toString() {
        return "field{name=" + name + ", type=" + type + ", parent=" + parent.getName() + "}";
    }

    @Override
    public void initSymbolFromIRElement(IRField irField) {
        if (symbol == null) {
            symbol = new Field(pos,
                    srcFile.getRelativePath(),
                    name,
                    irField.getId(),
                    irField.type,
                    parent == null ? null : parent.getFullyQualifiedName(),
                    irField.mp.isStatic());
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getDoopId());
    }
}
