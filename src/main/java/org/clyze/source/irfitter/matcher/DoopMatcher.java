package org.clyze.source.irfitter.matcher;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.clyze.source.irfitter.ir.model.IRVariable;
import org.clyze.source.irfitter.source.model.*;

/**
 * This class implements Doop-specific functionality for matching IR (Jimple)
 * elements against source code elements.
 */
public class DoopMatcher {
    /** The Doop database directory. */
    private final File db;
    /** If true, emit debug messages. */
    private final boolean debug;
    /** The mapper object to update while matching elements. */
    private final IdMapper idMapper;

    public DoopMatcher(File db, boolean debug, IdMapper idMapper) {
        this.db = db;
        this.debug = debug;
        this.idMapper = idMapper;
    }

    public void resolveDoopVariables() {
        System.out.println("Resolving variables from facts in " + db);
        processInstanceInvocations("SpecialMethodInvocation.facts", 5, 0, 3);
        processInstanceInvocations("SuperMethodInvocation.facts", 5, 0, 3);
        processInstanceInvocations("VirtualMethodInvocation.facts", 5, 0, 3);
        processAssignHeap();
        processAssignLocal();
    }

    private void processFacts(String factsFileName, int columns, Consumer<String[]> proc) {
        File factsFile = new File(db, factsFileName);
        if (factsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(factsFile))))  {
                br.lines().forEach(line -> {
                    String[] parts = line.split("\t");
                    if (parts.length < columns) {
                        System.out.println("Ignoring line: " + line);
                        return;
                    }
                    proc.accept(parts);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            System.err.println("ERROR: could not read " + factsFile);
    }

    /**
     * Process the assignments from heap allocations to IR "variables".
     */
    private void processAssignHeap() {
        processFacts("AssignHeapAllocation.facts", 6, ((String[] parts) -> {
            String irAllocId = parts[2], irVarId = parts[3];
            Map<String, Collection<JAllocation>> allocationMap = idMapper.allocationMap;
            Collection<JAllocation> srcAllocs = allocationMap.get(irAllocId);
            if (srcAllocs != null) {
                for (Targetable srcAlloc : srcAllocs) {
                    if (debug)
                        System.out.println("ALLOC_FACTS: IR allocation: " + irAllocId + " -> " + srcAlloc);
                    JVariable srcVar = srcAlloc.getTarget();
                    if (srcVar != null)
                        Matcher.addIrAlias("ALLOC_FACTS", srcVar, irVarId, debug);
                }
            }
        }));
    }

    /**
     * Process local variable assignments. Used to detect formal parameter aliases.
     */
    private void processAssignLocal() {
        processFacts("AssignLocal.facts", 5, ((String[] parts) -> {
            String fromVar = parts[2];
            // The variable extractor (only defined for interesting relation entries).
            Function<JMethod, JVariable> varSupplier = null;
            int preIdx = fromVar.indexOf(IRVariable.PARAM_PRE);
            if (preIdx >= 0)
                varSupplier = ((JMethod srcMethod) -> {
                    try {
                        int paramIdx = Integer.parseInt(fromVar.substring(preIdx + IRVariable.PARAM_PRE.length()));
                        return srcMethod.parameters.get(paramIdx);
                    } catch (Exception ex) {
                        System.err.println("ERROR: could not create alias for variable: " + fromVar + ": " + ex.getMessage());
                        return null;
                    }
                });
            else if (fromVar.endsWith(IRVariable.THIS_NAME))
                varSupplier = ((JMethod srcMethod) -> srcMethod.receiver);
            if (varSupplier != null) {
                String toIrVarId = parts[3], declaringMethodId = parts[4];
                Collection<JMethod> srcMethods = idMapper.methodMap.get(declaringMethodId);
                if (srcMethods != null)
                    for (JMethod srcMethod : srcMethods) {
                        JVariable srcVar = varSupplier.apply(srcMethod);
                        if (srcVar != null)
                            Matcher.addIrAlias("LOCAL_FACTS", srcVar, toIrVarId, debug);
                    }
            }
        }));
    }



    /**
     * Process a facts file representing an instance method invocation, to
     * match "base" variables.
     * @param factsFileName   the file name of the facts file
     * @param columns         the number of relation columns
     * @param invoIdx         the index of the invocation-id column
     * @param baseIdx         the index of the base-variable-id column
     */
    @SuppressWarnings("SameParameterValue")
    private void processInstanceInvocations(String factsFileName, int columns,
                                            int invoIdx, int baseIdx) {
        processFacts(factsFileName, columns,
                ((String[] parts) -> {
                    String irInvoId = parts[invoIdx];
                    String baseId = parts[baseIdx];
                    Collection<JMethodInvocation> srcInvos = idMapper.invocationMap.get(irInvoId);
                    if (srcInvos == null)
                        return;
                    for (JMethodInvocation srcInvo : srcInvos) {
                        if (debug)
                            System.out.println("INVOCATION_FACTS: IR invocation: " + irInvoId + " -> " + srcInvo);
                        JVariable base = srcInvo.getBase();
                        if (base != null)
                            Matcher.addIrAlias("INVOCATION_FACTS", base, baseId, debug);
                    }
                }));
    }
}
