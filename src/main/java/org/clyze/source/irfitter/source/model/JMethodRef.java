package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;
import org.clyze.source.irfitter.ir.model.IRMethodRef;

/**
 * Method references (<code>A::meth</code> in Java/Kotlin or {@code A.&meth} in Groovy).
 * The model of this feature reuses type-use machinery to be able to resolve
 * the type part of the reference.
 */
public class JMethodRef extends ElementWithPosition<IRMethodRef, Usage> {
    /** The name of the method. */
    public final String methodName;

    public JMethodRef(SourceFile sourceFile, Position position, String methodName) {
        super(sourceFile, position);
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return "METHOD-REFERENCE: " + methodName + "@" + this.srcFile + ", " + pos;
    }

    @Override
    public void initSymbolFromIRElement(IRMethodRef irElement) {
        if (symbol == null)
            symbol = new Usage(pos, srcFile.getRelativePath(), true, irElement.getId(), irElement.methodId, UsageKind.FUNCTION);
        else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }
}
