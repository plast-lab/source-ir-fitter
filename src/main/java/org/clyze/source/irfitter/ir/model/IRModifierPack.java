package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.ModifierPack;

public abstract class IRModifierPack extends ModifierPack {
    abstract public boolean isSynthetic();

    @Override
    public String toString() {
        return super.toString() + ", synthetic=" + isSynthetic();
    }
}
