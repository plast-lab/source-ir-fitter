package org.clyze.source.irfitter.ir.model;

import java.util.*;

import org.clyze.source.irfitter.base.ModifierPack;

/**
 * A low-level representation of a type.
 */
public class IRType extends IRElement {
    public final List<String> superTypes;
    public final List<IRField> fields = new LinkedList<>();
    public final List<IRMethod> methods = new LinkedList<>();
    public final ModifierPack mp;

    public IRType(String id, List<String> superTypes, ModifierPack mp) {
        super(id);
        this.superTypes = superTypes;
        this.mp = mp;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public void addReferencedTypesTo(Collection<String> target) {
        addTypeRefs(target, fields);
        addTypeRefs(target, methods);
        if (superTypes != null)
            target.addAll(superTypes);
    }
}
