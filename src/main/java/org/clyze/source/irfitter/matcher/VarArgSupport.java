package org.clyze.source.irfitter.matcher;

import java.util.*;
import java.util.stream.Collectors;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;
import org.clyze.source.irfitter.source.model.JMethodInvocation;

/** This class handles support for vararg methods. */
public class VarArgSupport {
    /**
     * A list of hard-coded metadata for Java API vararg methods.
     * Useful when the platform JAR is missing.
     */
    private static final Map<String, List<Integer>> javaVarargMethods = getJavaVarargMethods();

    /** The matching object. */
    private final Matcher matcher;
    /** The candidate source/IR invocations for vararg resolution. */
    private final List<Candidate> candidates = new LinkedList<>();
    /** Debugging mode. */
    private final boolean debug;

    /**
     * Generate a vararg resolver object.
     * @param matcher       the matching object
     * @param debug         debugging mode
     */
    public VarArgSupport(Matcher matcher, boolean debug) {
        this.matcher = matcher;
        this.debug = debug;
    }

    /**
     * Initializer for hard-coded vararg method metadata.
     * @return   the initial value of the vararg metadata
     */
    private static Map<String, List<Integer>> getJavaVarargMethods() {
        Map<String, List<Integer>> ret = new HashMap<>();
        ret.put("java.lang.Class.getConstructor", Collections.singletonList(1));
        ret.put("java.lang.Class.getDeclaredConstructor", Collections.singletonList(1));
        ret.put("java.lang.Class.getDeclaredMethod", Collections.singletonList(2));
        ret.put("java.lang.Class.getMethod", Collections.singletonList(2));
        ret.put("java.lang.String.format", Arrays.asList(2, 3));
        ret.put("java.lang.reflect.Constructor.newInstance", Collections.singletonList(1));
        ret.put("java.lang.reflect.Method.invoke", Collections.singletonList(2));
        ret.put("java.util.Arrays.asList", Collections.singletonList(1));
        return ret;
    }

    /**
     * Look up a platform method for vararg support.
     * @param methodId    the method id (e.g. "java.lang.String.format")
     * @return            a list of the different ways the method can accept varargs
     */
    private static List<Integer> getPlatformVarArgMethod(String methodId) {
        return javaVarargMethods.get(methodId);
    }

    public void recordInvocations(String srcName,
                                  Integer arity, Map<Integer, List<AbstractMethodInvocation>> srcArityMap,
                                  Map<Integer, List<AbstractMethodInvocation>> irArityMap) {
        candidates.add(new Candidate(srcName, arity, srcArityMap, irArityMap));
    }

    public void resolve(Map<String, Collection<JMethodInvocation>> invocationMap) {
        for (Candidate c : candidates) {
            Map<Integer, List<AbstractMethodInvocation>> srcMap = c.srcArityMap;
            Map<Integer, List<AbstractMethodInvocation>> irMap = c.irArityMap;
            if (srcMap.size() != 1 && irMap.size() != 1) {
                if (debug)
                    System.out.println("WARNING: vararg resolution of '" + c.srcName + "' is not yet supported.");
                continue;
            }
            if (debug)
                System.out.println("Attempting vararg resolution of '" + c.srcName + "' invocations.");
            Integer irArity = null;
            String methodId = null;
            for (Map.Entry<Integer, List<AbstractMethodInvocation>> irEntry : irMap.entrySet()) {
                irArity = irEntry.getKey();
                List<String> methodIds = irEntry.getValue().stream().map(ami -> ((IRMethodInvocation) ami).methodId).collect(Collectors.toList());
                if (methodIds.size() != 1)
                    System.out.println("WARNING: vararg resolution is not yet supported for IR invocations with multiple targets.");
                else
                    methodId = methodIds.get(0);
            }
            if (methodId != null && irArity != null) {
                List<Integer> platformVarArgMethods = getPlatformVarArgMethod(methodId);
                if (platformVarArgMethods == null)
                    continue;
                if (platformVarArgMethods.size() == 1) {
                    int srcArity = srcMap.keySet().toArray(new Integer[0])[0];
                    List<AbstractMethodInvocation> srcInvos = srcMap.get(srcArity);
                    List<AbstractMethodInvocation> irInvos = irMap.get(irArity);
                    int srcInvosSz = srcInvos.size();
                    int irInvosSz = irInvos.size();
                    if (srcInvosSz == irInvosSz) {
                        matcher.matchInvocationLists(invocationMap, srcInvos, irInvos, c.srcName, c.arity);
                    } else if (debug)
                        System.out.println("WARNING: vararg resolution failed for different src/IR arities: " +
                                srcArity + "(" + srcInvosSz + " invocations) vs. " +
                                irArity + "(" + irInvosSz + " invocations)");
                } else
                    System.out.println("WARNING: vararg resolution of overridden method '" + methodId + "' is not yet supported.");
            }
        }
    }
}

/** A candidate set of invocations for vararg resolution. */
class Candidate {
    final String srcName;
    final Integer arity;
    final Map<Integer, List<AbstractMethodInvocation>> srcArityMap;
    final Map<Integer, List<AbstractMethodInvocation>> irArityMap;

    Candidate(String srcName, Integer arity,
              Map<Integer, List<AbstractMethodInvocation>> srcArityMap,
              Map<Integer, List<AbstractMethodInvocation>> irArityMap) {
        this.srcName = srcName;
        this.arity = arity;
        this.srcArityMap = srcArityMap;
        this.irArityMap = irArityMap;
    }
}
