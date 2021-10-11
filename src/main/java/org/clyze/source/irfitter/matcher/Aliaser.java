package org.clyze.source.irfitter.matcher;

import org.clyze.persistent.model.SymbolAlias;
import org.clyze.source.irfitter.ir.model.IRVariable;
import org.clyze.source.irfitter.source.model.IdMapper;
import org.clyze.source.irfitter.source.model.JVariable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * This class handles symbol aliases (currently used to resolve Doop locals
 * against variables found in the sources.
 */
public class Aliaser {
    /** If true, keep more data to support Doop results translation. */
    private final boolean translateResults;
    /** If true, emit debug messages. */
    private final boolean debug;
    /** If true, support JSON metadata generation. */
    private final boolean json;
    /** A mapping from IR symbol ids to one or more source code variable ids. */
    public final Map<String, Collection<String>> aliases = new HashMap<>();
    /** The mapper object to use for registering variable aliases. */
    private final IdMapper idMapper;

    public Aliaser(boolean translateResults, boolean debug, boolean json, IdMapper idMapper) {
        this.translateResults = translateResults;
        this.debug = debug;
        this.json = json;
        this.idMapper = idMapper;
    }

    /**
     * Adds a symbol alias: "IR variable i is an alias for source variable x".
     * Used when there is no IR variable information available.
     * @param variableMap    the variable map to update
     * @param tag            a tag to use when printing debugging information
     * @param srcVar         the source variable
     * @param irVarId        the IR variable id
     */
    public void addIrAlias(Map<String, Collection<JVariable>> variableMap,
                           String tag, JVariable srcVar, String irVarId) {
        IRVariable irVar = IRVariable.fromSymbolId(irVarId);
        addIrAlias(variableMap, tag, srcVar, irVar);
        idMapper.registerSourceVariable(irVar.declaringMethodId, srcVar, debug);
    }

    /**
     * Adds a symbol alias: "IR variable i is an alias for source variable x".
     * @param tag            a tag to use when printing debugging information
     * @param srcVar         the source variable
     * @param irVar          the IR variable
     */
    public void addIrAlias(Map<String, Collection<JVariable>> variableMap,
                           String tag, JVariable srcVar, IRVariable irVar) {
        String irVarId = irVar.getId();
        if (debug)
            System.out.println(tag + ": variable alias " + srcVar + " -> " + irVarId);
        if (srcVar.symbol == null) {
            if (debug)
                System.out.println(tag + ": null symbol found, using provided IR variable id.");
            srcVar.initSymbolFromIRElement(IRVariable.fromSymbolId(irVarId));
        }
        String srcSymbolId = srcVar.symbol.getSymbolId();
        if (json) {
            SymbolAlias alias = new SymbolAlias(srcVar.srcFile.getRelativePath(), irVarId, srcSymbolId);
            srcVar.srcFile.getJvmMetadata().aliases.add(alias);
            if (debug)
                System.out.println(tag + ": alias = " + alias);
        }
        idMapper.recordMatch(variableMap, "variable", irVar, srcVar);
        if (translateResults) {
            aliases.computeIfAbsent(irVarId, (k -> new HashSet<>())).add(srcSymbolId);
            if (debug)
                System.out.println(tag + ": alias = " + irVarId + " <-> " + srcSymbolId);
        }
    }
}
