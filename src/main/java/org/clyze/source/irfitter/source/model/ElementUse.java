package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;

/** A point of use of a code element (such as a variable read). */
public abstract class ElementUse {
    public final Position position;
    public final SourceFile sourceFile;
    protected final UsageKind kind;
    public String referenceId = null;

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
}
