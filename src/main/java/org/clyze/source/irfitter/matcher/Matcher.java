package org.clyze.source.irfitter.matcher;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.*;
import org.clyze.source.irfitter.source.model.*;

/**
 * This class gathers all logic that matches source elements with IR elements.
 */
public class Matcher {
    /** If true, enable experimental/best-effort heuristics that may lose information. */
    private final boolean lossy;
    /** Debugging mode. */
    private final boolean debug;
    /** The source file where matching will happen. */
    private final SourceFile sourceFile;

    /**
     *
     * @param sourceFile   the source file where matching will happen
     * @param lossy        if true, use unsafe heuristics
     * @param debug        debugging mode
     */
    public Matcher(SourceFile sourceFile, boolean lossy, boolean debug) {
        this.sourceFile = sourceFile;
        this.lossy = lossy;
        this.debug = debug;
    }

    /**
     * Main entry point, matches source types and IR types. Call this method
     * before matching elements inside types (such as methods or invocations).
     * @param idMapper   the source-to-IR mapper object
     * @param irTypes    the IR type representations
     */
    public void matchTypes(IdMapper idMapper, Collection<IRType> irTypes) {
        for (JType jt : sourceFile.jTypes) {
            if (debug)
                System.out.println("Matching source type: " + jt);
            String id = jt.getFullyQualifiedName(sourceFile.packageName);
            //System.out.println("Mapping " + jt.name + " -> " + id);
            for (IRType irType : irTypes)
                if (!irType.matched && irType.getId().equals(id)) {
                    recordMatch(idMapper.typeMap, "type", irType, jt);
                    matchFields(idMapper.fieldMap, irType.fields, jt.fields);
                    matchMethods(idMapper, irType.methods, jt.methods, irType.outerTypes);
                    break;
                }
        }
        // When all types have been resolved, mark declaring symbol ids.
        for (JType jt : sourceFile.jTypes)
            jt.updateDeclaringSymbolId();
    }

    private void matchFields(Map<String, Collection<JField>> mapping,
                             Collection<IRField> irFields, Collection<JField> srcFields) {
        if (debug)
            System.out.println("Matching " + irFields.size() + " IR fields against " + srcFields.size() + " source fields...");
        for (JField srcField : srcFields)
            for (IRField irField : irFields) {
                if (!irField.matched && irField.name.equals(srcField.name)) {
                    recordMatch(mapping, "field", irField, srcField);
                    break;
                }
            }
    }

    /**
     * Match source/IR methods for a given type.
     * @param idMapper    the mapping object to update
     * @param irMethods   the methods found in the IR
     * @param srcMethods  the methods found in the source
     * @param outerTypes  the outer types (for inner classes)
     */
    private void matchMethods(IdMapper idMapper, List<IRMethod> irMethods,
                              List<JMethod> srcMethods, List<String> outerTypes) {
        if (debug)
            System.out.println("Matching " + irMethods.size() + " IR methods against " + srcMethods.size() + " methods...");

        Map<String, Collection<JMethod>> methodMap = idMapper.methodMap;

        // Match same-name methods without overloading.
        if (debug)
            System.out.println("* Matching same-name methods without overloading...");
        Map<String, Set<JMethod>> srcOverloading = getOverloadingTable(srcMethods, (JMethod::getLowLevelName));
        Map<String, Set<IRMethod>> irOverloading = getOverloadingTable(irMethods, (m -> m.name));
        for (Map.Entry<String, Set<JMethod>> srcEntry : srcOverloading.entrySet()) {
            Set<JMethod> srcMatches = srcEntry.getValue();
            String srcMethodName = srcEntry.getKey();
            Set<IRMethod> irMatches = irOverloading.get(srcMethodName);
            if (irMatches == null) {
                if (!JInit.isInitName(srcMethodName))
                    System.out.println("WARNING: method " + srcMethodName + "() does not match any bytecode methods: " + srcMatches);
                continue;
            }
            // Match methods with unique names both in sources and IR.
            if (srcMatches.size() == 1 && irMatches.size() == 1) {
                JMethod srcMethod = srcMatches.iterator().next();
                IRMethod irMethod = irMatches.iterator().next();
                recordMatch(methodMap, "method", irMethod, srcMethod);
            } else
                matchMethodsWithSameNameArity(methodMap, srcMatches, irMatches);
        }

        // Do fuzzy type matching on method signatures.
        matchMethodSignaturesFuzzily(methodMap, srcMethods, irMethods);

        // Match compiler-augmented constructors of inner classes.
        if (outerTypes != null)
            matchInnerConstructors(methodMap, srcMethods, irMethods, outerTypes);

        // After methods have been matched, match method invocations and allocations.
        matchInvocations(idMapper, srcMethods);
        matchAllocations(idMapper.allocationMap, srcMethods);
        matchFieldAccesses(idMapper.fieldAccessMap, srcMethods);
    }

