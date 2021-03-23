package org.clyze.source.irfitter.ir.model;

import org.clyze.source.irfitter.base.ModifierPack;

/** A set of access modifiers for a program element (in IR). */
public abstract class IRModifierPack extends ModifierPack {
    /** True if the "synthetic" flag is set. */
    abstract public boolean isSynthetic();

    @Override
    public String toString() {
        return super.toString() + ", synthetic=" + isSynthetic();
    }
}
