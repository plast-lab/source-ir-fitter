package org.clyze.source.irfitter.ir.bytecode;

import java.util.Map;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.*;

/**
 * The visitor for bytecode methods.
 */
public class BytecodeMethodVisitor extends MethodVisitor {
    private final int NO_LINE = -1;
    private int lastLine = NO_LINE;
    private final IRMethod irMethod;
    private final Map<Label, Integer> indexToSourceLine;
    private final boolean debug;

    public BytecodeMethodVisitor(IRMethod irMethod, Map<Label, Integer> indexToSourceLine, boolean debug) {
        super(Opcodes.ASM9);
        this.irMethod = irMethod;
        this.indexToSourceLine = indexToSourceLine;
        this.debug = debug;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (opcode == Opcodes.NEWARRAY)
            recordAllocation("java.lang.Object", true);
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.NEW)
            recordAllocation(type, false);
        super.visitTypeInsn(opcode, type);
    }

    private void recordAllocation(String type, boolean isArray) {
        String allocatedTypeId = TypeUtils.replaceSlashesWithDots(type);
        boolean inIIB = false;
        irMethod.addAllocation(allocatedTypeId, inIIB, isArray, getLastLine(), debug);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        int arity = TypeUtils.raiseSignature(descriptor).size() - 1;
        String invokedMethodId = TypeUtils.replaceSlashesWithDots(owner) + "." + name;
        IRMethodInvocation irInvo = irMethod.addInvocation(name, arity, invokedMethodId, getLastLine(), debug);
        if (debug)
            System.out.println("IR invocation: " + irInvo);
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    private Integer getLastLine() {
        return this.lastLine == NO_LINE ? null : this.lastLine;
    }

    @Override
    public void visitLabel(Label label) {
        Integer l = indexToSourceLine.get(label);
        lastLine = l == null ? NO_LINE : l;
        super.visitLabel(label);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof Type)
            irMethod.addTypeReference(((Type) value).getClassName());
        super.visitLdcInsn(value);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null)
            irMethod.addTypeReference(TypeUtils.replaceSlashesWithDots(type));
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            addFieldAccess(owner, name, descriptor, true);
        } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            addFieldAccess(owner, name, descriptor, false);
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    private void addFieldAccess(String owner, String name, String descriptor, boolean read) {
        String fieldType = TypeUtils.raiseTypeId(descriptor);
        String fieldId = "<" + TypeUtils.replaceSlashesWithDots(owner) + ": " + fieldType + " " + name + ">";
        irMethod.addFieldAccess(fieldId, name, fieldType, read, debug);
    }
}