    /**
     * Match constructors of inner classes, which take additional outer class
     * arguments in the IR.
     * @param methodMap     the method map to update
     * @param srcMethods    the class methods found in the source
     * @param irMethods     the class methods found in the IR
     * @param outerTypes    the outer classes found in the IR
     */
    private void matchInnerConstructors(Map<String, Collection<JMethod>> methodMap,
                                        List<JMethod> srcMethods, List<IRMethod> irMethods,
                                        List<String> outerTypes) {
        List<JMethod> srcInits = srcMethods.stream().filter(m -> m.matchId == null && m.getLowLevelName().equals("<init>")).collect(Collectors.toList());
        List<IRMethod> irInits = irMethods.stream().filter(m -> m.name.equals("<init>")).collect(Collectors.toList());
        List<String> outerSimpleTypes = outerTypes.stream().map(Utils::getSimpleType).collect(Collectors.toList());
        for (JMethod srcInit : srcInits)
            for (IRMethod irInit : irInits) {
                List<String> irParamTypes = irInit.paramTypes.stream().map(Utils::getSimpleType).collect(Collectors.toList());
                List<String> srcParamTypes = new ArrayList<>(outerSimpleTypes);
                for (JParameter parameter : srcInit.parameters)
                    srcParamTypes.add(Utils.getSimpleSourceType(parameter.type));
                if (debug)
                    System.out.println("Checking source/IR constructors: " +
                            srcInit + " vs. " + irInit + ": " +
                            srcParamTypes + " vs. " + irParamTypes);
                if (irParamTypes.equals(srcParamTypes))
                    recordMatch(methodMap, "method", irInit, srcInit);
            }
    }

    /**
     * Match source/IR field accesses in source methods. The corresponding IR
     * methods are implicit (assumed to have already been resolved, pointed to
     * by source methods).
     * @param fieldAccessMap   the map object to update
     * @param srcMethods       the source methods
     */
    private void matchFieldAccesses(Map<String, Collection<JFieldAccess>> fieldAccessMap, List<JMethod> srcMethods) {
        for (JMethod srcMethod : srcMethods) {
            if (srcMethod.matchId == null)
                continue;
            IRMethod irMethod = srcMethod.matchElement;
            // Group accesses by field name.
            Map<String, List<JFieldAccess>> srcAccessesByName = new HashMap<>();
            for (JFieldAccess srcFieldAcc : srcMethod.fieldAccesses)
                srcAccessesByName.computeIfAbsent(srcFieldAcc.fieldName, (x) -> new ArrayList<>()).add(srcFieldAcc);
            if (srcAccessesByName.size() == 0)
                continue;
            Map<String, List<IRFieldAccess>> irAccessesByName = new HashMap<>();
            for (IRFieldAccess irFieldAcc : irMethod.fieldAccesses)
                irAccessesByName.computeIfAbsent(irFieldAcc.fieldName, (x) -> new ArrayList<>()).add(irFieldAcc);
            if (irAccessesByName.size() == 0)
                continue;
            if (debug) {
                System.out.println("Field accesses by name (IR/SRC): " + irAccessesByName.size() + "/" + srcAccessesByName.size() + " in " + srcMethod);
                srcAccessesByName.forEach((k, v) -> System.out.println(k + " -> " + v));
                irAccessesByName.forEach((k, v) -> System.out.println(k + " -> " + v));
            }
            for (Map.Entry<String, List<JFieldAccess>> srcEntry : srcAccessesByName.entrySet()) {
                String fieldName = srcEntry.getKey();
                List<IRFieldAccess> irAccesses = irAccessesByName.get(fieldName);
                if (irAccesses == null)
                    continue;
                List<JFieldAccess> srcAccesses = srcEntry.getValue();
                int srcSize = srcAccesses.size();
                int irSize = irAccesses.size();
                if (srcSize == irSize && srcSize > 0) {
                    if (debug)
                        System.out.println("Matching " + srcSize + " '" + fieldName + "' field accesses in " + srcMethod + " with " + irMethod);
                    for (int i = 0; i < srcSize; i++) {
                        IRFieldAccess irAccess = irAccesses.get(i);
                        JFieldAccess srcAccess = srcAccesses.get(i);
                        boolean irRead = irAccess.read;
                        boolean srcRead = srcAccess.read;
                        if (irRead == srcRead)
                            recordMatch(fieldAccessMap, "field-access", irAccess, srcAccess);
                        else {
                            System.out.println("WARNING: incompatible field accesses found, aborting matching for field '" + fieldName + "' (IR/SRC 'read'): " + irRead + "/" + srcRead);
                            break;
                        }
                    }
                } else if (debug)
                    System.out.println("Field accesses ignored: (IR=" + irSize+ "/SRC=" + srcSize + ") in " + srcMethod);
            }
        }
    }

