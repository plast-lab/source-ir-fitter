package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.ModifierPack;

import java.util.LinkedList;
import java.util.List;

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
}
