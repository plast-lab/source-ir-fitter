package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;

/** A point of use of a code element (such as a variable read). */
public abstract class ElementUse implements Matchable {
    /** The source code position. */
    public final Position position;
    /** The source file where the element appears. */
    public final SourceFile sourceFile;
    protected final UsageKind kind;
    /** The id of the IR code element actually used. */
    public String referenceId = null;

    /**
     * Create an object that represents a use of a source code element.
     * @param sourceFile    the source file where the element appears
     * @param position      the source code position
     * @param kind          the type of the use
     */
    public ElementUse(SourceFile sourceFile, Position position, UsageKind kind) {
        this.sourceFile = sourceFile;
        this.position = position;
        this.kind = kind;
    }

    /**
     * Build the final metadata object.
     * @return the "usage" object
     */
    public Usage getUse() {
        if (referenceId == null)
            System.out.println("ERROR: Use not matched: " + this);
        String useId = this.toString();
        return new Usage(position, sourceFile.getRelativePath(), true, useId, referenceId, kind);
    }

    @Override
    public boolean hasBeenMatched() {
        return this.referenceId != null;
    }
}
