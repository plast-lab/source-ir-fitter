package org.clyze.source.irfitter.ir.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.clyze.source.irfitter.ir.model.*;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import static org.clyze.utils.TypeUtils.replaceSlashesWithDots;
import static org.objectweb.asm.Opcodes.ACC_VARARGS;

public class BytecodeParser {
    private final boolean debug;

    public BytecodeParser(boolean debug) {
        this.debug = debug;
    }

    public IRType processBytecode(ClassReader reader) {
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        String className = replaceSlashesWithDots(node.name);
        BytecodeModifierPack irTypeMods = new BytecodeModifierPack(node);
        List<String> superTypes = new LinkedList<>();
        superTypes.add(replaceSlashesWithDots(node.superName));
        node.interfaces.forEach(intf -> superTypes.add(replaceSlashesWithDots(intf)));
        IRType irType = new IRType(className, superTypes, irTypeMods);
        if (debug)
            System.out.println("IR type: " + irType);
        String classPrefix = "<" + className + ": ";
        for (FieldNode fNode : node.fields) {
            String fieldType = replaceSlashesWithDots(fNode.desc);
            String fieldType0 = TypeUtils.raiseTypeId(fieldType);
            String fieldName = fNode.name;
            String fieldId = classPrefix + fieldType0 + " " + fieldName + ">";
            irType.fields.add(new IRField(fieldId, fieldName, fieldType0, new BytecodeModifierPack(fNode)));
            if (debug)
                System.out.println("IR field: " + fieldId);
        }
        for (MethodNode mNode : node.methods) {
            String[] sig = TypeUtils.raiseSignature(mNode.desc).toArray(new String[0]);
            StringJoiner sj = new StringJoiner(",");
            List<String> paramTypes = new LinkedList<>();
            for (int i = 1; i < sig.length; i++) {
                String paramType = sig[i];
                sj.add(paramType);
                paramTypes.add(paramType);
            }
            String mName = mNode.name;
            String methodId = classPrefix + sig[0] + " " + mName + "(" + sj.toString() + ")>";
            IRMethod irMethod = new IRMethod(methodId, mName, sig[0], paramTypes,
                    new BytecodeModifierPack(mNode), irTypeMods.isInterface());
            if (debug)
                System.out.println("IR method: " + irMethod);
            processBytecodeInstructions(irMethod, mNode);
            irType.methods.add(irMethod);
        }
        return irType;
    }

    private void processBytecodeInstructions(IRMethod method, MethodNode mNode) {
        // First, read line-number-to-label table.
        Map<Label, Integer> indexToSourceLine = new HashMap<>();
        for (AbstractInsnNode instrNode : mNode.instructions) {
            if (instrNode instanceof LineNumberNode) {
                LineNumberNode lnn = (LineNumberNode) instrNode;
                indexToSourceLine.put(lnn.start.getLabel(), lnn.line);
            }
        }
        // Read the method again to map bytecode offsets to source positions.
        mNode.accept(new BytecodeMethodVisitor(method, indexToSourceLine, debug));
        // Parse method descriptor to discover type uses.
        for (String sigType : TypeUtils.raiseSignature(mNode.desc))
            method.addSigTypeReference(sigType);
        // Exceptions also introduce type uses.
        for (String excType : mNode.exceptions)
            method.addSigTypeReference(replaceSlashesWithDots(excType));
    }

    public void processClass(List<IRType> irTypes, InputStream is) {
        try {
            ClassReader reader = new ClassReader(is);
            irTypes.add((new BytecodeParser(debug)).processBytecode(reader));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

