package org.clyze.source.irfitter.source.model;

import java.util.HashSet;
import java.util.Set;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.ModifierPack;

/** Common functionality for source-level class/field/method modifiers. */
public abstract class SourceModifierPack extends ModifierPack {
    protected boolean isAbstract = false;
    protected boolean isFinal = false;
    protected boolean isPrivate = false;
    protected boolean isProtected = false;
    protected boolean isPublic = false;
    protected boolean isInterface = false;
    protected boolean isEnum = false;
    protected boolean isNative = false;
    protected final Set<TypeUsage> annotationUses = new HashSet<>();

    @Override
    public boolean isAbstract() {
        return isAbstract;
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
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isEnum() {
        return isEnum;
    }

    @Override
    public boolean isNative() {
        return this.isNative;
    }

    public Set<TypeUsage> getAnnotationUses() {
        return annotationUses;
    }

    protected void registerAnnotation(SourceFile sourceFile, String annotationType, Position annotationPos) {
        annotations.add(annotationType);
        annotationUses.add(new TypeUsage(annotationType, annotationPos, sourceFile));
    }
}
