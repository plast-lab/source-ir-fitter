package org.clyze.source.irfitter.source.model;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AbstractAllocation;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.*;
import org.clyze.persistent.metadata.FileInfo;
import org.clyze.persistent.model.BasicMetadata;

/**
 * This is the source code representation that is responsible for matching
 * source elements against IR elements.
 */
public class SourceFile {
    /** A parent directory that should be used to form relative paths. */
    public final File topDir;
    /** The parsed source file. */
    public final File file;
    /** The package name declared in the top of the source file. */
    public String packageName = "";
    /** The import declarations. */
    public final List<Import> imports = new LinkedList<>();
    /** List of visited types. */
    public final Set<JType> jTypes = new HashSet<>();
    /** List of string constants that may be inlined and should be preserved. */
    public final List<JStringConstant<?>> stringConstants = new LinkedList<>();
    /** Debugging flag, set by command-line option. */
    public final boolean debug;
    /**
     * If true, (partial) source types and (fully-qualified but erased) IR
     * types will be combined into a single more informative representation.
     */
    public final boolean synthesizeTypes;
    private FileInfo cachedFileInfo = null;
    private String cachedRelativePath = null;

    public SourceFile(File topDir, File file, boolean debug, boolean synthesizeTypes) {
        this.topDir = topDir;
        this.file = file;
        this.debug = debug;
        this.synthesizeTypes = synthesizeTypes;
    }

