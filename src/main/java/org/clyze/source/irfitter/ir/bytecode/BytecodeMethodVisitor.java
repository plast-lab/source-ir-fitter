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
    private final boolean inIIB;
    private final boolean debug;

    public BytecodeMethodVisitor(IRMethod irMethod, Map<Label, Integer> indexToSourceLine, boolean inIIB, boolean debug) {
        super(Opcodes.ASM9);
        this.irMethod = irMethod;
        this.indexToSourceLine = indexToSourceLine;
        this.inIIB = inIIB;
        this.debug = debug;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (opcode == Opcodes.NEWARRAY) {
            String elemType = null;
            switch (operand) {
                case Opcodes.T_BOOLEAN: elemType = "boolean"; break;
                case Opcodes.T_CHAR   : elemType = "char"   ; break;
                case Opcodes.T_FLOAT  : elemType = "float"  ; break;
                case Opcodes.T_DOUBLE : elemType = "double" ; break;
                case Opcodes.T_BYTE   : elemType = "byte"   ; break;
                case Opcodes.T_SHORT  : elemType = "short"  ; break;
                case Opcodes.T_INT    : elemType = "int"    ; break;
                case Opcodes.T_LONG   : elemType = "long"   ; break;
            }
            if (elemType != null)
                recordAllocation(elemType, true);
        }
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.NEW)
            recordAllocation(TypeUtils.replaceSlashesWithDots(type), false);
        else if (opcode == Opcodes.ANEWARRAY) {
            // Types can be either "[Ljava.lang.String;" or "java.lang.String".
            boolean isDescriptor = (type.startsWith("[") || type.startsWith("L")) && type.endsWith(";");
            String typeId = isDescriptor ? TypeUtils.raiseTypeId(type) : TypeUtils.replaceSlashesWithDots(type);
            recordAllocation(typeId, true);
        }
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        String arrayType = TypeUtils.raiseTypeId(descriptor);
        // Strip one pair of brackets, since allocations take the element type.
        if (arrayType.endsWith("[]"))
            recordAllocation(arrayType.substring(0, arrayType.length() - 2), true);
        else
            System.out.println("ERROR: could not process multianewarray(" + descriptor + ")");
        super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }

    private void recordAllocation(String type, boolean isArray) {
        String allocatedTypeId = TypeUtils.replaceSlashesWithDots(type);
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
