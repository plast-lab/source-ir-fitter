package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.source.irfitter.ir.model.IRElement;

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
    private final boolean debug;

    public IdMapper(boolean debug) {
        this.debug = debug;
    }

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
        long allFieldAccesses = 0, matchedFieldAccesses = 0, allUses = 0, matchedUses = 0;
        long allVariables = 0, matchedVariables = 0;
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
                    matchedInvos += countMatched(srcInvos);
                    List<JAllocation> srcAllocs = srcMethod.allocations;
                    allAllocs += srcAllocs.size();
                    matchedAllocs += countMatched(srcAllocs);
                    List<JFieldAccess> fieldAccesses = srcMethod.fieldAccesses;
                    allFieldAccesses += fieldAccesses.size();
                    matchedFieldAccesses += countMatched(fieldAccesses);
                    List<JMethodRef> methodRefs = srcMethod.methodRefs;
                    if (methodRefs != null) {
                        allMethodRefs += methodRefs.size();
                        matchedMethodRefs += countMatched(methodRefs);
                    }
                    allUses += srcMethod.elementUses.size();
                    matchedUses += countMatched(srcMethod.elementUses);
                    allVariables += srcMethod.parameters.size();
                    matchedVariables += countMatched(srcMethod.parameters);
                    for (JBlock block : srcMethod.blocks) {
                        List<JVariable> locals = block.getVariables();
                        if (locals != null) {
                            allVariables += locals.size();
                            matchedVariables += countMatched(locals);
                        }
                    }

                }
                List<JField> srcFields = srcType.fields;
                allFields += srcFields.size();
                matchedFields += countMatched(srcFields);
            }
        }
        System.out.println("== Statistics ==");
        printStat("Matched (source) types          : ", matchedTypes, allTypes);
        printStat("Matched (source) fields         : ", matchedFields, allFields);
        printStat("Matched (source) methods        : ", matchedMethods, allMethods);
        printStat("Matched (source) variables      : ", matchedVariables, allVariables);
        printStat("Matched (source) invocations    : ", matchedInvos, allInvos);
        printStat("Matched (source) allocations    : ", matchedAllocs, allAllocs);
        printStat("Matched (source) method-refs    : ", matchedMethodRefs, allMethodRefs);
        printStat("Matched (source) field accesses : ", matchedFieldAccesses, allFieldAccesses);
        printStat("Matched (source) element uses   : ", matchedUses, allUses);
    }

    @SuppressWarnings("MagicNumber")
    private static void printStat(String label, long matched, long all) {
        System.out.print(label);
        if (all == 0)
            System.out.println("      -");
        else
            System.out.printf("%6.2f%%%n", (100.0 * matched) / all);
    }

    private static <T extends Matchable> long countMatched(Collection<T> elems) {
        return elems.stream().filter(e -> {
            boolean check = e.hasBeenMatched();
            if (!check)
                System.out.println("UNMATCHED: " + e);
            return check;
        }).count();
    }

    public void registerSourceVariable(JMethod srcMethod, JVariable variable, boolean debug) {
        registerSourceVariable(srcMethod.matchId, variable, debug);
    }

    public void registerSourceVariable(String irMethodId, JVariable variable, boolean debug) {
        if (variable == null)
            return;
        if (variable.symbol == null) {
            variable.initSyntheticIRVariable(irMethodId);
            if (debug)
                System.out.println("Initialized source variable: " + variable + " -> " + variable.symbol);
        }
        variableMap.computeIfAbsent(variable.symbol.getSymbolId(), (k -> new HashSet<>())).add(variable);
    }

    /**
     * The core method that records a match between an IR and a source element.
     * @param mapping         the mapping to update
     * @param kind            the description of the element type
     * @param irElem          the IR code element
     * @param srcElem         the source code element
     * @param <IR_ELEM_T>     the type of the IR code element
     * @param <SRC_ELEM_T>    the type of the source code element
     */
    public <IR_ELEM_T extends IRElement, SRC_ELEM_T extends ElementWithPosition<IR_ELEM_T, ?>>
    void recordMatch(Map<String, Collection<SRC_ELEM_T>> mapping, String kind, IR_ELEM_T irElem,
                     SRC_ELEM_T srcElem) {
        String id = irElem.getId();
        mapping.computeIfAbsent(id, (k -> new ArrayList<>())).add(srcElem);
        matchElements(kind, irElem, srcElem, id);
    }

    public <IR_ELEM_T extends IRElement, SRC_ELEM_T extends ElementWithPosition<IR_ELEM_T, ?>>
    void matchElements(String kind, IR_ELEM_T irElem, SRC_ELEM_T srcElem, String id) {
        if (debug) {
            String pos = srcElem.pos == null ? "unknown" : srcElem.pos.toString();
            System.out.println("Match [" + kind + "] " + id + " -> " + srcElem.srcFile.getRelativePath() + ":" + pos);
        }
        if (srcElem.matchId == null) {
            srcElem.matchId = id;
            srcElem.matchElement = irElem;
            irElem.matched = true;
        } else
            System.err.println("WARNING: multiple matches: " + id + " vs. " + srcElem.matchId);
        srcElem.initSymbolFromIRElement(irElem);
    }

}
