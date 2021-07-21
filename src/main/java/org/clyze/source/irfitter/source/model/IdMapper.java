package org.clyze.source.irfitter.source.model;

import java.util.*;

/**
 * This data structure holds all mappings between ids and source code
 * elements of different types.
 */
public class IdMapper {
    public final Map<String, Collection<JType>> typeMap = new HashMap<>();
    public final Map<String, Collection<JField>> fieldMap = new HashMap<>();
    public final Map<String, Collection<JMethod>> methodMap = new HashMap<>();
    public final Map<String, Collection<JMethodInvocation>> invocationMap = new HashMap<>();
    public final Map<String, Collection<JAllocation>> allocationMap = new HashMap<>();
    public final Map<String, Collection<JFieldAccess>> fieldAccessMap = new HashMap<>();
    public final Map<String, Collection<JMethodRef>> methodRefMap = new HashMap<>();
    public final Map<String, Collection<JVariable>> variableMap = new HashMap<>();

    /**
     * Merge all element information into a single mapping.
     * @return   a mapping containing the correspondence for all source elements
     */
    public Map<String, Collection<? extends ElementWithPosition<?, ?>>> get() {
        Map<String, Collection<? extends ElementWithPosition<?, ?>>> mapping = new HashMap<>();
        mapping.putAll(typeMap);
        mapping.putAll(fieldMap);
        mapping.putAll(methodMap);
        mapping.putAll(invocationMap);
        mapping.putAll(allocationMap);
        mapping.putAll(fieldAccessMap);
        mapping.putAll(methodRefMap);
        mapping.putAll(variableMap);
        return mapping;
    }

    public void printStats(Collection<SourceFile> sources) {
        long allTypes = 0, matchedTypes = 0, allMethods = 0, matchedMethods = 0;
        long allFields = 0, matchedFields = 0, allInvos = 0, matchedInvos = 0;
        long allAllocs = 0, matchedAllocs = 0, allMethodRefs = 0, matchedMethodRefs = 0;
        long allFieldAccesses = 0, matchedFieldAccesses = 0;
        for (SourceFile sf : sources) {
            Set<JType> srcTypes = sf.jTypes;
            allTypes += srcTypes.size();
            for (JType srcType : srcTypes) {
                if (srcType.matchId != null)
                    matchedTypes++;
                List<JMethod> srcMethods = srcType.methods;
                allMethods += srcMethods.size();
                for (JMethod srcMethod : srcMethods) {
                    if (srcMethod.matchId != null)
                        matchedMethods++;
                    List<JMethodInvocation> srcInvos = srcMethod.invocations;
                    allInvos += srcInvos.size();
                    matchedInvos += countUnmatched(srcInvos);
                    List<JAllocation> srcAllocs = srcMethod.allocations;
                    allAllocs += srcAllocs.size();
                    matchedAllocs += countUnmatched(srcAllocs);
                    List<JFieldAccess> fieldAccesses = srcMethod.fieldAccesses;
                    allFieldAccesses += fieldAccesses.size();
                    matchedFieldAccesses += countUnmatched(fieldAccesses);
                    List<JMethodRef> methodRefs = srcMethod.methodRefs;
                    if (methodRefs != null) {
                        allMethodRefs += methodRefs.size();
                        matchedMethodRefs += countUnmatched(methodRefs);
                    }
                }
                List<JField> srcFields = srcType.fields;
                allFields += srcFields.size();
                matchedFields += countUnmatched(srcFields);
            }
        }
        System.out.println("== Statistics ==");
        System.out.printf("Matched (source) types          : %6.2f%%%n", (100.0 * matchedTypes) / allTypes);
        System.out.printf("Matched (source) fields         : %6.2f%%%n", (100.0 * matchedFields) / allFields);
        System.out.printf("Matched (source) methods        : %6.2f%%%n", (100.0 * matchedMethods) / allMethods);
        System.out.printf("Matched (source) invocations    : %6.2f%%%n", (100.0 * matchedInvos) / allInvos);
        System.out.printf("Matched (source) allocations    : %6.2f%%%n", (100.0 * matchedAllocs) / allAllocs);
        System.out.printf("Matched (source) method-refs    : %6.2f%%%n", (100.0 * matchedMethodRefs) / allMethodRefs);
        System.out.printf("Matched (source) field accesses : %6.2f%%%n", (100.0 * matchedFieldAccesses) / allFieldAccesses);
    }

    private static <T extends ElementWithPosition<?, ?>> long countUnmatched(Collection<T> elems) {
        return elems.stream().filter(e -> e.matchId != null).count();
    }
}