    /**
     * Match allocations in source methods. The corresponding IR methods are
     * implicit (assumed to have already been resolved, pointed to by source methods).
     * @param allocationMap    the allocation map to update
     * @param srcMethods       the source methods
     */
    private void matchAllocations(Map<String, Collection<JAllocation>> allocationMap,
                                  List<JMethod> srcMethods) {
        for (JMethod srcMethod : srcMethods) {
            if (srcMethod.matchId == null)
                continue;
            // Group source allocations by type.
            Map<String, List<JAllocation>> srcAllocationsByType = new HashMap<>();
            for (JAllocation srcAlloc : srcMethod.allocations)
                registerAllocationByType(srcAllocationsByType, srcAlloc);
            // Group IR allocations by type.
            IRMethod irMethod = srcMethod.matchElement;
            Map<String, List<IRAllocation>> irAllocationsByType = new HashMap<>();
            for (IRAllocation irAlloc : irMethod.allocations)
                registerAllocationByType(irAllocationsByType, irAlloc);
            // Match same-size groups.
            for (Map.Entry<String, List<JAllocation>> srcEntry : srcAllocationsByType.entrySet()) {
                for (Map.Entry<String, List<IRAllocation>> irEntry : irAllocationsByType.entrySet()) {
                    String simpleType = srcEntry.getKey();
                    if (simpleType.equals(irEntry.getKey())) {
                        List<JAllocation> srcAllocs = srcEntry.getValue();
                        List<IRAllocation> irAllocs = irEntry.getValue();
                        int srcSize = srcAllocs.size();
                        int irSize = irAllocs.size();
                        if (srcSize == irSize) {
                            for (int i = 0; i < srcSize; i++) {
                                IRAllocation irAlloc = irAllocs.get(i);
                                JAllocation srcAlloc = srcAllocs.get(i);
                                recordMatch(allocationMap, "allocation", irAlloc, srcAlloc);
                            }
                        } else if (lossy) {
                            if (debug) {
                                System.out.println("WARNING: cannot match allocations of type " + simpleType + ":");
                                System.out.println("Source allocations (" + srcSize + "):\n" + srcAllocs);
                                System.out.println("IR allocations (" + irSize + "):\n" + irAllocs);
                                System.out.println("Attempting matching by line number...");
                            }
                            matchSameLineFirstAllocations(allocationMap, srcAllocs, irAllocs);
                        }
                    }
                }
            }
        }

        // Last step: for the unmatched IR allocations that still have
        // source line information, generate metadata. This can help with
        // mapping compiler-generated allocations (such as StringBuilder objects
        // for string concatenation).
        for (JMethod srcMethod : srcMethods) {
            IRMethod irMethod = srcMethod.matchElement;
            if (irMethod == null)
                continue;
            for (IRAllocation irAlloc : irMethod.allocations) {
                if (!irAlloc.matched) {
                    Integer line = irAlloc.getSourceLine();
                    if (line != null) {
                        Position pos = new Position(line, line, 0, 0);
                        JAllocation approxSrcAlloc = srcMethod.addAllocation(sourceFile, pos, irAlloc.getSimpleType());
                        if (debug)
                            System.out.println("Adding approximate allocation: " + approxSrcAlloc);
                        recordMatch(allocationMap, "allocation", irAlloc, approxSrcAlloc);
                        approxSrcAlloc.symbol.setSource(false);
                    }
                }
            }
        }
    }

