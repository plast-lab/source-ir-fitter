package org.clyze.source.irfitter.source.model;

import java.util.LinkedList;
import java.util.List;
import org.clyze.persistent.model.MethodInvocation;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.ModifierPack;

public class JVariable {
    public final SourceFile srcFile;
    public final Position pos;
    public final String name;
    public final String type;
    /** The invocations that may be involved in variable initialization. */
    public final List<MethodInvocation> invocations = new LinkedList<>();
    public final ModifierPack mp;

    public JVariable(SourceFile srcFile, Position pos, String name, String type, ModifierPack mp) {
        this.srcFile = srcFile;
        this.pos = pos;
        this.name = name;
        this.type = type;
        this.mp = mp;
    }
}
