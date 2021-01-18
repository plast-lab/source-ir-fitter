package org.clyze.source.irfitter.source.model;

import org.clyze.source.irfitter.ir.model.IRElement;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithDoopId;

public abstract class NamedElementWithPosition<T extends IRElement> {
    public Position pos;
    public final SourceFile srcFile;
    public String matchId = null;
    public SymbolWithDoopId symbol = null;
    public T matchElement = null;

    protected NamedElementWithPosition(SourceFile srcFile, Position pos) {
        this.srcFile = srcFile;
        this.pos = pos;
    }

    public abstract void initSymbolFromIRElement(T irElement);

    public SymbolWithDoopId getSymbol() {
        return this.symbol;
    }
}
