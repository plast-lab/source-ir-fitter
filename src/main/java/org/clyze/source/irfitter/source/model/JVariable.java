package org.clyze.source.irfitter.source.model;

import java.util.LinkedList;
import java.util.List;
import org.clyze.persistent.model.jvm.JvmMethodInvocation;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.ModifierPack;

/** A source variable or field. */
public class JVariable {
    /** The source file. */
    public final SourceFile srcFile;
    /** The source code position. */
    public final Position pos;
    /** The element name. */
    public final String name;
    /** The element type. */
    public final String type;
    /** The invocations that may be involved in variable initialization. */
    public final List<JvmMethodInvocation> invocations = new LinkedList<>();
    /** The modifiers of the element. */
    public final ModifierPack mp;
    /** The initial string value. */
    public JStringConstant<JVariable> initStringValue = null;

    public JVariable(SourceFile srcFile, Position pos, String name, String type, ModifierPack mp) {
        this.srcFile = srcFile;
        this.pos = pos;
        this.name = name;
        this.type = type;
        this.mp = mp;
    }
}
