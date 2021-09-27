package org.clyze.source.irfitter.ir.bytecode;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.ir.model.IRFieldAccess;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;

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
        } else if (opcode == Opcodes.CHECKCAST)
            irMethod.addCast(TypeUtils.replaceSlashesWithDots(type), getLastLine(), debug);
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

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        if (bootstrapMethodHandle.getName().equals("metafactory") &&
            bootstrapMethodHandle.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
            if (debug)
                System.out.println("Processing special invokedynamic instruction: " + name + "/" + descriptor);
            for (Object arg : bootstrapMethodArguments)
                if (arg instanceof Handle) {
                    Handle handle = (Handle) arg;
                    List<String> sig = TypeUtils.raiseSignature(handle.getDesc());
                    StringJoiner params = new StringJoiner(",");
                    if (sig.size() > 1)
                        sig.subList(1, sig.size()).forEach(s -> params.add(TypeUtils.replaceSlashesWithDots(s)));
                    String methodName = handle.getName();
                    String methodId = '<' + TypeUtils.replaceSlashesWithDots(handle.getOwner()) + ": " + sig.get(0) + ' ' + methodName + '(' + params + ")>";
                    irMethod.addMethodRef(methodId, methodName, getLastLine(), debug);
                }

        } else if (debug)
            System.out.println("Ignoring invokedynamic instruction: " + name + "/" + descriptor + ", bootstrap: " + bootstrapMethodHandle);
    }

    private void recordAllocation(String type, boolean isArray) {
        String allocatedTypeId = TypeUtils.replaceSlashesWithDots(type);
        irMethod.addAllocation(allocatedTypeId, inIIB, isArray, getLastLine(), debug);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        List<String> sigParts = TypeUtils.raiseSignature(descriptor);
        int arity = sigParts.size() - 1;
        String invokedOwner = TypeUtils.replaceSlashesWithDots(owner);
        String invokedMethodId = invokedOwner + "." + name;
        StringJoiner sigStr = new StringJoiner(",");
        for (int paramIdx = 0; paramIdx < arity; paramIdx++)
            sigStr.add(sigParts.get(paramIdx + 1));
        IRMethodInvocation irInvo = irMethod.addInvocation(name, arity,
                invokedMethodId, invokedOwner, sigParts.get(0),
                sigStr.toString(), getLastLine(), debug);
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
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC)
            addFieldAccess(owner, name, descriptor, AccessType.READ);
        else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)
            addFieldAccess(owner, name, descriptor, AccessType.WRITE);
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitInsn(int opcode) {
        // TODO: aastore
        if (opcode == Opcodes.BASTORE || opcode == Opcodes.CASTORE ||
            opcode == Opcodes.DASTORE || opcode == Opcodes.FASTORE ||
            opcode == Opcodes.LASTORE || opcode == Opcodes.IASTORE ||
            opcode == Opcodes.SASTORE) {
            // This means that a previous get-field is not an actual read but
            // must be changed to a write, to catch this pattern, e.g.:
            //   getfield #9
            //   iload_1
            //   iload_2
            //   iastore
            List<IRFieldAccess> fieldAccesses = irMethod.fieldAccesses;
            int size = fieldAccesses.size();
            if (size > 0) {
                // Remove and re-add, to force re-enumeration of write accesses.
                IRFieldAccess lastAcc = fieldAccesses.remove(size - 1);
                if (debug)
                    System.out.println("Changing field access [read->write]: " + lastAcc);
                irMethod.addFieldAccess(lastAcc.fieldId, lastAcc.fieldName, lastAcc.fieldType, AccessType.WRITE, debug);
            } else
                System.err.println("ERROR: could not find previous field access for opcode " + Printer.OPCODES[opcode]);
        }
        super.visitInsn(opcode);
    }

    private void addFieldAccess(String owner, String name, String descriptor,
                                AccessType accessType) {
        String fieldType = TypeUtils.raiseTypeId(descriptor);
        String fieldId = "<" + TypeUtils.replaceSlashesWithDots(owner) + ": " + fieldType + " " + name + ">";
        irMethod.addFieldAccess(fieldId, name, fieldType, accessType, debug);
    }
}
