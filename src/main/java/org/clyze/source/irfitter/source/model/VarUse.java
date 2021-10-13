package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;

/** A variable use in the source code. */
public class VarUse extends ElementUse {
    public final JVariable var;

    public VarUse(SourceFile sourceFile, Position position, UsageKind kind, JVariable var) {
        super(sourceFile, position, kind);
        this.var = var;
    }

    @Override
    public Usage getUse() {
        // Resolve the id of the referenced element via the source variable.
        if (referenceId == null && var != null && var.matchId != null)
            referenceId = var.matchId;
        return super.getUse();
    }

    @Override
    public String toString() {
        return "var-use:: " + kind.toString() + " " + var + "@" + sourceFile.getRelativePath() + ", " + position;
    }
}
