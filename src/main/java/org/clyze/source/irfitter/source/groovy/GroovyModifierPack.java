package org.clyze.source.irfitter.source.groovy;

import org.apache.groovy.parser.antlr4.GroovyParser.*;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.source.model.SourceModifierPack;

/** Class/field/method modifiers for Groovy sources. */
class GroovyModifierPack extends SourceModifierPack {
    private boolean isStatic = false;
    private boolean isDef = false;
    private boolean isStrictFp = false;

    public GroovyModifierPack(SourceFile sourceFile, ClassOrInterfaceModifiersOptContext classOrInterfaceModifiersOptContext) {
        if (classOrInterfaceModifiersOptContext == null)
            return;
        ClassOrInterfaceModifiersContext mods = classOrInterfaceModifiersOptContext.classOrInterfaceModifiers();
        if (mods == null)
            return;
        for (ClassOrInterfaceModifierContext c : mods.classOrInterfaceModifier())
            updateFrom(sourceFile, c);
    }

    public GroovyModifierPack(SourceFile sourceFile, ModifiersOptContext modifiersOpt) {
        if (modifiersOpt == null)
            return;
        updateFrom(sourceFile, modifiersOpt.modifiers());
    }

    public GroovyModifierPack(SourceFile sourceFile, ModifiersContext modifiersContext) {
        updateFrom(sourceFile, modifiersContext);
    }

    public GroovyModifierPack(VariableModifiersOptContext variableModifiersOpt) {
        if (variableModifiersOpt == null)
            return;
        VariableModifiersContext variableModifiers = variableModifiersOpt.variableModifiers();
        if (variableModifiers == null)
            return;
        for (VariableModifierContext variableModifier : variableModifiers.variableModifier())
            updateFrom(variableModifier);
    }

    private void updateFrom(VariableModifierContext variableModifier) {
        if (variableModifier == null)
            return;
        if (variableModifier.ABSTRACT() != null)
            this.isAbstract = true;
        else if (variableModifier.DEF() != null || variableModifier.VAR() != null)
            this.isDef = true;
        else if (variableModifier.FINAL() != null)
            this.isFinal = true;
        else if (variableModifier.PUBLIC() != null)
            this.isPublic = true;
        else if (variableModifier.PROTECTED() != null)
            this.isProtected = true;
        else if (variableModifier.PRIVATE() != null)
            this.isPrivate = true;
        else if (variableModifier.STATIC() != null)
            this.isStatic = true;
        else if (variableModifier.STRICTFP() != null)
            this.isStrictFp = true;
        else if (variableModifier.PRIVATE() != null)
            this.isPrivate = true;
    }

    private void updateFrom(SourceFile sourceFile, ModifiersContext modifiersContext) {
        if (modifiersContext == null)
            return;
        for (ModifierContext mc : modifiersContext.modifier())
            updateFrom(sourceFile, mc.classOrInterfaceModifier());
    }

    private void updateFrom(SourceFile sourceFile, ClassOrInterfaceModifierContext c) {
        if (c == null)
            return;
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
        else {
            AnnotationContext annotation = c.annotation();
            if (annotation != null) {
                AnnotationNameContext annotationName = annotation.annotationName();
                String nameText = annotationName.getText();
                // Trim starting "@" symbol.
                String txt = nameText.startsWith("@") ? nameText.substring(1) : nameText;
                registerAnnotation(sourceFile, txt, GroovyUtils.createPositionFromToken(annotationName.start));
            }
        }
    }

    @Override
    public boolean isStatic() {
        return this.isStatic;
    }

    @Override
    public boolean isInterface() {
        throw new UnsupportedOperationException("Groovy sources: isInterface() is not supported");
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
    public boolean isEnum() {
        throw new UnsupportedOperationException("Groovy sources: isEnum() is not supported");
    }

    public boolean isDef() {
        return this.isDef;
    }

    public boolean isStrictFp() {
        return this.isStrictFp;
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
