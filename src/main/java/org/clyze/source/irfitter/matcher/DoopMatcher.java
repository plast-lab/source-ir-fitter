package org.clyze.source.irfitter.matcher;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
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
    /** The symbol-id aliasing handler. */
    private final Aliaser aliaser;
    /** The variable columns in interesting Doop outputs. */
    private final Map<String, int[]> relationVarColumns;

    public DoopMatcher(File db, boolean debug, IdMapper idMapper, Aliaser aliaser, String[] relVars) {
        this.db = db;
        this.debug = debug;
        this.idMapper = idMapper;
        this.aliaser = aliaser;
        this.relationVarColumns = initRelationVarColumns(relVars);
    }

    public void resolveDoopVariables() {
        if (!db.exists()) {
            System.err.println("ERROR: cannot resolve variables, database directory "  + db + " does not exist.");
            return;
        }
        System.out.println("Resolving variables from facts in " + db);
        processInstanceInvocations("SpecialMethodInvocation.facts", 5, 0, 3);
        processInstanceInvocations("SuperMethodInvocation.facts", 5, 0, 3);
        processInstanceInvocations("VirtualMethodInvocation.facts", 5, 0, 3);
        processAssignHeap();
        processAssignLocal();
        processAssignReturnValue();
    }

    private void processFacts(String factsFileName, int columns, Consumer<String[]> proc) {
        File factsFile = new File(db, factsFileName);
        if (factsFile.exists()) {
            if (debug)
                System.err.println("Processing file: " + factsFileName);
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

    private <T extends Targetable>
    void processTargetVariable(String TAG, Map<String, Collection<T>> map,
                               String irInstrId, String irVarId) {
        Collection<? extends Targetable> srcInstrs = map.get(irInstrId);
        if (srcInstrs != null)
            for (Targetable srcInstr : srcInstrs) {
                if (debug)
                    System.out.println(TAG + ": IR targetable: " + irInstrId + " -> " + srcInstr);
                JVariable srcVar = srcInstr.getTarget();
                if (srcVar != null)
                    aliaser.addIrAlias(TAG, srcVar, irVarId);
            }
    }

    /**
     * Process the assignments from heap allocations to IR "variables".
     */
    private void processAssignReturnValue() {
        processFacts("AssignReturnValue.facts", 2, ((String[] parts) -> {
            String irInvoId = parts[0], irVarId = parts[1];
            processTargetVariable("ASSIGN_RETURN_FACTS", idMapper.invocationMap, irInvoId, irVarId);
        }));
    }

    /**
     * Process the assignments from heap allocations to IR "variables".
     */
    private void processAssignHeap() {
        processFacts("AssignHeapAllocation.facts", 6, ((String[] parts) -> {
            String irAllocId = parts[2], irVarId = parts[3];
            processTargetVariable("ALLOC_FACTS", idMapper.allocationMap, irAllocId, irVarId);
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
                            aliaser.addIrAlias("LOCAL_FACTS", srcVar, toIrVarId);
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
                            aliaser.addIrAlias("INVOCATION_FACTS", base, baseId);
                    }
                }));
    }

    public static Map<String, int[]> initRelationVarColumns(String[] relVars) {
        Map<String, int[]> ret = new HashMap<>();
        if (relVars != null) {
            for (String relVarDesc : relVars) {
                try {
                    String[] parts = relVarDesc.split(":");
                    String relName = parts[0];
                    int[] cols = Arrays.stream(parts[1].split(",")).mapToInt(Integer::parseInt).toArray();
                    ret.put(relName, cols);
                    System.out.println("Relation variables: " + relName + " / " + Arrays.toString(cols));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return ret;
    }

    public void translateResults(boolean uniqueResults) {
        System.out.println("Translating results in " + db);
        for (Map.Entry<String, int[]> entry : relationVarColumns.entrySet()) {
            File rel = new File(db, entry.getKey());
            if (rel.exists()) {
                File newFile = new File(db, entry.getKey() + ".new");
                System.out.println("Translating relation: " + rel);
                int[] varCols = entry.getValue();
                OptionalInt max = Arrays.stream(varCols).max();
                // Sanity check: there is at least one usable column index.
                if (!max.isPresent())
                    return;
                int maxCol = max.getAsInt();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(newFile)))  {
                    DoopResultsWriter resWriter = new DoopResultsWriter(uniqueResults, bw);
                    Files.lines(rel.toPath()).forEach(line -> {
                        String[] parts = line.split("\t");
                        if (parts.length < maxCol) {
                            System.out.println("Ignoring line: " + line);
                            return;
                        }
                        // Filter flag: only translated lines survive.
                        boolean write = false;
                        for (int i : varCols) {
                            Collection<String> aliases = aliaser.aliases.get(parts[i]);
                            if (aliases != null) {
                                if (aliases.size() == 1) {
                                    for (String alias : aliases) {
                                        if (debug)
                                            System.out.println("Translating: " + parts[i] + " -> " + alias);
                                        parts[i] = alias;
                                        write = true;
                                    }
                                } else
                                    System.out.println("ERROR: multi-aliases not yet supported: " + parts[i]);
                            }
                        }
                        if (write)
                            try {
                                resWriter.write(String.join("\t", parts) + '\n');
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    });
                    resWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Replacing " + rel + " with " + newFile);
                boolean relAction = debug ? rel.renameTo(new File(rel.getParentFile(), rel.getName() + ".backup")) : rel.delete();
                boolean rename = newFile.renameTo(rel);
                if (debug)
                    System.out.println("Move operation: relAction=" + relAction + ", rename=" + rename);
            }
        }
    }
}

