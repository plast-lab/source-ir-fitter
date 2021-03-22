package org.clyze.source.irfitter.source.model;

import org.clyze.source.irfitter.ir.model.IRElement;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;

public abstract class NamedElementWithPosition<T extends IRElement, S extends SymbolWithId> {
    public Position pos;
    public final SourceFile srcFile;
    public String matchId = null;
    public S symbol = null;
    public T matchElement = null;

    protected NamedElementWithPosition(SourceFile srcFile, Position pos) {
        this.srcFile = srcFile;
        this.pos = pos;
    }

    public abstract void initSymbolFromIRElement(T irElement);

    public S getSymbol() {
        return this.symbol;
    }

    /**
     * Set the "source" flag on the matched symbol. This will fail if no
     * matching symbol has already been configured.
     * @param source   true if the symbol is to be characterized as "source",
     *                 false otherwise
     */
    public void setSource(boolean source) {
        if (symbol == null)
            System.out.println("ERROR: cannot set 'source' attribute for null symbol.");
        else
            this.symbol.setSource(source);
    }

    /**
     * Pretty printer for location (source-file + position).
     * @return a string representation of the location of this element
     */
    protected String getLocation() {
        return "@" + srcFile.file.getPath() + ":" + pos;
    }
}
