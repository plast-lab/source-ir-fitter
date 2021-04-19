package org.clyze.source.irfitter.matcher;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import org.clyze.persistent.model.SymbolAlias;
import org.clyze.source.irfitter.source.model.IdMapper;
import org.clyze.source.irfitter.source.model.JAllocation;
import org.clyze.source.irfitter.source.model.JMethodInvocation;
import org.clyze.source.irfitter.source.model.JVariable;

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

    private void processAssignHeap() {
        processFacts("AssignHeapAllocation.facts", 6, ((String[] parts) -> {
            String irAllocId = parts[2];
            String irVarId = parts[3];
            Map<String, Collection<JAllocation>> allocationMap = idMapper.allocationMap;
            Collection<JAllocation> srcAllocs = allocationMap.get(irAllocId);
            if (srcAllocs != null) {
                for (JAllocation srcAlloc : srcAllocs) {
                    if (debug)
                        System.out.println("ALLOC_FACTS: IR allocation: " + irAllocId + " -> " + srcAlloc);
                    JVariable srcVar = srcAlloc.getTarget();
                    if (srcVar != null)
                        addIrAlias("ALLOC_FACTS", srcVar, irVarId);
                }
            }
        }));
    }

    private void addIrAlias(String TAG, JVariable srcVar, String irVarId) {
        if (debug)
            System.out.println(TAG + ": variable alias " + srcVar + " -> " + irVarId);
        SymbolAlias alias = new SymbolAlias(srcVar.srcFile.getRelativePath(), irVarId, srcVar.symbol.getSymbolId());
        srcVar.srcFile.getJvmMetadata().aliases.add(alias);
        if (debug)
            System.out.println(TAG + ": alias = " + alias);
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
                    JMethodInvocation srcInvo = idMapper.srcInvoMap.get(irInvoId);
                    if (srcInvo != null) {
                        if (debug)
                            System.out.println("INVOCATION_FACTS: IR invocation: " + irInvoId + " -> " + srcInvo);
                        JVariable base = srcInvo.getBase();
                        if (base != null)
                            addIrAlias("INVOCATION_FACTS", base, baseId);
                    }
                }));
    }
}
