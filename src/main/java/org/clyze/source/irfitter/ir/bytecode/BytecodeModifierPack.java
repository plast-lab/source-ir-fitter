package org.clyze.source.irfitter.ir.bytecode;

import org.clyze.source.irfitter.ir.model.IRModifierPack;
import org.objectweb.asm.Opcodes;

class BytecodeModifierPack extends IRModifierPack {
    final int access;

    BytecodeModifierPack(int access) {
        this.access = access;
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
