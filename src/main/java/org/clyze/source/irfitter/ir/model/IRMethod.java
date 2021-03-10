package org.clyze.source.irfitter.ir.model;

import java.util.*;
import java.util.function.BiFunction;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.utils.TypeUtils;

public class IRMethod extends IRElement implements AbstractMethod {
    public final List<IRMethodInvocation> invocations = new LinkedList<>();
    public final List<IRAllocation> allocations = new LinkedList<>();
    private final Map<String, Integer> invocationCounters = new HashMap<>();
    private final Map<String, Integer> allocationCounters = new HashMap<>();
    private Set<String> typeReferences = null;
    private Set<String> sigTypeReferences = null;
    public final String name;
    public final String returnType;
    public final List<String> paramTypes;
    public final IRModifierPack mp;
    public final int arity;
    public final boolean isInterface;

    public IRMethod(String id, String name, String returnType, List<String> paramTypes,
                    IRModifierPack mp, boolean isInterface) {
        super(id);
        this.name = name;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
        this.mp = mp;
        this.arity = paramTypes.size();
        this.isInterface = isInterface;
    }

    public IRMethodInvocation addInvocation(String methodId, String methodName,
                                            int arity, String invokedMethodId,
                                            Integer sourceLine, boolean debug) {
        return addNumberedElement(invocationCounters, invocations, methodId, invokedMethodId,
                ((counter, elemId) -> {
                    IRMethodInvocation irInvo = new IRMethodInvocation(elemId, methodId,
                            methodName, arity, invokedMethodId, counter, sourceLine);
                    if (debug)
                        System.out.println("Found IR invocation: " + irInvo);
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
                        System.out.println("Found IR allocation: " + irAlloc);
                    return irAlloc;
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
        if (type.equals("void") || TypeUtils.isPrimitiveType(type))
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
}
