package org.clyze.source.irfitter.base;

import java.util.HashSet;
import java.util.Set;

/** A set of access modifiers for a program element. */
public abstract class ModifierPack {
    protected final Set<String> annotations = new HashSet<>();

    /**
     * True if this is an interface declaration.
     * @return true if this type is an interface
     */
    abstract public boolean isInterface();
    /**
     * True if this is an enum declaration.
     * @return true if this type is an enum
     */
    abstract public boolean isEnum();
    /**
     * True if this is a "static" declaration.
     * @return true if this type/field/method is static
     */
    abstract public boolean isStatic();
    /**
     * True if this is an "abstract" declaration.
     * @return true if this type/method is abstract
     */
    abstract public boolean isAbstract();
    /**
     * True if this is a "native" (or "external" in Kotlin) declaration.
     * @return true if this method is native
     */
    abstract public boolean isNative();
    /**
     * True if this is a "synchronized" declaration.
     * @return true if this method is marked as synchronized
     */
    abstract public boolean isSynchronized();
    /**
     * True if this is a "final" declaration.
     * @return true if this type/field/method is final
     */
    abstract public boolean isFinal();
    /**
     * True if this is a "public" declaration.
     * @return true if this type/field/method is public
     */
    abstract public boolean isPublic();
    /**
     * True if this is a "protected" declaration.
     * @return true if this type/field/method is protected
     */
    abstract public boolean isProtected();
    /**
     * True if this is a "private" declaration.
     * @return true if this type/field/method is private
     */
    abstract public boolean isPrivate();
    /**
     * True if the "varargs" flag is set.
     * @return     true for varargs methods
     */
    abstract public boolean isVarArgs();

    /**
     * The set of annotations in the source code.
     * @return   the annotations set
     */
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
