package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.jvm.JvmVariable;
import org.clyze.source.irfitter.ir.model.IRParameter;

/** A named parameter (with an optional type). Used in method declarations. */
public class JParameter extends NamedElementWithPosition<IRParameter, JvmVariable> {
    /** The parameter name. */
    public final String name;
    /** The parameter type (may be null). */
    public final String type;
    /** The position of the parameter. */
    public final Position position;

    /**
     * Create a method parameter.
     * @param sourceFile the source file containing this parameter
     * @param position   the position of the parameter
     * @param name       the name of the parameter
     * @param type       the type of the parameter (may be null)
     */
    public JParameter(SourceFile sourceFile, Position position, String name, String type) {
        super(sourceFile, position);
        this.name = name;
        this.type = type;
        this.position = position;
    }

    @Override
    public String toString() {
        return (type == null ? "*" : type) + " " + name;
    }

    @Override
    public void initSymbolFromIRElement(IRParameter irElement) {
        if (symbol == null)
            symbol = new JvmVariable(position, srcFile.getRelativePath(), true,
                    irElement.name, irElement.getId(), type,
                    irElement.declaringMethodId, false, true, false);
        else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }
}