    private <T extends AbstractAllocation>
    void registerAllocationByType(Map<String, List<T>> map, T alloc) {
        String type = alloc.getSimpleType();
        map.computeIfAbsent(type, (k -> new LinkedList<>())).add(alloc);
    }

    /**
     * If there is only one source allocation in this line, match it with the
     * first IR allocation in this same line.
     * @param allocationMap  the allocation map to update
     * @param srcAllocs      the source code allocations
     * @param irAllocs       the IR allocations
     */
    private void matchSameLineFirstAllocations(Map<String, Collection<JAllocation>> allocationMap,
                                               List<JAllocation> srcAllocs, List<IRAllocation> irAllocs) {
        Map<Long, List<JAllocation>> srcAllocsPerLine = new HashMap<>();
        for (JAllocation srcAlloc : srcAllocs)
            srcAllocsPerLine.computeIfAbsent(srcAlloc.pos.getStartLine(), (k -> new ArrayList<>())).add(srcAlloc);
        Map<Long, Collection<IRAllocation>> irAllocsPerLine = new HashMap<>();
        for (IRAllocation irAlloc : irAllocs) {
            Integer sourceLine = irAlloc.getSourceLine();
            if (sourceLine != null)
                irAllocsPerLine.computeIfAbsent(sourceLine.longValue(), (k -> new ArrayList<>())).add(irAlloc);
        }
        for (Map.Entry<Long, List<JAllocation>> entry : srcAllocsPerLine.entrySet()) {
            List<JAllocation> lineAllocs = entry.getValue();
            if (lineAllocs.size() == 1) {
                Collection<IRAllocation> irAllocations = irAllocsPerLine.get(entry.getKey());
                if (irAllocations != null)
                    for (IRAllocation irAlloc : irAllocations) {
                        recordMatch(allocationMap, "allocation", irAlloc, lineAllocs.get(0));
                        break;
                    }
            }
        }
    }

    /**
     * Match invocations by name/arity combination when both source and IR
     * sets for a combination have the same size (e.g. two IR invocations and
     * two source invocations for method name "m" and arity "2".
     * This matching requires that the source parser:
     * (a) visits *all* invocations and
     * (b) the source invocations are visited in the same order as in the IR.
     * Invocation matching is performed for each method, so any errors (such as
     * wrong visit order due to new Java syntax not yet visited) only affect
     * specific name/arity pairs.
     *
     * @param idMapper   the source-to-IR mapper object
     * @param srcMethods the source methods
     */
    private void matchInvocations(IdMapper idMapper, List<JMethod> srcMethods) {
        if (debug)
            System.out.println("* Matching method invocations by name/arity...");

        Map<String, Collection<JMethodInvocation>> invocationMap = idMapper.invocationMap;
        for (JMethod srcMethod : srcMethods) {
            IRMethod irMethod = srcMethod.matchElement;
            Map<String, Map<Integer, List<AbstractMethodInvocation>>> irSigs = computeAbstractSignatures(irMethod);
            if (irSigs == null)
                continue;
            Map<String, Map<Integer, List<AbstractMethodInvocation>>> srcSigs = computeAbstractSignatures(srcMethod);
            if (srcSigs == null)
                continue;

            VarArgSupport va = new VarArgSupport(this, debug);
            for (Map.Entry<String, Map<Integer, List<AbstractMethodInvocation>>> srcNameEntry : srcSigs.entrySet()) {
                String srcName = srcNameEntry.getKey();
                Map<Integer, List<AbstractMethodInvocation>> srcArityMap = srcNameEntry.getValue();
                for (Map.Entry<Integer, List<AbstractMethodInvocation>> srcArityEntry : srcArityMap.entrySet()) {
                    List<AbstractMethodInvocation> srcInvos = srcArityEntry.getValue();
                    Map<Integer, List<AbstractMethodInvocation>> irArityMap = irSigs.get(srcName);
                    if (irArityMap == null) {
                        if (debug)
                            for (AbstractMethodInvocation ami : srcInvos)
                                System.out.println("WARNING: method name not found in IR: " + ami);
                        continue;
                    }
                    Integer arity = srcArityEntry.getKey();
                    List<AbstractMethodInvocation> irInvos = irArityMap.get(arity);
                    if (irInvos == null) {
                        if (debug)
                            System.out.println("Could not find " + srcName + "/" + arity + " in IR, postponing vararg resolution.");
                        va.recordInvocations(srcName, arity, srcArityMap, irArityMap);
                        continue;
                    }
                    matchInvocationLists(invocationMap, srcInvos, irInvos, srcName, arity);
                }
            }
            va.resolve(invocationMap);

            // Last step: generate metadata for unmatched IR elements that have
            // source line information.
            generateUnknownMetadata(invocationMap, srcMethod, irMethod);
        }
    }

