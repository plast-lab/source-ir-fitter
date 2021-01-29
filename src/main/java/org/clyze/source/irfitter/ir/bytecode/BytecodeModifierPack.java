package org.clyze.source.irfitter.ir.bytecode;

import java.util.List;
import org.clyze.source.irfitter.ir.model.IRModifierPack;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The modifiers of a class/field/method found in Java bytecode. */
class BytecodeModifierPack extends IRModifierPack {
    private final int access;

    private BytecodeModifierPack(int access, List<AnnotationNode> visibleAnnotations,
                                 List<TypeAnnotationNode> visibleTypeAnnotations) {
        this.access = access;
        if (visibleAnnotations != null)
            for (AnnotationNode visibleAnnotation : visibleAnnotations)
                annotations.add(TypeUtils.raiseTypeId(visibleAnnotation.desc));
        if (visibleTypeAnnotations != null)
            for (TypeAnnotationNode visibleTypeAnnotation : visibleTypeAnnotations) {
                annotations.add(TypeUtils.raiseTypeId(visibleTypeAnnotation.desc));
            }
    }

    BytecodeModifierPack(ClassNode node) {
        this(node.access, node.visibleAnnotations, node.visibleTypeAnnotations);
    }

    BytecodeModifierPack(FieldNode node) {
        this(node.access, node.visibleAnnotations, node.visibleTypeAnnotations);
    }

    BytecodeModifierPack(MethodNode node) {
        this(node.access, node.visibleAnnotations, node.visibleTypeAnnotations);
    }

    @Override
    public boolean isStatic() {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    @Override
    public boolean isInterface() {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    @Override
    public boolean isAbstract() {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    @Override
    public boolean isNative() {
        return (access & Opcodes.ACC_NATIVE) != 0;
    }

    @Override
    public boolean isSynchronized() {
        return (access & Opcodes.ACC_SYNCHRONIZED) != 0;
    }

    @Override
    public boolean isFinal() {
        return (access & Opcodes.ACC_FINAL) != 0;
    }

    @Override
    public boolean isSynthetic() {
        return (access & Opcodes.ACC_SYNTHETIC) != 0;
    }

    @Override
    public boolean isPublic() {
        return (access & Opcodes.ACC_PUBLIC) != 0;
    }

    @Override
    public boolean isProtected() {
        return (access & Opcodes.ACC_PROTECTED) != 0;
    }

    @Override
    public boolean isPrivate() {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    @Override
    public boolean isEnum() {
        return (access & Opcodes.ACC_ENUM) != 0;
    }
}
