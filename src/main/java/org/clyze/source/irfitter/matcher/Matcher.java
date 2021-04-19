package org.clyze.source.irfitter.matcher;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.clyze.persistent.metadata.jvm.JvmMetadata;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolAlias;
import org.clyze.persistent.model.jvm.JvmVariable;
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
     * @param sourceFile   the source file where matching will happen
     * @param debug        debugging mode
     * @param lossy        if true, use unsafe heuristics
     */
    public Matcher(SourceFile sourceFile, boolean debug, boolean lossy) {
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
                    generateUnknownTypeMetadata(idMapper, irType, jt);
                    break;
                }
        }
        // When all types have been resolved, mark declaring symbol ids.
        for (JType jt : sourceFile.jTypes)
            jt.updateDeclaringSymbolId();
    }

    /**
     * Generate metadata for type members that could not be matched with the
     * sources. Positions are assumed to come from the source type argument.
     * @param idMapper    the mapper object to update
     * @param irType      the IR type
     * @param jt          the source type
     */
    private void generateUnknownTypeMetadata(IdMapper idMapper, IRType irType, JType jt) {
        for (IRField irField : irType.fields)
            if (!irField.matched) {
                JField fakeField = new JField(sourceFile, irField.type, irField.name, new HashSet<>(), jt.pos, jt);
                recordMatch(idMapper.fieldMap, "field", irField, fakeField);
                fakeField.symbol.setSource(false);
            }
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

        // After methods have been matched, match elements inside methods.
        if (debug)
            System.out.println("* Matching method invocations by name/arity...");
        for (JMethod srcMethod : srcMethods) {
            matchInvocations(idMapper.invocationMap, idMapper.srcInvoMap, srcMethod);
            if (srcMethod.matchId != null) {
                matchParameters(idMapper.variableMap, srcMethod);
                matchAllocations(idMapper.allocationMap, srcMethod);
                matchFieldAccesses(idMapper.fieldAccessMap, srcMethod);
                matchMethodReferences(idMapper.methodRefMap, srcMethod);
                matchVariables(idMapper.variableMap, srcMethod);
            }
        }

        generateUnknownMethodAllocations(idMapper.allocationMap, srcMethods);
    }

    private void matchVariables(Map<String, Collection<JVariable>> variableMap, JMethod srcMethod) {
        for (JMethodInvocation srcInvo : srcMethod.invocations) {
            JVariable base = srcInvo.getBase();
            if (base != null) {
                if (base.symbol == null)
                    base.initSyntheticIRVariable(srcMethod.matchId);
                variableMap.computeIfAbsent(base.symbol.getSymbolId(), (k -> new ArrayList<>())).add(base);
            }
        }
    }

    private void matchParameters(Map<String, Collection<JVariable>> variableMap, JMethod srcMethod) {
        IRMethod irMethod = srcMethod.matchElement;
        JVariable srcReceiver = srcMethod.receiver;
        IRVariable irReceiver = irMethod.receiver;
        if (srcReceiver != null && irReceiver != null)
            recordMatch(variableMap, "receiver", irReceiver, srcReceiver);
        List<JVariable> srcParameters = srcMethod.parameters;
        List<IRVariable> irParameters = irMethod.parameters;
        int irParamSize = irParameters.size();
        int srcParamSize = srcParameters.size();
        if (irParamSize == srcParamSize) {
            for (int i = 0; i < irParamSize; i++)
                recordMatch(variableMap, "parameter", irParameters.get(i), srcParameters.get(i));
        } else
            System.out.println("WARNING: different number of parameters, source: " +
                    srcParamSize + " vs. IR: " + irParamSize + " for method: " +
                    srcMethod.matchId);
    }

    private void matchMethodReferences(Map<String, Collection<JMethodRef>> mapper,
                                       JMethod srcMethod) {
        List<IRMethodRef> methodRefs = srcMethod.matchElement.methodRefs;
        if (methodRefs == null)
            return;
        Map<String, List<JMethodRef>> srcRefsByName = groupElementsBy(srcMethod.getMethodRefs(), (ref -> ref.methodName));
        Map<String, List<IRMethodRef>> irRefsByName = groupElementsBy(srcMethod.matchElement.methodRefs, (ref -> ref.name));
        for (Map.Entry<String, List<JMethodRef>> srcEntry : srcRefsByName.entrySet()) {
            String mName = srcEntry.getKey();
            List<JMethodRef> srcRefs = srcEntry.getValue();
            List<IRMethodRef> irRefs = irRefsByName.get(mName);
            if (irRefs == null) {
                System.out.println("WARNING: source reference " + mName + " not found in the IR.");
                continue;
            }
            int srcSize = srcRefs.size();
            int irSize = irRefs.size();
            if (srcSize == irSize) {
                for (int i = 0; i < srcSize; i++) {
                    recordMatch(mapper, "method-reference", irRefs.get(i), srcRefs.get(i));
                }
            } else {
                System.out.println("WARNING: method reference '" + mName +
                        "' matches " + srcSize + " source elements but " +
                        irSize + " IR elements.");
            }
        }

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
        List<IRMethod> irInits = irMethods.stream().filter(m -> m.name.equals("<init>")).collect(Collectors.toList());
        List<String> outerSimpleTypes = outerTypes.stream().map(Utils::getSimpleIrType).collect(Collectors.toList());
        int outerTypesCount = outerTypes.size();
        for (JMethod srcInit : srcMethods) {
            if (srcInit.matchId != null || !srcInit.getLowLevelName().equals("<init>"))
                continue;
            for (IRMethod irInit : irInits) {
                List<String> irParamTypes = irInit.paramTypes.stream().map(Utils::getSimpleIrType).collect(Collectors.toList());
                // Abort if |outer-types + source-types| != |ir-types|.
                if (irInit.paramTypes.size() != outerTypesCount + srcInit.parameters.size())
                    continue;
                List<String> srcParamTypes = new ArrayList<>(outerSimpleTypes);
                for (JVariable parameter : srcInit.parameters)
                    srcParamTypes.add(Utils.getSimpleSourceType(parameter.type));
                if (debug)
                    System.out.println("Checking source/IR constructors: " +
                            srcInit + " vs. " + irInit + ": " +
                            srcParamTypes + " vs. " + irParamTypes);
                if (irParamTypes.equals(srcParamTypes))
                    recordMatch(methodMap, "method", irInit, srcInit);
            }
        }
    }

    /**
     * Match source/IR field accesses in source methods. The corresponding IR
     * methods are implicit (assumed to have already been resolved, pointed to
     * by source methods).
     * @param fieldAccessMap   the map object to update
     * @param srcMethod        the source method to process
     */
    private void matchFieldAccesses(Map<String, Collection<JFieldAccess>> fieldAccessMap, JMethod srcMethod) {
        IRMethod irMethod = srcMethod.matchElement;
        // Group accesses by field name.
        Map<String, List<JFieldAccess>> srcAccessesByName = groupElementsBy(srcMethod.fieldAccesses, (a -> a.fieldName));
        if (srcAccessesByName.size() == 0)
            return;
        Map<String, List<IRFieldAccess>> irAccessesByName = groupElementsBy(irMethod.fieldAccesses, (a -> a.fieldName));
        if (irAccessesByName.size() == 0)
            return;
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

    /**
     * Match allocations in source methods. The corresponding IR methods are
     * implicit (assumed to have already been resolved, pointed to by source methods).
     * @param allocationMap    the allocation map to update
     * @param srcMethod        the source method to process
     */
    private void matchAllocations(Map<String, Collection<JAllocation>> allocationMap,
                                  JMethod srcMethod) {
        // Group source allocations by type.
        Map<String, List<JAllocation>> srcAllocationsByType = groupElementsBy(srcMethod.allocations, AbstractAllocation::getSimpleType);
        // Group IR allocations by type.
        IRMethod irMethod = srcMethod.matchElement;
        Map<String, List<IRAllocation>> irAllocationsByType = groupElementsBy(irMethod.allocations, AbstractAllocation::getSimpleType);
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

    private void generateUnknownMethodAllocations(Map<String, Collection<JAllocation>> allocationMap,
                                                  List<JMethod> srcMethods) {
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

    /**
     * Helper method to group elements by a key. Used to partition lists into
     * groups of lists, so that failures are localized.
     * @param elems          the elements to process
     * @param keyExtractor   a function that computes the group key from an element
     * @param <T>            the type of an element
     * @return               the resulting map of groups
     */
    private <T>
    Map<String, List<T>> groupElementsBy(Collection<T> elems, Function<T, String> keyExtractor) {
        Map<String, List<T>> map = new HashMap<>();
        if (elems != null)
            for (T elem : elems)
                map.computeIfAbsent(keyExtractor.apply(elem), (k -> new LinkedList<>())).add(elem);
        return map;
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
     * @param invocationMap  the source-to-IR mapper object
     * @param srcMethod      the source method to process
     */
    private void matchInvocations(Map<String, Collection<JMethodInvocation>> invocationMap,
                                  Map<String, JMethodInvocation> srcInvoMap,
                                  JMethod srcMethod) {
        IRMethod irMethod = srcMethod.matchElement;
        Map<String, Map<Integer, List<AbstractMethodInvocation>>> irSigs = computeAbstractSignatures(irMethod);
        if (irSigs == null)
            return;
        Map<String, Map<Integer, List<AbstractMethodInvocation>>> srcSigs = computeAbstractSignatures(srcMethod);
        if (srcSigs == null)
            return;

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
                matchInvocationLists(invocationMap, srcInvoMap, srcInvos, irInvos, srcName, arity);
            }
        }
        va.resolve(invocationMap, srcInvoMap);

        // Last step: generate metadata for unmatched IR elements that have
        // source line information.
        generateUnknownMethodMetadata(invocationMap, srcInvoMap, srcMethod, irMethod);
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
     * @param srcInvos        the first list (assumed to come from sources)
     * @param irInvos         the second list (assumed to come from the IR)
     * @param srcName         the method name (used for error reporting)
     * @param arity           the arity (used for error reporting)
     */
    public void matchInvocationLists(Map<String, Collection<JMethodInvocation>> invocationMap,
                                     Map<String, JMethodInvocation> srcInvoMap,
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
                recordInvoMatch(irInvo, srcInvo, invocationMap, srcInvoMap);
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
    private void generateUnknownMethodMetadata(Map<String, Collection<JMethodInvocation>> invocationMap,
                                               Map<String, JMethodInvocation> srcInvoMap,
                                               JMethod srcMethod, IRMethod irMethod) {
        for (IRMethodInvocation irInvo : irMethod.invocations)
            if (!irInvo.matched) {
                Integer line = irInvo.getSourceLine();
                if (line != null) {
                    Position pos = new Position(line, line, 0, 0);
                    String mName = irMethod.name;
                    boolean inIIB = "<init>".equals(mName) || JInit.isInitName(mName);
                    JBlock block = new JBlock(mName, null);
                    JMethodInvocation fakeSrcInvo = new JMethodInvocation(sourceFile, pos, irInvo.methodName, irInvo.arity, srcMethod, inIIB, block, null);
                    srcMethod.invocations.add(fakeSrcInvo);
                    recordInvoMatch(irInvo, fakeSrcInvo, invocationMap, srcInvoMap);
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
                            String srcParamType = srcMethod.parameters.get(i).type;
                            String irParamType = irMethod.paramTypes.get(i);
                            if (!Utils.simpleTypesAreEqual(srcParamType, irParamType)) {
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

    public void recordInvoMatch(IRMethodInvocation irInvo, JMethodInvocation srcInvo,
                                Map<String, Collection<JMethodInvocation>> invocationMap,
                                Map<String, JMethodInvocation> srcInvoMap) {
        srcInvoMap.put(irInvo.getId(), srcInvo);
        recordMatch(invocationMap, "invocation", irInvo, srcInvo);
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
    public <IR_ELEM_T extends IRElement, SRC_ELEM_T extends NamedElementWithPosition<IR_ELEM_T, ?>>
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

    public static void resolveDoopVariables(File db, IdMapper idMapper, boolean debug) {
        System.out.println("Resolving variables from facts in " + db);
        // Keep a map of encountered IR variables to avoid recomputation.
        Map<String, JvmVariable> jvmVars = new HashMap<>();
        processInstanceInvocations(db, idMapper, debug, jvmVars, "SpecialMethodInvocation.facts", 5, 0, 3, 4);
        processInstanceInvocations(db, idMapper, debug, jvmVars, "SuperMethodInvocation.facts", 5, 0, 3, 4);
        processInstanceInvocations(db, idMapper, debug, jvmVars, "VirtualMethodInvocation.facts", 5, 0, 3, 4);
    }

    /**
     * Process a facts file representing an instance method invocation, to
     * match "base" variables.
     * @param db              the Doop database
     * @param idMapper        the mapper object to update
     * @param debug           debugging mode
     * @param jvmVars         the map of already encountered JVM variables
     * @param factsFileName   the file name of the facts file
     * @param columns         the number of relation columns
     * @param invoIdx         the index of the invocation-id column
     * @param baseIdx         the index of the base-variable-id column
     * @param methIdx         the index of the declaring-method-id column
     */
    @SuppressWarnings("SameParameterValue")
    private static void processInstanceInvocations(File db, IdMapper idMapper, boolean debug,
                                                   Map<String, JvmVariable> jvmVars,
                                                   String factsFileName, int columns,
                                                   int invoIdx, int baseIdx, int methIdx) {
        File factsFile = new File(db, factsFileName);
        if (factsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(factsFile))))  {
                br.lines().forEach(line -> {
                    String[] parts = line.split("\t");
                    if (parts.length < columns) {
                        System.out.println("Ignoring line: " + line);
                        return;
                    }
                    String irInvoId = parts[invoIdx];
                    String baseId = parts[baseIdx];
                    String declaringMethoId = parts[methIdx];
                    JMethodInvocation srcInvo = idMapper.srcInvoMap.get(irInvoId);
                    if (srcInvo != null) {
                        if (debug)
                            System.out.println("FACTS: IR invocation: " + irInvoId + " -> " + srcInvo);
                        JVariable base = srcInvo.getBase();
                        if (base != null) {
                            if (debug)
                                System.out.println("FACTS: Variable alias: " + base + " -> " + baseId);
                            JvmMetadata jvmMetadata = srcInvo.srcFile.getJvmMetadata();
                            if (base.symbol == null)
                                jvmMetadata.jvmVariables.add(base.getSymbol());
                            String sourceFileName = srcInvo.srcFile.getRelativePath();
                            if (jvmVars.get(baseId) == null) {
                                String name = baseId.substring(baseId.indexOf(">/") + 2);
                                boolean inIIB = false;
                                jvmMetadata.jvmVariables.add(new JvmVariable(null, sourceFileName, false, name, baseId, base.type, declaringMethoId, true, false, inIIB));
                            }
                            SymbolAlias alias = new SymbolAlias(sourceFileName, baseId, base.symbol.getSymbolId());
                            jvmMetadata.aliases.add(alias);
                            if (debug)
                                System.out.println("FACTS: alias = " + alias);
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            System.err.println("ERROR: could not read " + factsFile);
    }
}

/** Helper exception to control match failures. */
class BacktrackException extends Exception { }