    /**
     * Compute signatures per method name/arity pair.
     * @param method    a source/IR method
     * @return          a map (method name to arity to invocations)
     */
    private Map<String, Map<Integer, List<AbstractMethodInvocation>>>
    computeAbstractSignatures(AbstractMethod method) {
        Map<String, Map<Integer, List<AbstractMethodInvocation>>> sigs = new HashMap<>();
        if (method == null)
            return null;
        for (AbstractMethodInvocation invo : method.getInvocations()) {
            String invoMethodName = invo.getMethodName();
            sigs.computeIfAbsent(invoMethodName, (k -> new HashMap<>()))
                    .computeIfAbsent(invo.getArity(), (k -> new ArrayList<>()))
                    .add(invo);
        }
        return sigs;
    }

    /**
     * Match two invocation lists, pairwise.
     * @param invocationMap   the map to update
     * @param srcInvos        the first list (assumed to come from sources)
     * @param irInvos         the second list (assumed to come from the IR)
     * @param srcName         the method name (used for error reporting)
     * @param arity           the arity (used for error reporting)
     */
    public void matchInvocationLists(Map<String, Collection<JMethodInvocation>> invocationMap,
                                     List<AbstractMethodInvocation> srcInvos,
                                     List<AbstractMethodInvocation> irInvos,
                                     String srcName, Integer arity) {
        // If both name/arity sets have same size, match them one-by-one.
        int srcCount = srcInvos.size();
        int irCount = irInvos.size();
        if (srcCount == irCount)
            for (int i = 0; i < srcCount; i++) {
                IRMethodInvocation irInvo = (IRMethodInvocation) irInvos.get(i);
                JMethodInvocation srcInvo = (JMethodInvocation) srcInvos.get(i);
                recordMatch(invocationMap, "invocation", irInvo, srcInvo);
            }
        else if (debug)
            System.out.println("WARNING: name/arity invocation combination (" +
                    srcName + "," + arity + ") matches " + srcCount +
                    " source elements but " + irCount + " IR elements exist.");
    }

    /**
     * Generate metadata for the unmatched IR invocations that still have
     * source line information. This can help with mapping compiler-generated
     * invocations (such as StringBuilder calls for string concatenation).
     * @param invocationMap    the invocation map to update
     * @param srcMethod        a source method
     * @param irMethod         the IR method that corresponds to the source method
     */
    private void generateUnknownMetadata(Map<String, Collection<JMethodInvocation>> invocationMap, JMethod srcMethod, IRMethod irMethod) {
        for (IRMethodInvocation irInvo : irMethod.invocations)
            if (!irInvo.matched) {
                Integer line = irInvo.getSourceLine();
                if (line != null) {
                    Position pos = new Position(line, line, 0, 0);
                    boolean inIIB = "<init>".equals(irMethod.name) || JInit.isInitName(irMethod.name);
                    JMethodInvocation fakeSrcInvo = new JMethodInvocation(sourceFile, pos, irInvo.methodName, irInvo.arity, srcMethod, inIIB);
                    srcMethod.invocations.add(fakeSrcInvo);
                    recordMatch(invocationMap, "invocation", irInvo, fakeSrcInvo);
                    fakeSrcInvo.symbol.setSource(false);
                }
            }
    }

