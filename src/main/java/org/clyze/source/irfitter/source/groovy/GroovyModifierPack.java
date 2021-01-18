package org.clyze.source.irfitter.source.groovy;

import org.apache.groovy.parser.antlr4.GroovyParser.*;
import org.clyze.source.irfitter.base.ModifierPack;

class GroovyModifierPack extends ModifierPack {
    private boolean isPublic = false;
    private boolean isPrivate = false;
    private boolean isProtected = false;
    private boolean isStatic = false;
    private boolean isAbstract = false;
    private boolean isFinal = false;

    public GroovyModifierPack(ClassOrInterfaceModifiersOptContext classOrInterfaceModifiersOptContext) {
        if (classOrInterfaceModifiersOptContext == null)
            return;
        ClassOrInterfaceModifiersContext mods = classOrInterfaceModifiersOptContext.classOrInterfaceModifiers();
        if (mods == null)
            return;
        for (ClassOrInterfaceModifierContext c : mods.classOrInterfaceModifier())
            updateFrom(c);
    }

    public GroovyModifierPack(ModifiersOptContext modifiersOpt) {
        if (modifiersOpt == null)
            return;
        updateFrom(modifiersOpt.modifiers());
    }

    public GroovyModifierPack(ModifiersContext modifiersContext) {
        updateFrom(modifiersContext);
    }

    private void updateFrom(ModifiersContext modifiersContext) {
        if (modifiersContext == null)
            return;
        for (ModifierContext mc : modifiersContext.modifier())
            updateFrom(mc.classOrInterfaceModifier());
    }

    private void updateFrom(ClassOrInterfaceModifierContext c) {
        if (c.ABSTRACT() != null)
            isAbstract = true;
        else if (c.PUBLIC() != null)
            isPublic = true;
        else if (c.PRIVATE() != null)
            isPrivate = true;
        else if (c.PROTECTED() != null)
            isProtected = true;
        else if (c.STATIC() != null)
            isStatic = true;
        else if (c.FINAL() != null)
            isFinal = true;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isInterface() {
        throw new UnsupportedOperationException("Groovy sources: isInterface() is not supported");
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public boolean isNative() {
        throw new UnsupportedOperationException("Groovy sources: isNative() is not supported");
    }

    @Override
    public boolean isSynchronized() {
        throw new UnsupportedOperationException("Groovy sources: isSynchronized() is not supported");
    }

    @Override
    public boolean isFinal() {
        return isFinal;
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
        throw new UnsupportedOperationException("Groovy sources: isEnum() is not supported");
    }

    /**
     * Take into account that default visibility (no modifier) in Groovy is
     * also considered public.
     * @return true if the access is public
     */
    public boolean isGroovyPublic() {
        return isPublic() || (!isPrivate() && !isProtected());
    }
}
