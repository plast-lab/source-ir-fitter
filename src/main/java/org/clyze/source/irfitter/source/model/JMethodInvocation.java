package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.jvm.JvmMethodInvocation;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRMethodInvocation;

/** A method invocation in the source code. */
public class JMethodInvocation extends ElementWithPosition<IRMethodInvocation, JvmMethodInvocation>
implements AbstractMethodInvocation, Targetable {
    /** The method containing the invocation line. */
    public final JMethod parent;
    /** The name of the invoked method. */
    public final String methodName;
    /** The number of arguments in the call. */
    public final int arity;
    /** True if the invocation is inside an initializer block. */
    public final boolean inIIB;
    private final JBlock block;
    private final String base;
    /** The target of the allocation (such as "x" in {@code x = obj.m()}). */
    private JVariable target = null;

    /**
     * Create a method invocation.
     * @param srcFile      the source file containing the invocation
     * @param pos          the source code position
     * @param methodName   the name of the invoked method
     * @param arity        the number of arguments in the call
     * @param parent       the method containing the invocation
     * @param inIIB        true if the invocation is inside an initializer block
     * @param block        the containing block
     * @param base         the name of the base variable (if it exists)
     */
    public JMethodInvocation(SourceFile srcFile, Position pos,
                             String methodName, int arity, JMethod parent,
                             boolean inIIB, JBlock block, String base) {
        super(srcFile, pos);
        this.methodName = methodName;
        this.arity = arity;
        this.parent = parent;
        this.inIIB = inIIB;
        this.block = block;
        this.base = base;
    }

    @Override
    public void initSymbolFromIRElement(IRMethodInvocation irMethodInvocation) {
        if (symbol == null) {
            matchElement = irMethodInvocation;
            symbol = new JvmMethodInvocation(pos, srcFile.getRelativePath(), true,
                    methodName, irMethodInvocation.getId(),
                    irMethodInvocation.targetType,
                    irMethodInvocation.targetReturnType,
                    irMethodInvocation.targetParamTypes,
                    irMethodInvocation.invokingMethodId, inIIB);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    @Override
    public String toString() {
        return "invo:: " + getId();
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
        return "method: [base: " + base + ", target:" + target + ", parent:" + parentDesc + "]/" + methodName + ":" + arity + getLocation();
    }

    public JVariable getBase() {
        if (block == null)
            return null;
        JBlock.Result lookup = block.lookup(base);
        return lookup == null ? null : lookup.variable;
    }

    @Override
    public void setTarget(JVariable target) {
        this.target = target;
    }

    @Override
    public JVariable getTarget() {
        return this.target;
    }
}
