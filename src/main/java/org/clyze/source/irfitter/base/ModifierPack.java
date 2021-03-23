package org.clyze.source.irfitter.base;

import java.util.HashSet;
import java.util.Set;

/** A set of access modifiers for a program element. */
public abstract class ModifierPack {
    protected final Set<String> annotations = new HashSet<>();

    /** True if this is an interface declaration. */
    abstract public boolean isInterface();
    /** True if this is an enum declaration. */
    abstract public boolean isEnum();
    /** True if this is a "static" declaration. */
    abstract public boolean isStatic();
    /** True if this is an "abstract" declaration. */
    abstract public boolean isAbstract();
    /** True if this is a "native" (or "external" in Kotlin) declaration. */
    abstract public boolean isNative();
    /** True if this is a "synchronized" declaration. */
    abstract public boolean isSynchronized();
    /** True if this is a "final" declaration. */
    abstract public boolean isFinal();
    /** True if this is a "public" declaration. */
    abstract public boolean isPublic();
    /** True if this is a "protected" declaration. */
    abstract public boolean isProtected();
    /** True if this is a "private" declaration. */
    abstract public boolean isPrivate();
    /** True if the "varargs" flag is set. */
    abstract public boolean isVarArgs();

    /** The set of annotations in the source code. */
    public Set<String> getAnnotations() {
        return this.annotations;
    }

    /** Pretty printer. */
    @Override
    public String toString() {
        return "static=" + isStatic() + "," +
                "interface=" + isInterface() + "," +
                "abstract=" + isAbstract() + "," +
                "native=" + isNative() + "," +
                "synchronized=" + isSynchronized() + "," +
                "final=" + isFinal() + "," +
                "enum=" + isEnum() + "," +
                "varargs=" + isVarArgs() + "," +
                "public=" + isPublic() + "," +
                "protected=" + isProtected() + "," +
                "private=" + isPrivate();
    }
}
