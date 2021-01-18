package org.clyze.source.irfitter.source.kotlin;

import org.antlr.grammars.KotlinParser.*;
import org.clyze.source.irfitter.base.ModifierPack;

public class KotlinModifierPack extends ModifierPack {
    private boolean isPublic = false;
    private boolean isPrivate = false;
    private boolean isProtected = false;
    private boolean isAbstract = false;
    private boolean isFinal = false;
    private boolean isInner = false;
    private boolean isEnum = false;
    private boolean isNative = false;

    void updateFrom(ModifierContext mc) {
        VisibilityModifierContext visModCtx = mc.visibilityModifier();
        if (visModCtx != null) {
            if (visModCtx.PUBLIC() != null)
                this.isPublic = true;
            if (visModCtx.PRIVATE() != null)
                this.isPrivate = true;
            if (visModCtx.PROTECTED() != null)
                this.isProtected = true;
        }
        InheritanceModifierContext inModCtx = mc.inheritanceModifier();
        if (inModCtx != null) {
            if (inModCtx.ABSTRACT() != null)
                this.isAbstract = true;
            if (inModCtx.FINAL() != null)
                this.isFinal = true;
        }
        ClassModifierContext classModCtx = mc.classModifier();
        if (classModCtx != null) {
            if (classModCtx.INNER() != null)
                this.isInner = true;
            if (classModCtx.ENUM() != null)
                this.isEnum = true;
        }
        FunctionModifierContext funModCtx = mc.functionModifier();
        if (funModCtx != null) {
            if (funModCtx.EXTERNAL() != null)
                this.isNative = true;
        }
    }

    @Override
    public boolean isStatic() {
        throw new UnsupportedOperationException("Kotlin sources: isStatic() is not supported");
    }

    @Override
    public boolean isInterface() {
        throw new UnsupportedOperationException("Kotlin sources: isInterface() is not supported");
    }

    @Override
    public boolean isAbstract() {
        return this.isAbstract;
    }

    @Override
    public boolean isNative() {
        return this.isNative;
    }

    @Override
    public boolean isSynchronized() {
        throw new UnsupportedOperationException("Kotlin sources: isSynchronized() is not supported");
    }

    @Override
    public boolean isFinal() {
        return this.isFinal;
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

    @Override
    public boolean isProtected() {
        return isProtected;
    }

    @Override
    public boolean isPrivate() {
        return isPrivate;
    }

    @Override
    public boolean isEnum() {
        return isEnum;
    }

    public boolean isInner() {
        return this.isInner;
    }
}
