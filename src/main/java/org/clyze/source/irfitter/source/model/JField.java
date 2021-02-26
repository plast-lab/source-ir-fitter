package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.persistent.model.jvm.JvmField;
import org.clyze.source.irfitter.ir.model.IRField;
import org.clyze.persistent.model.Position;

/**
 * A source field that can be mapped to an IR field.
 */
public class JField extends TypedNamedElementWithPosition<IRField, JvmField> {
    /** The type of the field (sources, not necessarily qualified). */
    public final String type;
    /** The name of the field. */
    public final String name;
    public final JType parent;
    public final Set<String> annotations;
    private Collection<String> cachedIds = null;

    public JField(SourceFile srcFile, String type, String name,
                  Set<String> annotations, Position pos, JType parent) {
        super(srcFile, pos);
        this.type = type;
        this.name = name;
        this.parent = parent;
        this.annotations = new HashSet<>(annotations);
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
        String parentDesc = parent == null ? "-" : parent.getUnqualifiedName();
        return "field{name=" + name + ", type=" + type + ", parent=" + parentDesc + "}";
    }

    @Override
    public void initSymbolFromIRElement(IRField irField) {
        if (symbol == null) {
            symbol = new JvmField(pos,
                    srcFile.getRelativePath(),
                    name,
                    irField.getId(),
                    irField.type,
                    parent == null ? null : parent.getFullyQualifiedName(),
                    irField.mp.isStatic());
            symbol.setAnnotations(annotations);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }
}
