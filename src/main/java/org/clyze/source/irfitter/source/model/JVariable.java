package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.jvm.JvmVariable;
import org.clyze.source.irfitter.base.ModifierPack;
import org.clyze.source.irfitter.ir.model.IRVariable;

/** A source variable. */
public class JVariable extends NamedElementWithPosition<IRVariable, JvmVariable> {
    /** The element name. */
    public final String name;
    /** The element type. Can be null when the type is omitted (Groovy, Kotlin). */
    public final String type;
    /** If true, this is a local variable; if false, this is a method parameter. */
    private final boolean isLocal;
    /** The modifiers of the element. */
    public final ModifierPack mp;
    /** The initial string value. */
    public JStringConstant<JVariable> initStringValue = null;

    /**
     * Create a variable.
     * @param sourceFile the source file containing this variable
     * @param position   the position of the variable
     * @param name       the name of the variable
     * @param type       the type of the variable (may be null)
     * @param isLocal    true for method locals, false for method parameters
     * @param mp         the modifiers of this variable
     */
    public JVariable(SourceFile sourceFile, Position position, String name,
                     String type, boolean isLocal, ModifierPack mp) {
        super(sourceFile, position);
        this.name = name;
        this.type = type;
        this.isLocal = isLocal;
        this.mp = mp;
    }

    @Override
    public String toString() {
        return (type == null ? "*" : type) + " " + name + getLocation();
    }

    @Override
    public void initSymbolFromIRElement(IRVariable irElement) {
        if (symbol == null)
            symbol = new JvmVariable(pos, srcFile.getRelativePath(), true,
                    irElement.name, irElement.getId(), type,
                    irElement.declaringMethodId, isLocal, !isLocal, false);
        else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    /**
     * Generates a synthetic IR code element that corresponds to this source
     * variable. Used when the IR has no concrete support for local variables.
     * @param declaringMethodId   the declaring method
     */
    public void initSyntheticIRVariable(String declaringMethodId) {
        IRVariable irVar = new IRVariable(declaringMethodId + "/VAR@" + type + "@" + name, name, declaringMethodId);
        initSymbolFromIRElement(irVar);
        this.matchId = irVar.getId();
    }
}
