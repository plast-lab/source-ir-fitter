package org.clyze.source.irfitter.source.model;

import java.util.Map;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.source.irfitter.ir.model.IRElement;
import org.clyze.source.irfitter.ir.model.IRType;

public abstract class ElementWithPosition<T extends IRElement, S extends SymbolWithId>
implements Matchable {
    public Position pos;
    public final SourceFile srcFile;
    public String matchId = null;
    public S symbol = null;
    public T matchElement = null;

    protected ElementWithPosition(SourceFile srcFile, Position pos) {
        this.srcFile = srcFile;
        this.pos = pos;
    }

    public abstract void initSymbolFromIRElement(T irElement);

    public S getSymbol() {
        return this.symbol;
    }

    /**
     * Pretty printer for location (source-file + position).
     * @return a string representation of the location of this element
     */
    protected String getLocation() {
        return Utils.getLocation(srcFile.getRelativePath(), pos);
    }

    @Override
    public boolean hasBeenMatched() {
        return this.matchId != null;
    }

    /**
     * Generates partial metadata for elements with partial information.
     * Override in subclasses as needed.
     * @param irTypeLookup the global id-to-IR-type mapping to use for auxiliary resolution
     * @return             the (partial) metadata object
     */
    public SymbolWithId generatePartialMetadata(Map<String, IRType> irTypeLookup) {
        if (srcFile.debug)
            System.out.println("WARNING: not generating partial metadata for element: " + this);
        return null;
    }
}
