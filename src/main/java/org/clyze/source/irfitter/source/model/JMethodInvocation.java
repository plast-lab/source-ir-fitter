package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.MethodInvocation;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;

public class JMethodInvocation extends NamedElementWithPosition<IRMethodInvocation>
implements AbstractMethodInvocation {
    public final JMethod parent;
    public final String methodName;
    public final int arity;
    public final boolean inIIB;

    public JMethodInvocation(SourceFile srcFile, Position pos,
                             String methodName, int arity, JMethod parent,
                             boolean inIIB) {
        super(srcFile, pos);
        this.methodName = methodName;
        this.arity = arity;
        this.parent = parent;
        this.inIIB = inIIB;
    }

    @Override
    public void initSymbolFromIRElement(IRMethodInvocation irMethodInvocation) {
        if (symbol == null) {
            matchElement = irMethodInvocation;
            symbol = new MethodInvocation(pos, srcFile.getRelativePath(),
                    methodName, irMethodInvocation.getId(),
                    irMethodInvocation.invokingMethodId, inIIB);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getDoopId());
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public int getArity() {
        return arity;
    }

    @Override
    public String getId() {
        String parentDesc = parent == null ? "{}" : (parent.matchId == null ? parent.toString() : parent.matchId);
        String posDesc = pos == null ? "unknown" : pos.toString();
        return "method: [parent:" + parentDesc + "]/" + methodName + ":" + arity + "@" + posDesc;
    }
}
