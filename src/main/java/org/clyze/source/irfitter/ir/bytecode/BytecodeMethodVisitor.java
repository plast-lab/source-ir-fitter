package org.clyze.source.irfitter.ir.bytecode;

import java.util.Map;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class BytecodeMethodVisitor extends MethodVisitor {
    private final int NO_LINE = -1;
    private int lastLine = NO_LINE;
    private final IRMethod irMethod;
    private final String methodId;
    private final Map<Label, Integer> indexToSourceLine;
    private final boolean debug;

    public BytecodeMethodVisitor(IRMethod irMethod, Map<Label, Integer> indexToSourceLine, boolean debug) {
        super(Opcodes.ASM9);
        this.irMethod = irMethod;
        this.indexToSourceLine = indexToSourceLine;
        this.debug = debug;
        this.methodId = irMethod.getId();
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
        IRMethodInvocation irInvo = irMethod.addInvocation(methodId, name, arity, invokedMethodId, getLastLine(), debug);
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
}
