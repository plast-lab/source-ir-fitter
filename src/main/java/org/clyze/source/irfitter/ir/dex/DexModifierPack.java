package org.clyze.source.irfitter.ir.dex;

import org.clyze.source.irfitter.ir.model.IRModifierPack;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Member;

import java.util.Set;

/** The modifiers of a Dex class/field/method. */
class DexModifierPack extends IRModifierPack {
    private final int access;

    public DexModifierPack(ClassDef classDef) {
        this(classDef.getAccessFlags(), classDef.getAnnotations());
    }

    public DexModifierPack(Member memberDef) {
        this(memberDef.getAccessFlags(), memberDef.getAnnotations());
    }

    private DexModifierPack(int access, Set<? extends Annotation> annotationList) {
        this.access = access;
        for (Annotation annotation : annotationList)
            annotations.add(TypeUtils.raiseTypeId(annotation.getType()));
    }

    @Override
    public boolean isStatic() {
        return AccessFlags.STATIC.isSet(access);
    }

    @Override
    public boolean isInterface() {
        return AccessFlags.INTERFACE.isSet(access);
    }

    @Override
    public boolean isAbstract() {
        return AccessFlags.ABSTRACT.isSet(access);
    }

    @Override
    public boolean isNative() {
        return AccessFlags.NATIVE.isSet(access);
    }

    @Override
    public boolean isSynchronized() {
        return AccessFlags.SYNCHRONIZED.isSet(access);
    }

    @Override
    public boolean isFinal() {
        return AccessFlags.FINAL.isSet(access);
    }

    @Override
    public boolean isSynthetic() {
        return AccessFlags.SYNTHETIC.isSet(access);
    }

    @Override
    public boolean isPublic() {
        return AccessFlags.PUBLIC.isSet(access);
    }

    @Override
    public boolean isProtected() {
        return AccessFlags.PROTECTED.isSet(access);
    }

    @Override
    public boolean isPrivate() {
        return AccessFlags.PRIVATE.isSet(access);
    }

    @Override
    public boolean isEnum() {
        return AccessFlags.ENUM.isSet(access);
    }
}
