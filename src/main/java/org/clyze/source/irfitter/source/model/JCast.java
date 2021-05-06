package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;
import org.clyze.source.irfitter.ir.model.IRCast;

/**
 * A type casting expression in the sources.
 */
public class JCast extends ElementWithPosition<IRCast, Usage> implements Targetable {
    public final String type;
    private JVariable target = null;

    public JCast(SourceFile srcFile, Position pos, String type) {
        super(srcFile, pos);
        this.type = type;
    }

    @Override
    public void initSymbolFromIRElement(IRCast irElement) {
        if (symbol == null)
            symbol = new Usage(pos, srcFile.getRelativePath(), true, irElement.getId(), irElement.methodId, UsageKind.TYPE);
        else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String toString() {
        return "CAST: " + type + " [target=" + target + "]@" + this.srcFile + ", " + pos;
    }

    @Override
    public void setTarget(JVariable v) {
        this.target = v;
    }

    @Override
    public JVariable getTarget() {
        return this.target;
    }
}
