package org.clyze.source.irfitter.ir.dex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.ir.IRProcessor;
import org.clyze.source.irfitter.ir.model.IRField;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRVariable;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.source.irfitter.source.model.JInit;
import org.clyze.utils.TypeUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.value.DexBackedArrayEncodedValue;
import org.jf.dexlib2.dexbacked.value.DexBackedTypeEncodedValue;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.iface.value.EncodedValue;

import static org.clyze.utils.TypeUtils.replaceSlashesWithDots;

/** The .dex parser for Dalvik opcodes. */
public class DexParser extends IRProcessor {
    public DexParser(boolean debug, boolean enterMethods, Set<String> varArgMethods) {
        super(debug, enterMethods, varArgMethods);
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
                    List<String> superTypes = new ArrayList<>();
                    superTypes.add(raiseLowLevelType(dexClass.getSuperclass()));
                    dexClass.getInterfaces().forEach(intf -> superTypes.add(raiseLowLevelType(intf)));
                    IRType irType = new IRType(typeId, superTypes, irTypeMods);
                    if (debug)
                        System.out.println("IR type: " + irType);
                    irTypes.add(irType);
                    String classPrefix = "<" + typeId + ": ";
                    for (DexBackedField dexField : dexClass.getFields()) {
                        String fieldName = dexField.getName();
                        String fieldType = raiseLowLevelType(dexField.getType());
                        String fieldId = classPrefix + fieldType + " " + fieldName + ">";
                        if (debug)
                            System.out.println("IR field: " + fieldId);
                        irType.addField(new IRField(fieldId, fieldName, fieldType, new DexModifierPack(dexField)));
                    }
                    for (DexBackedMethod dexMethod : dexClass.getMethods()) {
                        StringJoiner sj = new StringJoiner(",");
                        List<String> paramTypes = new ArrayList<>();
                        for (String pType : dexMethod.getParameterTypes()) {
                            String paramType = raiseLowLevelType(pType);
                            sj.add(paramType);
                            paramTypes.add(paramType);
                        }
                        String mName = dexMethod.getName();
                        String retType = raiseLowLevelType(dexMethod.getReturnType());
                        String methodId = classPrefix + retType + " " + mName + "(" + sj + ")>";
                        List<IRVariable> parameters = new ArrayList<>();
                        for (int i = 0; i < paramTypes.size(); i++)
                            parameters.add(IRVariable.newParam(methodId, i));
                        DexModifierPack methodMods = new DexModifierPack(dexMethod);
                        IRMethod irMethod = new IRMethod(methodId, mName, retType, paramTypes,
                                parameters, methodMods, irTypeMods.isInterface());
                        if (!methodMods.isStatic())
                            irMethod.setReceiver();
                        if (methodMods.isVarArgs())
                            varArgMethods.add(methodId);
                        paramTypes.forEach(irMethod::addSigTypeReference);
                        irMethod.addSigTypeReference(retType);
                        for (Annotation annotation : dexMethod.getAnnotations()) {
                            String annType = raiseLowLevelType(annotation.getType());
                            irMethod.addSigTypeReference(annType);
                            processSpecialAnnotations(annotation, annType, irMethod);
                        }
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

    private void processSpecialAnnotations(Annotation annotation, String annType, IRMethod irMethod) {
        if (annType.equals("dalvik.annotation.Throws"))
            for (AnnotationElement annElem : annotation.getElements())
                if (annElem.getName().equals("value")) {
                    EncodedValue encAnnValue = annElem.getValue();
                    if (encAnnValue instanceof DexBackedArrayEncodedValue)
                        for (EncodedValue encodedValue : ((DexBackedArrayEncodedValue) encAnnValue).getValue())
                            if (encodedValue instanceof DexBackedTypeEncodedValue)
                                irMethod.addSigTypeReference(((DexBackedTypeEncodedValue) encodedValue).getValue());
                }
    }

    private void processDexInstructions(DexBackedMethod dexMethod,
                                        IRMethod irMethod, boolean debug) {
        DexBackedMethodImplementation implementation = dexMethod.getImplementation();
        if (implementation != null) {
            for (Instruction instr : implementation.getInstructions()) {
                switch (instr.getOpcode()) {
                    case NEW_INSTANCE:
                    case NEW_ARRAY:
                    case FILLED_NEW_ARRAY:
                    case FILLED_NEW_ARRAY_RANGE: {
                        String typeId = raisedJvmTypeOf((ReferenceInstruction) instr);
                        // TODO: support non-static initializer blocks
                        boolean inIIB = irMethod.name.equals(JInit.CLINIT);
                        // TODO: read source line
                        Integer sourceLine = null;
                        irMethod.addAllocation(typeId, inIIB, false, sourceLine, debug);
                        break;
                    }
                    case CONST_CLASS: {
                        String typeId = raisedJvmTypeOf((ReferenceInstruction) instr);
                        irMethod.addTypeReference(typeId);
                        break;
                    }
                    case CHECK_CAST: {
                        String typeId = raisedJvmTypeOf((ReferenceInstruction)instr);
                        irMethod.addTypeReference(typeId);
                        // TODO: read source line
                        Integer sourceLine = null;
                        irMethod.addCast(typeId, sourceLine, debug);
                        break;
                    }
                    case INVOKE_DIRECT:
                    case INVOKE_VIRTUAL:
                    case INVOKE_STATIC:
                    case INVOKE_INTERFACE:
                    case INVOKE_SUPER:
                    case INVOKE_DIRECT_RANGE:
                    case INVOKE_VIRTUAL_RANGE:
                    case INVOKE_STATIC_RANGE:
                    case INVOKE_INTERFACE_RANGE:
                    case INVOKE_SUPER_RANGE: {
                        DexBackedMethodReference mRef = (DexBackedMethodReference) ((ReferenceInstruction)instr).getReference();
                        int arity = mRef.getParameterTypes().size();
                        String methodName = mRef.getName();
                        String targetType = TypeUtils.raiseTypeId(mRef.getDefiningClass());
                        String invokedMethodId = targetType + '.' + methodName;
                        StringJoiner sigStr = new StringJoiner(",");
                        for (String parameterType : mRef.getParameterTypes())
                            sigStr.add(raiseLowLevelType(parameterType));
                        String targetRetType = raiseLowLevelType(mRef.getReturnType());
                        // TODO: read source line
                        Integer sourceLine = null;
                        irMethod.addInvocation(methodName, arity, invokedMethodId, targetType, targetRetType, sigStr.toString(), sourceLine, debug);
                        break;
                    }
                    case SPUT:
                    case SPUT_WIDE:
                    case SPUT_OBJECT:
                    case SPUT_BOOLEAN:
                    case SPUT_BYTE:
                    case SPUT_CHAR:
                    case SPUT_SHORT:
                    case IPUT:
                    case IPUT_WIDE:
                    case IPUT_OBJECT:
                    case IPUT_BOOLEAN:
                    case IPUT_BYTE:
                    case IPUT_CHAR:
                    case IPUT_SHORT: {
                        writeFieldAccess(((ReferenceInstruction)instr).getReference(), irMethod, AccessType.WRITE);
                        break;
                    }
                    case IGET:
                    case IGET_WIDE:
                    case IGET_OBJECT:
                    case IGET_BOOLEAN:
                    case IGET_BYTE:
                    case IGET_CHAR:
                    case IGET_SHORT:
                    case SGET:
                    case SGET_WIDE:
                    case SGET_OBJECT:
                    case SGET_BOOLEAN:
                    case SGET_BYTE:
                    case SGET_CHAR:
                    case SGET_SHORT: {
                        writeFieldAccess(((ReferenceInstruction)instr).getReference(), irMethod, AccessType.READ);
                        break;
                    }
                }
            }
            for (DexBackedTryBlock tryBlock : implementation.getTryBlocks())
                for (DexBackedExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                    String exceptionType = handler.getExceptionType();
                    if (exceptionType != null)
                        irMethod.addTypeReference(raiseLowLevelType(exceptionType));
                }
        }
    }

    private void writeFieldAccess(Reference ref, IRMethod irMethod, AccessType accessType) {
        if (ref instanceof FieldReference) {
            FieldReference fieldRef = (FieldReference) ref;
            String fieldName = fieldRef.getName();
            String fieldType = raiseLowLevelType(fieldRef.getType());
            String fieldId = "<" + TypeUtils.raiseTypeId(fieldRef.getDefiningClass()) +
                    ": " + fieldType + " " + fieldName + ">";
            if (debug)
                System.out.println("Adding access to field: " + fieldId);
            irMethod.addFieldAccess(fieldId, fieldName, fieldType, accessType, debug);
        } else
            System.err.println("Unknown reference, field expected: " + ref);
    }

    static String raisedJvmTypeOf(ReferenceInstruction instr) {
        return TypeUtils.raiseTypeId(((TypeReference) instr.getReference()).getType());
    }

    /**
     * Helper method to simultaneously raise a type and replace slashes with dots.
     * @param desc   the low-level type string
     * @return       the fully-qualified Java type
     */
    private static String raiseLowLevelType(String desc) {
        return TypeUtils.raiseTypeId(replaceSlashesWithDots(desc));
    }
}