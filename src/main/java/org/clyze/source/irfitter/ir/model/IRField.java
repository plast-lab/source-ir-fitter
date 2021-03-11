package org.clyze.source.irfitter.ir.model;

import java.util.Collection;
import org.clyze.source.irfitter.base.ModifierPack;

/**
 * A low-level representation of a field.
 */
public class IRField extends IRElement {
    /** The name of the field. */
    public final String name;
    /** The fully-qualified type of the field. */
    public final String type;
    /** The modifiers of the field. */
    public final ModifierPack mp;

    public IRField(String id, String name, String type, ModifierPack mp) {
        super(id);
        this.name = name;
        this.type = type;
        this.mp = mp;
    }

    @Override
    public void addReferencedTypesTo(Collection<String> target) {
        if (type != null)
            target.add(type);
    }
}
