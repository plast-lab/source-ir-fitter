package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.ModifierPack;

public class IRField extends IRElement {
    public final String name;
    public final String type;
    public final ModifierPack mp;

    public IRField(String id, String name, String type, ModifierPack mp) {
        super(id);
        this.name = name;
        this.type = type;
        this.mp = mp;
    }
}
