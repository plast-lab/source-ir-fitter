package org.clyze.source.irfitter.matcher;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.base.AccessType;
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
    /** If true, keep only results that match both source and IR elements. */
    private final boolean matchIR;

    /** The symbol-id aliasing handler. */
    private final Aliaser aliaser;

    /**
     * @param sourceFile   the source file where matching will happen
     * @param debug        debugging mode
     * @param lossy        if true, use unsafe heuristics
     * @param matchIR      if true, keep only results that match both source and IR elements
     * @param aliaser      the symbol aliasing helper
     */
    public Matcher(SourceFile sourceFile, boolean debug, boolean lossy,
                   boolean matchIR, Aliaser aliaser) {
        this.sourceFile = sourceFile;
        this.lossy = lossy;
        this.debug = debug;
        this.matchIR = matchIR;
        this.aliaser = aliaser;
    }

    /**
     * Main entry point, matches source types and IR types. Call this method
     * before matching elements inside types (such as methods or invocations).
     * @param idMapper   the source-to-IR mapper object
     * @param irTypes    the IR type representations
     */
    public void matchTypes(IdMapper idMapper, Iterable<IRType> irTypes) {
        for (JType jt : sourceFile.jTypes) {
            String id = jt.getFullyQualifiedName();
            if (debug)
                System.out.println("Matching source type: " + jt + ", fully qualified name: " + id);
            boolean typeMatched = false;
            for (IRType irType : irTypes)
                if (!irType.matched && irType.getId().equals(id)) {
                    recordMatch(idMapper.typeMap, "type", irType, jt);
                    typeMatched = true;
                    matchFields(idMapper.fieldMap, irType.fields, jt.fields);
                    matchMethods(idMapper, irType.methods, jt.methods, irType.outerTypes);
                    generateUnknownTypeMetadata(idMapper, irType, jt);
                    break;
                }
            if (!typeMatched && !matchIR)
                idMapper.typeMap.put(id, Collections.singletonList(jt));
        }
        // When all types have been resolved, mark declaring symbol ids.
        for (JType jt : sourceFile.jTypes)
            jt.updateDeclaringSymbolId();
    }

    private void matchLambdas(IdMapper idMapper, JMethod srcMethod) {
        List<JLambda> lambdas = srcMethod.lambdas;
        if (lambdas == null)
            return;
        IRMethod irMethod = srcMethod.matchElement;
        if (irMethod == null)
            return;
        int srcLambdasSize = lambdas.size();
        List<IRLambda> irLambdas = irMethod.lambdas;
        if (irLambdas == null)
            return;
        int irLambdasSize = irLambdas.size();
        if (srcLambdasSize > irLambdasSize)
            System.err.println("WARNING: SRC/IR lambda arities differ for method " + srcMethod + ": " + srcLambdasSize + " vs. " + irLambdasSize);
        else if (srcLambdasSize < irLambdasSize)
            System.err.println("WARNING: SRC/IR lambda arities differ for method " + srcMethod + ": " + srcLambdasSize + " vs. " + irLambdasSize + ", handling it as an environment-capturing lambda...");
        int captureShift = irLambdasSize - srcLambdasSize;
        for (int i = 0; i < srcLambdasSize; i++) {
            JLambda jLambda = lambdas.get(i);
            // We assume that capture parameters are first, then original lambda parameters.
            IRLambda irLambda = irLambdas.get(i + captureShift);
            IRMethod implMethod = irLambda.implMethod;
            if (implMethod != null) {
                recordMatch(idMapper.methodMap, "lambda", implMethod, jLambda);
                matchInsideMethod(idMapper, jLambda, true);
            }
        }
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
                              List<JMethod> srcMethods, Collection<String> outerTypes) {
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
            matchInvocations(idMapper.invocationMap, srcMethod);
            if (srcMethod.matchId != null)
                matchInsideMethod(idMapper, srcMethod, false);
        }

        if (debug)
            System.out.println("* Matching lambdas...");
        for (JMethod srcMethod : srcMethods)
            matchLambdas(idMapper, srcMethod);

        generateUnknownMethodAllocations(idMapper.allocationMap, srcMethods);
    }

    private void matchInsideMethod(IdMapper idMapper, JMethod srcMethod, boolean isLambda) {
        matchParameters(idMapper.variableMap, srcMethod, isLambda);
        matchAllocations(idMapper.allocationMap, srcMethod);
        matchFieldAccesses(idMapper.fieldAccessMap, srcMethod);
        matchMethodReferences(idMapper.methodRefMap, srcMethod);
        matchVariables(idMapper.variableMap, srcMethod);
        matchCasts(srcMethod);
    }

    private void matchCasts(JMethod srcMethod) {
        if (debug)
            System.out.println("Matching casts in " + srcMethod);
        IRMethod irMethod = srcMethod.matchElement;
        List<JCast> srcCasts = srcMethod.casts;
        List<IRCast> irCasts = irMethod.casts;
        if (srcCasts == null || irCasts == null) {
            if (debug)
                System.out.println("No casts found in method body.");
            return;
        }
        int srcSize = srcCasts.size();
        int irSize = irCasts.size();
        if (srcSize == irSize) {
            if (debug)
                System.out.println("Cast groups have size " + srcSize);
            for (int i = 0; i < srcSize; i++) {
                IRCast irCast = irCasts.get(i);
                matchElements("cast", irCast, srcCasts.get(i), irCast.getId());
            }
        } else if (debug)
            System.out.println("WARNING: Casts not matched: srcSize=" + srcSize + " but irSize=" + irSize);
    }

    private static <T> void printGroups(Map<String, List<T>> group) {
        for (Map.Entry<String, List<T>> entry : group.entrySet()) {
            System.out.println("== Key: " + entry.getKey() + " ==");
            entry.getValue().forEach(v -> System.out.println("  " + v));
        }
    }

    /**
     * Process the local variables of the source method.
     * @param variableMap   the variable map to update
     * @param srcMethod     the source method being processed
     */
    private void matchVariables(Map<String, Collection<JVariable>> variableMap, JMethod srcMethod) {
        for (JBlock block : srcMethod.blocks) {
            List<JVariable> variables = block.getVariables();
            if (variables != null)
                for (JVariable variable : variables)
                    registerSourceVariable(variableMap, srcMethod, variable);
        }
    }

    private void registerSourceVariable(Map<String, Collection<JVariable>> variableMap,
                                        JMethod srcMethod, JVariable variable) {
        if (variable == null)
            return;
        if (variable.symbol == null) {
            variable.initSyntheticIRVariable(srcMethod.matchId);
            if (debug)
                System.out.println("Initialized source variable: " + variable + " -> " + variable.symbol);
        }
        variableMap.computeIfAbsent(variable.symbol.getSymbolId(), (k -> new ArrayList<>())).add(variable);
    }

    private void matchParameters(Map<String, Collection<JVariable>> variableMap,
                                 JMethod srcMethod, boolean isLambda) {
        IRMethod irMethod = srcMethod.matchElement;
        JVariable srcReceiver = srcMethod.receiver;
        IRVariable irReceiver = irMethod.receiver;
        if (srcReceiver != null && irReceiver != null) {
            registerSourceVariable(variableMap, srcMethod, srcReceiver);
            aliaser.addIrAlias("THIS", srcReceiver, irReceiver.getId());
        }
        List<JVariable> srcParameters = srcMethod.parameters;
        List<IRVariable> irParameters = irMethod.parameters;
        int irParamSize = irParameters.size();
        int srcParamSize = srcParameters.size();
        if (irParamSize != srcParamSize) {
            String msg = "different number of parameters, source: " + srcParamSize +
                    " vs. IR: " + irParamSize + " for method: " + srcMethod.matchId;
            if (irParamSize < srcParamSize || !isLambda) {
                System.out.println("ERROR: " + msg);
                return;
            } else
                System.out.println("WARNING: " + msg + ". Assuming this is a capturing lambda.");
            int captureShift = irParamSize - srcParamSize;
            for (int i = 0; i < srcParamSize; i++) {
                JVariable srcVar = srcParameters.get(i);
                registerSourceVariable(variableMap, srcMethod, srcVar);
                aliaser.addIrAlias("PARAMETER", srcVar, irParameters.get(captureShift + i).getId());
            }
        }
    }

    private void matchMethodReferences(Map<String, Collection<JMethodRef>> mapper,
                                       JMethod srcMethod) {
        List<IRMethodRef> methodRefs = srcMethod.matchElement.methodRefs;
        if (methodRefs == null)
            return;
        Map<String, List<JMethodRef>> srcRefsByName = groupElementsBy(srcMethod.methodRefs, (ref -> ref.methodName));
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
                                        Iterable<JMethod> srcMethods, Collection<IRMethod> irMethods,
                                        Collection<String> outerTypes) {
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
                Collection<String> srcParamTypes = new ArrayList<>(outerSimpleTypes);
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
        Map<String, List<JFieldAccess>> srcAccessesByName = groupElementsBy(srcMethod.fieldAccesses, (a -> a.fieldName + '/' + a.accessType));
        if (srcAccessesByName.size() == 0)
            return;
        Map<String, List<IRFieldAccess>> irAccessesByName = groupElementsBy(irMethod.fieldAccesses, (a -> a.fieldName + '/' + a.accessType));
        if (irAccessesByName.size() == 0)
            return;
        if (debug) {
            System.out.println("Field accesses by name (IR/SRC): " + irAccessesByName.size() + "/" + srcAccessesByName.size() + " in " + srcMethod);
            irAccessesByName.forEach((k, v) -> System.out.println("* IR: " + k + " -> " + v));
            srcAccessesByName.forEach((k, v) -> System.out.println("* SRC: " + k + " -> " + v));
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
                    AccessType irRead = irAccess.accessType;
                    AccessType srcRead = srcAccess.accessType;
                    if (irRead == srcRead)
                        recordMatch(fieldAccessMap, "field-access", irAccess, srcAccess);
                    else {
                        System.out.println("WARNING: incompatible field accesses found, aborting matching for field '" + fieldName + "' (IR/SRC 'read'): " + irRead + "/" + srcRead);
                        break;
                    }
                }
            } else if (debug)
                System.out.println("Field accesses to '" + fieldName + "' ignored: (IR=" + irSize+ "/SRC=" + srcSize + ") in " + srcMethod);
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
        Map<String, List<JAllocation>> srcAllocationsByType = groupElementsBy(srcMethod.allocations, AbstractAllocation::getBareIrType);
        // Group IR allocations by type.
        IRMethod irMethod = srcMethod.matchElement;
        Map<String, List<IRAllocation>> irAllocationsByType = groupElementsBy(irMethod.allocations, AbstractAllocation::getBareIrType);
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
                                                  Iterable<JMethod> srcMethods) {
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
                        JAllocation approxSrcAlloc = srcMethod.addAllocation(sourceFile, pos, irAlloc.getBareIrType());
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
    Map<String, List<T>> groupElementsBy(Iterable<T> elems, Function<T, String> keyExtractor) {
        Map<String, List<T>> map = new HashMap<>();
        if (elems != null)
            for (T elem : elems)
                map.computeIfAbsent(keyExtractor.apply(elem), (k -> new ArrayList<>())).add(elem);
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
                                               Iterable<JAllocation> srcAllocs, Iterable<IRAllocation> irAllocs) {
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
                matchInvocationLists(invocationMap, srcInvos, irInvos, srcName, arity);
            }
        }
        va.resolve(invocationMap);

        // Last step: generate metadata for unmatched IR elements that have
        // source line information.
        generateUnknownMethodMetadata(invocationMap, srcMethod, irMethod);
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
     * @param invocationMap   the id-to-invocations map
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
    private void generateUnknownMethodMetadata(Map<String, Collection<JMethodInvocation>> invocationMap,
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
                    recordMatch(invocationMap, "invocation", irInvo, fakeSrcInvo);
                    fakeSrcInvo.symbol.setSource(false);
                }
            }
    }

    private void matchMethodsWithSameNameArity(Map<String, Collection<JMethod>> methodMap,
                                               Iterable<JMethod> srcMatches,
                                               Iterable<IRMethod> irMatches) {
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
                                              Iterable<JMethod> srcMethods,
                                              Iterable<IRMethod> irMethods) {
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
    private <IR_ELEM_T extends IRElement, SRC_ELEM_T extends ElementWithPosition<IR_ELEM_T, ?>>
    void recordMatch(Map<String, Collection<SRC_ELEM_T>> mapping, String kind, IR_ELEM_T irElem,
                     SRC_ELEM_T srcElem) {
        String id = irElem.getId();
        mapping.computeIfAbsent(id, (k -> new ArrayList<>())).add(srcElem);
        matchElements(kind, irElem, srcElem, id);
    }

    private <IR_ELEM_T extends IRElement, SRC_ELEM_T extends ElementWithPosition<IR_ELEM_T, ?>>
    void matchElements(String kind, IR_ELEM_T irElem, SRC_ELEM_T srcElem, String id) {
        if (debug) {
            String pos = srcElem.pos == null ? "unknown" : srcElem.pos.toString();
            System.out.println("Match [" + kind + "] " + id + " -> " + sourceFile + ":" + pos);
        }
        if (srcElem.matchId == null) {
            srcElem.matchId = id;
            srcElem.matchElement = irElem;
            irElem.matched = true;
        } else
            System.err.println("WARNING: multiple matches: " + id + " vs. " + srcElem.matchId);
        srcElem.initSymbolFromIRElement(irElem);
    }

    private <T> Map<String, Set<T>> getOverloadingTable(Iterable<T> methods,
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
