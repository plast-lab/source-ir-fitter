package org.clyze.source.irfitter.base;

/** A set of access modifiers for a program element. */
public abstract class ModifierPack {
    abstract public boolean isStatic();
    abstract public boolean isInterface();
    abstract public boolean isAbstract();
    abstract public boolean isNative();
    abstract public boolean isSynchronized();
    abstract public boolean isFinal();
    abstract public boolean isPublic();
    abstract public boolean isProtected();
    abstract public boolean isPrivate();
    abstract public boolean isEnum();

    @Override
    public String toString() {
        return "static=" + isStatic() + "," +
                "interface=" + isInterface() + "," +
                "abstract=" + isAbstract() + "," +
                "native=" + isNative() + "," +
                "synchronized=" + isSynchronized() + "," +
                "final=" + isFinal() + "," +
                "enum=" + isEnum() + "," +
                "public=" + isPublic() + "," +
                "protected=" + isProtected() + "," +
                "private=" + isPrivate();
    }
}
