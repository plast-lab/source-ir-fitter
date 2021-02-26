package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.source.irfitter.ir.model.IRElement;

public abstract class TypedNamedElementWithPosition<T extends IRElement, S extends SymbolWithId>
        extends NamedElementWithPosition<T, S> implements FuzzyTypes {
    protected TypedNamedElementWithPosition(SourceFile srcFile, Position pos) {
        super(srcFile, pos);
    }

    @Override
    public SourceFile getSourceFile() {
        return this.srcFile;
    }
}
