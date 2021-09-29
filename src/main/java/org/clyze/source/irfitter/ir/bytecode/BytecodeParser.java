package org.clyze.source.irfitter.ir.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.clyze.source.irfitter.ir.IRProcessor;
import org.clyze.source.irfitter.ir.IRState;
import org.clyze.source.irfitter.ir.model.*;
import org.clyze.utils.TypeUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import static org.clyze.utils.TypeUtils.replaceSlashesWithDots;

public class BytecodeParser extends IRProcessor {
    public BytecodeParser(boolean debug, boolean enterMethods, Set<String> varArgMethods) {
        super(debug, enterMethods, varArgMethods);
    }

    public IRType processBytecode(ClassReader reader) {
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        String className = replaceSlashesWithDots(node.name);
        BytecodeModifierPack irTypeMods = new BytecodeModifierPack(node);
        List<String> superTypes = new ArrayList<>();
        String superName = node.superName;
        if (superName != null)
            superTypes.add(replaceSlashesWithDots(superName));
        node.interfaces.forEach(intf -> superTypes.add(replaceSlashesWithDots(intf)));
        IRType irType = new IRType(className, superTypes, irTypeMods);
        if (debug)
            System.out.println("IR type: " + irType);
        String classPrefix = "<" + className + ": ";
        for (FieldNode fNode : node.fields) {
            String fieldType = TypeUtils.raiseTypeId(replaceSlashesWithDots(fNode.desc));
            String fieldName = fNode.name;
            String fieldId = classPrefix + fieldType + " " + fieldName + ">";
            irType.addField(new IRField(fieldId, fieldName, fieldType, new BytecodeModifierPack(fNode)));
            if (debug)
                System.out.println("IR field: " + fieldId);
        }
        for (MethodNode mNode : node.methods) {
            String[] sig = TypeUtils.raiseSignature(mNode.desc).toArray(new String[0]);
            StringJoiner sj = new StringJoiner(",");
            List<String> paramTypes = new ArrayList<>();
            for (int i = 1; i < sig.length; i++) {
                String paramType = sig[i];
                sj.add(paramType);
                paramTypes.add(paramType);
            }
            String mName = mNode.name;
            String methodId = classPrefix + sig[0] + " " + mName + "(" + sj + ")>";
            List<IRVariable> parameters = new ArrayList<>();
            for (int i = 1; i < sig.length; i++)
                parameters.add(IRVariable.newParam(methodId, i-1));
            BytecodeModifierPack methodMods = new BytecodeModifierPack(mNode);
            IRMethod irMethod = new IRMethod(methodId, mName, sig[0], paramTypes,
                    parameters, methodMods, irTypeMods.isInterface());
            if (!methodMods.isStatic())
                irMethod.setReceiver();
            if (methodMods.isVarArgs())
                varArgMethods.add(methodId);
            if (debug)
                System.out.println("IR method: " + irMethod);
            if (enterMethods)
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
        boolean inIIB = mNode.name.equals("<clinit>");
        mNode.accept(new BytecodeMethodVisitor(method, indexToSourceLine, inIIB, debug));
        // Parse method descriptor to discover type uses.
        for (String sigType : TypeUtils.raiseSignature(mNode.desc))
            method.addSigTypeReference(sigType);
        // Exceptions also introduce type uses.
        for (String excType : mNode.exceptions)
            method.addSigTypeReference(replaceSlashesWithDots(excType));
    }

    public void processClass(IRState irState, InputStream is) {
        try {
            irState.irTypes.add(processBytecode(new ClassReader(is)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

