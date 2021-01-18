package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.ir.model.IRElement;

public abstract class TypedNamedElementWithPosition<T extends IRElement>
        extends NamedElementWithPosition<T> implements FuzzyTypes {
    protected TypedNamedElementWithPosition(SourceFile srcFile, Position pos) {
        super(srcFile, pos);
    }

    @Override
    public SourceFile getSourceFile() {
        return this.srcFile;
    }
}