    private void matchMethodsWithSameNameArity(Map<String, Collection<JMethod>> methodMap,
                                               Set<JMethod> srcMatches,
                                               Set<IRMethod> irMatches) {
        // Match methods with same name and arity (in the presence of overloading).
        for (JMethod srcMethod : srcMatches) {
            IRMethod irMethodMatch = null;
            JMethod srcMethodMatch = null;
            try {
                for (IRMethod irMethod : irMatches) {
                    if (irMethod.arity == srcMethod.arity) {
                        if (irMethodMatch == null) {
                            irMethodMatch = irMethod;
                            srcMethodMatch = srcMethod;
                        } else {
                            // If adding a second method, switch to slower signature comparison.
                            if (debug)
                                System.out.println("Too many name/arity matches for " + srcMethod + ", switching to pairwise signature checks.");
                            irMethodMatch = null;
                            throw new BacktrackException();
                        }
                    }
                }
            } catch (BacktrackException ignored) {
                // Compare signatures by comparing each parameter type,
                List<IRMethod> matches = new ArrayList<>();
                for (IRMethod irMethod : irMatches) {
                    if (irMethod.arity == srcMethod.arity) {
                        boolean equal = true;
                        for (int i = 0; i < irMethod.arity; i++) {
                            String irParamType = Utils.getSimpleType(irMethod.paramTypes.get(i));
                            String srcParamType = Utils.getSimpleSourceType(srcMethod.parameters.get(i).type);
                            if (!irParamType.equals(srcParamType)) {
                                equal = false;
                                break;
                            }
                        }
                        if (equal) {
                            if (debug)
                                System.out.println("Type-match candidate: " + irMethod);
                            matches.add(irMethod);
                        }
                    }
                }
                if (matches.size() == 1)
                    irMethodMatch = matches.get(0);
            }
            if (irMethodMatch != null)
                recordMatch(methodMap, "method", irMethodMatch, srcMethodMatch);
        }
    }

    private void matchMethodSignaturesFuzzily(Map<String, Collection<JMethod>> methodMap,
                                              List<JMethod> srcMethods,
                                              List<IRMethod> irMethods) {
        if (debug)
            System.out.println("* Performing fuzzy type matching in method signatures...");
        for (JMethod srcMethod : srcMethods) {
            if (srcMethod.matchId != null)
                continue;
            Collection<String> ids = srcMethod.getIds();
            for (IRMethod irMethod : irMethods) {
                if (irMethod.matched)
                    continue;
                String irElemId = irMethod.getId();
                if (ids.contains(irElemId)) {
                    recordMatch(methodMap, "method", irMethod, srcMethod);
                    break;
                }
            }
        }
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
    private <IR_ELEM_T extends IRElement, SRC_ELEM_T extends NamedElementWithPosition<IR_ELEM_T, ?>>
    void recordMatch(Map<String, Collection<SRC_ELEM_T>> mapping, String kind, IR_ELEM_T irElem,
                     SRC_ELEM_T srcElem) {
        String id = irElem.getId();
        if (debug) {
            String pos = srcElem.pos == null ? "unknown" : srcElem.pos.toString();
            System.out.println("Match [" + kind + "] " + id + " -> " + sourceFile + ":" + pos);
        }
        Collection<SRC_ELEM_T> sourceElements = mapping.get(id);
        if (sourceElements == null)
            sourceElements = new LinkedList<>();
        sourceElements.add(srcElem);
        if (srcElem.matchId == null) {
            srcElem.matchId = id;
            srcElem.matchElement = irElem;
            irElem.matched = true;
        } else
            System.err.println("WARNING: multiple matches: " + id + " vs. " + srcElem.matchId);
        mapping.put(id, sourceElements);
        srcElem.initSymbolFromIRElement(irElem);
    }

    private <T> Map<String, Set<T>> getOverloadingTable(Collection<T> methods,
                                         Function<T, String> namer) {
        Map<String, Set<T>> srcOverloading = new HashMap<>();
        for (T method : methods) {
            String mName = namer.apply(method);
            srcOverloading.computeIfAbsent(mName, (k -> new HashSet<>())).add(method);
        }
        return srcOverloading;
    }
}

/** Helper exception to control match failures. */
class BacktrackException extends Exception { }
