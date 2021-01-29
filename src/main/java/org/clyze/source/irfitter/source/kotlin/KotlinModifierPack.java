package org.clyze.source.irfitter.source.kotlin;

import java.util.List;
import org.antlr.grammars.KotlinParser.*;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.source.model.SourceModifierPack;

/** Class/field/method modifiers for Kotlin sources. */
public class KotlinModifierPack extends SourceModifierPack {
    private boolean isInner = false;

    public KotlinModifierPack(SourceFile sourceFile, ModifiersContext modsCtxt) {
        if (modsCtxt != null) {
            List<ModifierContext> mcl = modsCtxt.modifier();
            if (mcl != null)
                for (ModifierContext mc : mcl)
                    updateFrom(mc);
            updateFrom(sourceFile, modsCtxt.annotation());
        }
    }

    public KotlinModifierPack(SourceFile sourceFile, List<AnnotationContext> anl) {
        updateFrom(sourceFile, anl);
    }

    private void updateFrom(SourceFile sourceFile, List<AnnotationContext> anl) {
        if (anl != null)
            for (AnnotationContext ac : anl)
                updateFrom(sourceFile, ac);
    }

    public void updateFrom(ModifierContext mc) {
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

    public void updateFrom(SourceFile sourceFile, AnnotationContext ac) {
        if (ac == null)
            return;
        registerAnnotation(sourceFile, ac.getText(), KotlinUtils.createPositionFromToken(ac.start));
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
    public boolean isSynchronized() {
        throw new UnsupportedOperationException("Kotlin sources: isSynchronized() is not supported");
    }

    public boolean isInner() {
        return this.isInner;
    }
}
