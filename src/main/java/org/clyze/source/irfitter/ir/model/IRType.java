package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.ModifierPack;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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

    /**
     * Gathers all references to types in method bodies.
     * @return the set of all type references as fully-qualified types
     */
    public Set<String> getTypeReferences() {
        Set<String> ret = new HashSet<>();
        for (IRMethod method : methods) {
            Set<String> typeReferences = method.getTypeReferences();
            if (typeReferences != null)
                ret.addAll(typeReferences);
            Set<String> sigTypeReferences = method.getSigTypeReferences();
            if (sigTypeReferences != null)
                ret.addAll(sigTypeReferences);
        }
        return ret;
    }
}
