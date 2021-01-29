package org.clyze.source.irfitter.ir.dex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.commons.io.IOUtils;
import org.clyze.source.irfitter.ir.model.IRField;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.TypeReference;

import static org.clyze.utils.TypeUtils.replaceSlashesWithDots;

/** The .dex parser for Dalvik opcodes. */
public class DexParser {
    private final boolean debug;

    public DexParser(boolean debug) {
        this.debug = debug;
    }

    public void processDex(List<IRType> irTypes, InputStream is) {
        try {
            final File tmpDex = File.createTempFile("temp", ".dex");
            tmpDex.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tmpDex)) {
                IOUtils.copy(is, out);
            }
            DexBackedDexFile dexFile = DexFileFactory.loadDexFile(tmpDex, null);
            Set<? extends DexBackedClassDef> classes = dexFile.getClasses();
            for (DexBackedClassDef dexClass : classes) {
                String className = dexClass.toString();
                if (!className.startsWith("L") || !className.endsWith(";"))
                    System.err.println("ERROR: bad .dex class: " + className);
                else {
                    String typeId = replaceSlashesWithDots(className.substring(1, className.length()-1));
                    DexModifierPack irTypeMods = new DexModifierPack(dexClass);
                    List<String> superTypes = new LinkedList<>();
                    superTypes.add(dexClass.getSuperclass());
                    superTypes.addAll(dexClass.getInterfaces());
                    IRType irType = new IRType(typeId, superTypes, irTypeMods);
                    if (debug)
                        System.out.println("IR type = " + irType);
                    irTypes.add(irType);
                    String classPrefix = "<" + typeId + ": ";
                    for (DexBackedField dexField : dexClass.getFields()) {
                        String fieldName = dexField.getName();
                        String fieldType = TypeUtils.raiseTypeId(replaceSlashesWithDots(dexField.getType()));
                        String fieldId = classPrefix + fieldType + " " + fieldName + ">";
                        irType.fields.add(new IRField(fieldId, fieldName, fieldType, new DexModifierPack(dexField)));
                    }
                    for (DexBackedMethod dexMethod : dexClass.getMethods()) {
                        StringJoiner sj = new StringJoiner(",");
                        List<String> paramTypes = new LinkedList<>();
                        for (String pType : dexMethod.getParameterTypes()) {
                            String paramType = TypeUtils.raiseTypeId(replaceSlashesWithDots(pType));
                            sj.add(paramType);
                            paramTypes.add(paramType);
                        }
                        String mName = dexMethod.getName();
                        String retType = TypeUtils.raiseTypeId(replaceSlashesWithDots(dexMethod.getReturnType()));
                        String methodId = classPrefix + retType + " " + mName + "(" + sj.toString() + ")>";
                        IRMethod irMethod = new IRMethod(methodId, mName, retType, paramTypes,
                                new DexModifierPack(dexMethod), irTypeMods.isInterface());
                        if (debug)
                            System.out.println("IR method: " + irMethod);
                        processDexInstructions(dexMethod, irMethod, debug);
                        irType.methods.add(irMethod);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void processDexInstructions(DexBackedMethod dexMethod,
                                        IRMethod irMethod, boolean debug) {
        DexBackedMethodImplementation implementation = dexMethod.getImplementation();
        if (implementation != null) {
            for (Instruction instr : implementation.getInstructions()) {
                switch (instr.getOpcode()) {
                    case NEW_INSTANCE: {
                        TypeReference typeRef = (TypeReference) ((ReferenceInstruction) instr).getReference();
                        String typeId = TypeUtils.raiseTypeId(typeRef.getType());
                        boolean inIIB = false;
                        Integer sourceLine = null;
                        irMethod.addAllocation(typeId, inIIB, false, sourceLine, debug);
                        break;
                    }
                }
            }
        }
    }
}

