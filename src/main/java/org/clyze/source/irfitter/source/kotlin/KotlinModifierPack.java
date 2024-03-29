package org.clyze.source.irfitter.source.kotlin;

import java.util.ArrayList;
import java.util.List;
import org.antlr.grammars.KotlinParser.*;
import org.antlr.grammars.KotlinParserBaseVisitor;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.source.model.SourceModifierPack;

/** Class/field/method modifiers for Kotlin sources. */
public class KotlinModifierPack extends SourceModifierPack {
    private boolean isInner = false;
    private boolean isConst = false;
    private boolean isSuspend = false;

    public KotlinModifierPack(SourceFile sourceFile, ModifiersContext modsCtxt) {
        this(sourceFile, null, modsCtxt, false, false);
    }

    public KotlinModifierPack(SourceFile sourceFile, List<AnnotationContext> anl) {
        this(sourceFile, anl, null, false, false);
    }

    public KotlinModifierPack(SourceFile sourceFile, List<AnnotationContext> anl,
                              ModifiersContext modsCtxt, boolean isInterface, boolean isEnum) {
        this.isInterface = isInterface;
        this.isEnum = isEnum;
        updateFrom(sourceFile, anl);
        if (modsCtxt != null) {
            List<ModifierContext> mcl = modsCtxt.modifier();
            if (mcl != null)
                for (ModifierContext mc : mcl)
                    updateFrom(mc);
            updateFrom(sourceFile, modsCtxt.annotation());
        }
    }

    public KotlinModifierPack(SourceFile sourceFile, TypeModifiersContext typeModifiers) {
        if (typeModifiers == null)
            return;
        for (TypeModifierContext typeModifier : typeModifiers.typeModifier()) {
            updateFrom(sourceFile, typeModifier.annotation());
            if (typeModifier.SUSPEND() != null)
                this.isSuspend = true;
        }
    }

    private void updateFrom(SourceFile sourceFile, List<AnnotationContext> anl) {
        if (anl != null)
            for (AnnotationContext ac : anl)
                updateFrom(sourceFile, ac);
    }

    public void updateFrom(ModifierContext mc) {
        VisibilityModifierContext visMod = mc.visibilityModifier();
        if (visMod != null) {
            if (visMod.PUBLIC() != null)
                this.isPublic = true;
            if (visMod.PRIVATE() != null)
                this.isPrivate = true;
            if (visMod.PROTECTED() != null)
                this.isProtected = true;
        }
        InheritanceModifierContext inMod = mc.inheritanceModifier();
        if (inMod != null) {
            if (inMod.ABSTRACT() != null)
                this.isAbstract = true;
            if (inMod.FINAL() != null)
                this.isFinal = true;
        }
        ClassModifierContext classMod = mc.classModifier();
        if (classMod != null) {
            if (classMod.INNER() != null)
                this.isInner = true;
            if (classMod.ENUM() != null)
                this.isEnum = true;
        }
        FunctionModifierContext funMod = mc.functionModifier();
        if (funMod != null) {
            if (funMod.EXTERNAL() != null)
                this.isNative = true;
        }
        PropertyModifierContext propMod = mc.propertyModifier();
        if (propMod != null) {
            if (propMod.CONST() != null)
                this.isConst = true;
        }
    }

    public void updateFrom(SourceFile sourceFile, AnnotationContext ac) {
        if (ac == null)
            return;
        for (String annotationName : getAnnotationNames(ac))
            registerAnnotation(sourceFile, annotationName, KotlinUtils.createPositionFromToken(ac.start));
    }

    private static List<String> getAnnotationNames(AnnotationContext ac) {
        List<String> names = new ArrayList<>();
        ac.accept(new KotlinParserBaseVisitor<Void>() {
            @Override
            public Void visitUserType(UserTypeContext ctx) {
                for (SimpleUserTypeContext simpleUserType : ctx.simpleUserType()) {
                    names.add(simpleUserType.simpleIdentifier().getText());
                }
                return null;
            }
        });
        return names;
    }

    @Override
    public boolean isStatic() {
        throw new UnsupportedOperationException("Kotlin sources: isStatic() is not supported");
    }

    @Override
    public boolean isInterface() {
        return this.isInterface;
    }

    @Override
    public boolean isSynchronized() {
        throw new UnsupportedOperationException("Kotlin sources: isSynchronized() is not supported");
    }

    public boolean isInner() {
        return this.isInner;
    }

    public boolean isConst() {
        return this.isConst;
    }

    public boolean isSuspend() {
        return this.isSuspend;
    }

    /** Pretty printer. */
    @Override
    public String toString() {
        return "inner=" + isInner() + "," +
                "const=" + isConst() + "," +
                "abstract=" + isAbstract() + "," +
                "native=" + isNative() + "," +
                "final=" + isFinal() + "," +
                "enum=" + isEnum() + "," +
                "public=" + isPublic() + "," +
                "protected=" + isProtected() + "," +
                "private=" + isPrivate();
    }
}
