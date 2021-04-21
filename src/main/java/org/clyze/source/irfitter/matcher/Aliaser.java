package org.clyze.source.irfitter.matcher;

import org.clyze.persistent.model.SymbolAlias;
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

    public Aliaser(boolean translateResults, boolean debug, boolean json) {
        this.translateResults = translateResults;
        this.debug = debug;
        this.json = json;
    }

    /**
     * Adds a symbol alias: "IR variable i is an alias for source variable x".
     * @param TAG            a tag to use when printing debugging information
     * @param srcVar         the source variable
     * @param irVarId        the symbol id of the IR variable
     */
    public void addIrAlias(String TAG, JVariable srcVar, String irVarId) {
        if (debug)
            System.out.println(TAG + ": variable alias " + srcVar + " -> " + irVarId);
        if (srcVar.symbol == null) {
            if (debug)
                System.out.println(TAG + ": aborting due to null symbol.");
            return;
        }
        String srcSymbolId = srcVar.symbol.getSymbolId();
        if (json) {
            SymbolAlias alias = new SymbolAlias(srcVar.srcFile.getRelativePath(), irVarId, srcSymbolId);
            srcVar.srcFile.getJvmMetadata().aliases.add(alias);
            if (debug)
                System.out.println(TAG + ": alias = " + alias);
        }
        if (translateResults) {
            aliases.computeIfAbsent(irVarId, (k -> new HashSet<>())).add(srcSymbolId);
            if (debug)
                System.out.println(TAG + ": alias = " + irVarId + " <-> " + srcSymbolId);
        }
    }
}
