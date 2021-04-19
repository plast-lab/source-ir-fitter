package org.clyze.source.irfitter.ir.model;

import java.util.*;
import java.util.function.BiFunction;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.utils.TypeUtils;

/**
 * A low-level representation of a method.
 */
public class IRMethod extends IRElement implements AbstractMethod {
    public final List<IRMethodInvocation> invocations = new ArrayList<>();
    public final List<IRAllocation> allocations = new ArrayList<>();
    public final List<IRFieldAccess> fieldAccesses = new ArrayList<>();
    private final Map<String, Integer> invocationCounters = new HashMap<>();
    private final Map<String, Integer> allocationCounters = new HashMap<>();
    private final Map<String, Integer> fieldAccessCounters = new HashMap<>();
    private Set<String> typeReferences = null;
    private Set<String> sigTypeReferences = null;
    public List<IRMethodRef> methodRefs = null;
    private Map<String, Integer> methodRefCounters = null;
    public final String name;
    public final String returnType;
    public final List<String> paramTypes;
    public final List<IRVariable> parameters;
    public final IRModifierPack mp;
    public final int arity;
    public final boolean isInterface;
    public IRVariable receiver = null;

    public IRMethod(String id, String name, String returnType, List<String> paramTypes,
                    List<IRVariable> parameters, IRModifierPack mp, boolean isInterface) {
        super(id);
        this.name = name;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
        this.parameters = parameters;
        this.mp = mp;
        this.arity = paramTypes.size();
        this.isInterface = isInterface;
    }

    public IRMethodInvocation addInvocation(String methodName, int arity,
                                            String invokedMethodId,
                                            Integer sourceLine, boolean debug) {
        String methodId = getId();
        return addNumberedElement(invocationCounters, invocations, methodId, invokedMethodId,
                ((counter, elemId) -> {
                    IRMethodInvocation irInvo = new IRMethodInvocation(elemId, methodId,
                            methodName, arity, invokedMethodId, counter, sourceLine);
                    if (debug)
                        System.out.println("IR invocation: " + irInvo);
                    return irInvo;
        }));
    }

    public void addAllocation(String typeId, boolean inIIB, boolean isArray,
                              Integer sourceLine, boolean debug) {
        String typeId0 = isArray ? typeId + "[]" : typeId;
        String methodId = getId();
        addNumberedElement(allocationCounters, allocations, methodId, "new " + typeId0,
                ((counter, elemId) -> {
                    IRAllocation irAlloc = new IRAllocation(elemId, typeId0, methodId, inIIB, isArray, sourceLine);
                    if (debug)
                        System.out.println("IR allocation: " + irAlloc);
                    return irAlloc;
                }));
    }

    public IRFieldAccess addFieldAccess(String fieldId, String fieldName, String fieldType, boolean read, boolean debug) {
        String key = fieldName + (read ? "+R" : "+W");
        return addNumberedElement(fieldAccessCounters, fieldAccesses, getId(), key,
                ((counter, elemId) -> {
                    IRFieldAccess irFieldAccess = new IRFieldAccess(elemId, fieldId, fieldName, fieldType, read);
                    if (debug)
                        System.out.println("IR field " + (read ? "read" : "write") + ": " + irFieldAccess.getId());
                    return irFieldAccess;
                }));
    }

    private static <T>
    T addNumberedElement(Map<String, Integer> counters, List<T> target,
                         String methodId, String key, BiFunction<Integer, String, T> generator) {
        Integer counter = counters.get(key);
        if (counter == null)
            counter = 0;
        String elemId = methodId + "/" + key + "/" + counter;
        T res = generator.apply(counter, elemId);
        target.add(res);
        counters.put(key, counter + 1);
        return res;
    }

    @Override
    public List<? extends AbstractMethodInvocation> getInvocations() {
        return invocations;
    }

    @Override
    public String toString() {
        return getId();
    }

    /**
     * Register a reference to a type in the method body (such as {@code C.class}).
     * @param type  the (fully-qualified) type
     */
    public void addTypeReference(String type) {
        if (typeReferences == null)
            typeReferences = new HashSet<>();
//        System.out.println("Found type reference to " + type + " in " + this);
        typeReferences.add(type);
    }

    /**
     * Register a reference to a method in the method body (such as {@code A::m}).
     * @param methodId    the full id of the method
     * @param name        the name (e.g. "m")
     * @param sourceLine  the source line information (if available)
     * @param debug       debugging mode
     */
    public void addMethodRef(String methodId, String name, Integer sourceLine, boolean debug) {
        if (methodRefs == null)
            methodRefs = new ArrayList<>();
        if (methodRefCounters == null)
            methodRefCounters = new HashMap<>();
        addNumberedElement(methodRefCounters, methodRefs, getId(), "<method-ref-" + name + ">",
                (counter, elemId) -> {
                    IRMethodRef ref = new IRMethodRef(elemId, methodId, name, sourceLine);
                    if (debug)
                        System.out.println("IR method reference: " + ref);
                    return ref;
                });
    }

    /**
     * Get the class/interface references that exist in the method body.
     * @return    a set of (fully-qualified) types
     */
    public Set<String> getTypeReferences() {
        return typeReferences;
    }

    /**
     * Register a reference to a type in the method signature.
     * @param type  the (fully-qualified) type
     */
    public void addSigTypeReference(String type) {
        if (TypeUtils.isPrimitiveType(type))
            return;
        if (sigTypeReferences == null)
            sigTypeReferences = new HashSet<>();
//        System.out.println("Found signature type reference to " + type + " in " + this);
        sigTypeReferences.add(type);
    }

    /**
     * Get the class/interface references that exist in the method signature.
     * @return    a set of (fully-qualified) types
     */
    public Set<String> getSigTypeReferences() {
        return sigTypeReferences;
    }

    @Override
    public void addReferencedTypesTo(Collection<String> target) {
        Set<String> typeReferences = getTypeReferences();
        if (typeReferences != null)
            target.addAll(typeReferences);
        Set<String> sigTypeReferences = getSigTypeReferences();
        if (sigTypeReferences != null)
            target.addAll(sigTypeReferences);
        addTypeRefs(target, invocations);
        addTypeRefs(target, allocations);
    }

    @Override
    public boolean isVarArgs() {
        return mp.isVarArgs();
    }

    /**
     * Sets up the hidden receiver parameter for instance methods.
     */
    public void setReceiver() {
        this.receiver = IRVariable.newThis(getId());
    }
}