    /**
     * Main entry point, matches source types and IR types. Call this method
     * before matching elements inside types (such as methods or invocations).
     * @param idMapper   the source-to-IR mapper object
     * @param irTypes    the IR type representations
     */
    public void matchTypes(IdMapper idMapper, Collection<IRType> irTypes) {
        for (JType jt : jTypes) {
            if (debug)
                System.out.println("Matching source type: " + jt);
            String id = jt.getFullyQualifiedName(packageName);
            //System.out.println("Mapping " + jt.name + " -> " + id);
            for (IRType irType : irTypes) {
                if (!irType.matched && irType.getId().equals(id)) {
                    recordMatch(idMapper.typeMap, "type", irType, jt);
                    matchFields(idMapper.fieldMap, irType.fields, jt.fields);
                    matchMethods(idMapper, irType.methods, jt.methods);
                    break;
                }
            }
        }
        // When all types have been resolved, mark declaring symbol ids.
        for (JType jt : jTypes)
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

    private void matchMethods(IdMapper idMapper,
                              List<IRMethod> irMethods, List<JMethod> srcMethods) {
        if (debug)
            System.out.println("Matching " + irMethods.size() + " IR methods against " + srcMethods.size() + " methods...");

        Map<String, Collection<JMethod>> methodMap = idMapper.methodMap;

        // Match same-name methods without overloading.
        if (debug)
            System.out.println("* Matching same-name methods without overloading...");
        Map<String, NameMatch<JMethod>> srcOverloading = new HashMap<>();
        setOverloadingTable(srcMethods, srcOverloading, (JMethod::getLowLevelName));
        Map<String, NameMatch<IRMethod>> irOverloading = new HashMap<>();
        setOverloadingTable(irMethods, irOverloading, (m -> m.name));
        for (Map.Entry<String, NameMatch<JMethod>> srcEntry : srcOverloading.entrySet()) {
            NameMatch<JMethod> srcMatch = srcEntry.getValue();
            String srcMethodName = srcEntry.getKey();
            NameMatch<IRMethod> irMatch = irOverloading.get(srcMethodName);
            if (irMatch == null) {
                if (!"<clinit>".equals(srcMethodName))
                    System.out.println("WARNING: method " + srcMethodName + "() does not match any bytecode methods: " + srcMatch.methods);
                continue;
            }
            // Match methods with unique names both in sources and IR.
            if (srcMatch.appearances == 1 && irMatch.appearances == 1) {
                JMethod srcMethod = srcMatch.methods.get(0);
                IRMethod irMethod = irMatch.methods.get(0);
                recordMatch(methodMap, "method", irMethod, srcMethod);
            }
            else
                matchMethodsWithSameNameArity(methodMap, srcMatch, irMatch);
        }

        // Do fuzzy type matching on method signatures.
        matchMethodSignaturesFuzzily(methodMap, srcMethods, irMethods);

        // After methods have been matched, match method invocations and allocations.
        matchInvocations(idMapper, srcMethods);
        matchAllocations(idMapper.allocationMap, srcMethods);
    }

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
                        if (srcSize == irAllocs.size()) {
                            for (int i = 0; i < srcSize; i++) {
                                IRAllocation irAlloc = irAllocs.get(i);
                                JAllocation srcAlloc = srcAllocs.get(i);
                                recordMatch(allocationMap, "allocation", irAlloc, srcAlloc);
                            }
                        } else
                            System.out.println("WARNING: cannot match allocations of type " + simpleType);
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
                        JAllocation fakeSrcAlloc = new JAllocation(this, pos, irAlloc.getSimpleType());
                        srcMethod.allocations.add(fakeSrcAlloc);
                        recordMatch(allocationMap, "allocation", irAlloc, fakeSrcAlloc);
                    }
                }
            }
        }
    }

    private <T extends AbstractAllocation>
    void registerAllocationByType(Map<String, List<T>> map, T alloc) {
        String type = alloc.getSimpleType();
        List<T> allocs = map.get(type);
        if (allocs == null)
            allocs = new LinkedList<>();
        allocs.add(alloc);
        map.put(type, allocs);
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

            for (Map.Entry<String, Map<Integer, List<AbstractMethodInvocation>>> srcNameEntry : srcSigs.entrySet()) {
                String srcName = srcNameEntry.getKey();
                for (Map.Entry<Integer, List<AbstractMethodInvocation>> srcArityEntry : srcNameEntry.getValue().entrySet()) {
                    List<AbstractMethodInvocation> srcInvos = srcArityEntry.getValue();
                    Map<Integer, List<AbstractMethodInvocation>> irInvoMap = irSigs.get(srcName);
                    if (irInvoMap == null) {
                        if (debug)
                            for (AbstractMethodInvocation ami : srcInvos)
                                System.out.println("WARNING: method name not found in IR: " + ami);
                        continue;
                    }
                    Integer arity = srcArityEntry.getKey();
                    List<AbstractMethodInvocation> irInvos = irInvoMap.get(arity);
                    if (irInvos == null) {
                        if (debug)
                            for (AbstractMethodInvocation ami : srcInvos)
                                System.out.println("WARNING: method arity not found in IR: " + ami);
                        continue;
                    }
                    // If both name/arity sets have same size, match them one-by-one.
                    int srcCount = srcInvos.size();
                    int irCount = irInvos.size();
                    if (srcCount == irCount) {
                        for (int i = 0; i < srcCount; i++) {
                            IRMethodInvocation irInvo = (IRMethodInvocation) irInvos.get(i);
                            JMethodInvocation srcInvo = (JMethodInvocation) srcInvos.get(i);
                            recordMatch(invocationMap, "invocation", irInvo, srcInvo);
                        }
                    } else if (debug)
                        System.out.println("WARNING: name/arity invocation combination (" + srcName + "," + arity + ") matches " + srcCount + " source elements but " + irCount + " IR elements.");
                }
            }

            // Last step: for the unmatched IR invocations that still have
            // source line information, generate metadata. This can help with
            // mapping compiler-generated invocations (such as StringBuilder
            // calls for string concatenation).
            for (IRMethodInvocation irInvo : irMethod.invocations) {
                if (!irInvo.matched) {
                    Integer line = irInvo.getSourceLine();
                    if (line != null) {
                        Position pos = new Position(line, line, 0, 0);
                        boolean inIIB = "<init>".equals(irMethod.name) || "<clinit>".equals(irMethod.name);
                        JMethodInvocation fakeSrcInvo = new JMethodInvocation(this, pos, irInvo.methodName, irInvo.arity, srcMethod, inIIB);
                        srcMethod.invocations.add(fakeSrcInvo);
                        recordMatch(invocationMap, "invocation", irInvo, fakeSrcInvo);
                    }
                }
            }
        }
    }

    private void matchMethodsWithSameNameArity(Map<String, Collection<JMethod>> methodMap, NameMatch<JMethod> srcMatch, NameMatch<IRMethod> irMatch) {
        // Match methods with same name and arity (in the presence of overloading).
        for (JMethod srcMethod : srcMatch.methods) {
            try {
                IRMethod irMethodMatch = null;
                JMethod srcMethodMatch = null;
                for (IRMethod irMethod : irMatch.methods) {
                    if (irMethod.arity == srcMethod.arity) {
                        if (irMethodMatch == null) {
                            irMethodMatch = irMethod;
                            srcMethodMatch = srcMethod;
                        } else
                            throw new BacktrackException();
                    }
                }
                if (irMethodMatch != null)
                    recordMatch(methodMap, "method", irMethodMatch, srcMethodMatch);
            } catch (BacktrackException ignored) { }
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

    private <T> List<T> lookupNameArity(Map<String, Map<Integer, List<T>>> sigs,
                                        String methodName, Integer arity) {
        Map<Integer, List<T>> arEntry = sigs.get(methodName);
        if (arEntry == null)
            return null;
        return arEntry.get(arity);
    }

    // Compute signatures per method name/arity pair.
    private Map<String, Map<Integer, List<AbstractMethodInvocation>>>
    computeAbstractSignatures(AbstractMethod method) {
        Map<String, Map<Integer, List<AbstractMethodInvocation>>> sigs = new HashMap<>();
        if (method == null)
            return null;
        for (AbstractMethodInvocation invo : method.getInvocations()) {
            String invoMethodName = invo.getMethodName();
            Map<Integer, List<AbstractMethodInvocation>> sigs0 = sigs.get(invoMethodName);
            boolean add0 = false;
            if (sigs0 == null) {
                sigs0 = new HashMap<>();
                add0 = true;
            }
            int invoArity = invo.getArity();
            boolean add1 = false;
            List<AbstractMethodInvocation> sigs1 = sigs0.get(invoArity);
            if (sigs1 == null) {
                sigs1 = new ArrayList<>();
                add1 = true;
            }
            sigs1.add(invo);
            if (add1)
                sigs0.put(invoArity, sigs1);
            if (add0)
                sigs.put(invoMethodName, sigs0);
        }
        return sigs;
    }

    private <T> void setOverloadingTable(Collection<T> methods,
                                         Map<String, NameMatch<T>> srcOverloading,
                                         Function<T, String> namer) {
        for (T method : methods) {
            String mName = namer.apply(method);
            NameMatch<T> nameMatch = srcOverloading.get(mName);
            if (nameMatch == null)
                nameMatch = new NameMatch<>();
            nameMatch.appearances++;
            nameMatch.methods.add(method);
            srcOverloading.put(mName, nameMatch);
        }
    }

    private <IR_ELEM_T extends IRElement, SRC_ELEM_T extends NamedElementWithPosition<IR_ELEM_T>>
    void recordMatch(Map<String, Collection<SRC_ELEM_T>> mapping, String kind, IR_ELEM_T irElem,
                     SRC_ELEM_T srcElem) {
        String id = irElem.getId();
        if (debug) {
            String pos = srcElem.pos == null ? "unknown" : srcElem.pos.toString();
            System.out.println("Match [" + kind + "] " + id + " -> " + file.getPath() + ":" + pos);
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

    public int reportUmatched(boolean debug) {
        int unmatched = 0;
        for (JType jt : jTypes) {
            if (jt.matchId == null) {
                unmatched++;
                if (debug)
                    System.out.println("Unmatched type: " + jt.getFullyQualifiedName(packageName) + " (" + jt + ")");
            }
            for (JField jf : jt.fields)
                if (jf.matchId == null) {
                    unmatched++;
                    if (debug)
                        System.out.println("Unmatched field: " + jf.toString());
                }
            for (JMethod jm : jt.methods)
                // <clinit>() methods are optimistically created for sources and
                // may not really exist in IR, so ignore their match failures
                if (jm.matchId == null && !"<clinit>".equals(jm.name)) {
                    unmatched++;
                    if (debug) {
                        System.out.println("Unmatched method: " + jm.toString());
                        jm.getIds().forEach(System.out::println);
                    }
                }
        }
        return unmatched;
    }

    /**
     * Returns the object needed by the metadata library.
     * @return  the FileInfo representation of this source file
     */
    public FileInfo getFileInfo() {
        if (cachedFileInfo == null) {
            try {
                cachedFileInfo = new SourceFileInfo(packageName, file.getName(), file.getCanonicalPath(), "", new BasicMetadata());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cachedFileInfo;
    }

    /**
     * Returns the path of this source file, relative to the "top directory".
     * @return the relative path of the source file
     */
    public String getRelativePath() {
        if (cachedRelativePath == null) {
            try {
                String fullPath = file.getCanonicalPath();
                String topPath = topDir.getCanonicalPath();
                if (fullPath.startsWith(topPath))
                    cachedRelativePath = fullPath.substring(topPath.length() + File.separator.length());
                else {
                    System.out.println("WARNING: path " + fullPath + " not under " + topPath);
                    cachedRelativePath = fullPath;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return cachedRelativePath;
    }
}

class NameMatch<M> {
    int appearances = 0;
    final List<M> methods = new ArrayList<>();
}

class BacktrackException extends Exception { }
