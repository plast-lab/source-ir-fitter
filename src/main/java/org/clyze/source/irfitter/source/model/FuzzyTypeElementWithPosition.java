package org.clyze.source.irfitter.source.model;

import java.util.Collection;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.source.irfitter.ir.model.IRElement;

public abstract class FuzzyTypeElementWithPosition<T extends IRElement, S extends SymbolWithId>
        extends ElementWithPosition<T, S> implements FuzzyTypes{

    protected FuzzyTypeElementWithPosition(SourceFile srcFile, Position pos) {
        super(srcFile, pos);
    }

    @Override
    public SourceFile getSourceFile() {
        return this.srcFile;
    }

    /**
     * Return the possible ids that this element can match.
     * @return              a collection of ids
     */
    public abstract Collection<String> getIds();
}
