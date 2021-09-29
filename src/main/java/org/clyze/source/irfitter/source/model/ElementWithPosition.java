package org.clyze.source.irfitter.source.model;

import org.clyze.source.irfitter.ir.model.IRElement;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;

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
     */
    public SymbolWithId generatePartialMetadata() {
        if (srcFile.debug)
            System.out.println("WARNING: not generating partial metadata for element: " + this);
        return null;
    }
}
