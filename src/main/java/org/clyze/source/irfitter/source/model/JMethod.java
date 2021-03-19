package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.persistent.model.jvm.JvmMethod;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRModifierPack;
import org.clyze.persistent.model.Position;

/** A method in the source code. */
public class JMethod extends TypedNamedElementWithPosition<IRMethod, JvmMethod>
implements AbstractMethod {
    public final String name;
    private final String retType;
    private final List<JParameter> parameters;
    public final int arity;
    private final JType parent;
    public Position outerPos;
    /** The method invocations found in the method body. */
    public final List<JMethodInvocation> invocations = new LinkedList<>();
    /** The object allocations found in the method body. */
    public final List<JAllocation> allocations = new LinkedList<>();
    public final List<JFieldAccess> fieldAccesses = new LinkedList<>();
    /** The annotations found in the source code. */
    public final Set<String> annotations;
    private Collection<String> cachedIds = null;

    public JMethod(SourceFile srcFile, String name, String retType,
                   List<JParameter> parameters, Set<String> annotations,
                   Position outerPos, JType parent, Position pos) {
        super(srcFile, pos);
        this.name = name;
        this.retType = retType;
        this.parameters = parameters;
        this.annotations = new HashSet<>(annotations);
        this.parent = parent;
        this.arity = parameters.size();
        this.outerPos = outerPos;
    }

    @Override
    public Collection<String> getIds() {
        if (cachedIds == null) {
            cachedIds = new LinkedList<>();
            List<String[]> variants = new LinkedList<>();
            variants.add(resolveType(retType).toArray(new String[0]));
            for (JParameter param : parameters)
                variants.add(resolveType(param.type).toArray(new String[0]));
            cachedIds = flattenVariants(variants);
        }
        return cachedIds;
    }

    private List<String> flattenVariants(List<String[]> variants) {
        String packageName = getSourceFile().packageName;
        String[][] components = computeCartesianProduct(variants.toArray(new String[0][0]));
        List<String> ids = new LinkedList<>();
        for (String[] component : components) {
            StringBuilder sb = new StringBuilder();
            sb.append("<");
            sb.append(parent.getFullyQualifiedName(packageName));
            sb.append(": ");
            sb.append(component[0]);
            sb.append(' ');
            sb.append(getLowLevelName());
            sb.append('(');
            StringJoiner sj = new StringJoiner(",");
            for (int i = 1; i < component.length; i++)
                sj.add(component[i]);
            sb.append(sj.toString());
            sb.append(")>");
            ids.add(sb.toString());
        }
        return ids;
    }

    public String[][] computeCartesianProduct(String[][] variants) {
        int n = variants.length, solutions = 1;

        for (String[] set : variants)
            solutions *= set.length;

        String[][] combinations = new String[solutions][];
        for (int i = 0; i < solutions; i++) {
            List<String> combination = new ArrayList<>(n);
            int j = 1;
            for (String[] set : variants) {
                combination.add(set[((i / j) % set.length)]);
                j *= set.length;
            }
            combinations[i] = combination.toArray(new String[n]);
        }

        return combinations;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ");
        for (JParameter param : parameters)
            sj.add(param.toString());
        String parentName = parent.getUnqualifiedName();
        String parentDesc = parentName == null ? parent.toString() : parentName;
        return "method{name=" + name + ", type=" + retType + "(" + sj.toString() + "), parent=" + parentDesc + "}";
    }

    @Override
    public void initSymbolFromIRElement(IRMethod irMethod) {
        if (symbol == null) {
            List<String> paramNames = new LinkedList<>();
            List<String> paramTypes = new LinkedList<>();
            for (JParameter param : parameters) {
                paramNames.add(param.name);
                paramTypes.add(param.type);
            }
            String[] pNames = paramNames.toArray(new String[0]);
            String[] pTypes = srcFile.synthesizeTypes ? Utils.getSynthesizedTypes(paramTypes, irMethod.paramTypes, srcFile.debug) : paramTypes.toArray(new String[0]);
            if (pNames.length != pTypes.length)
                System.out.println("WARNING: arity mismatch, source names: " + Arrays.toString(pNames) +
                        " vs. IR types: " + Arrays.toString(pTypes));
            String returnType = irMethod.returnType;
            IRModifierPack mp = irMethod.mp;
            // When a class has a <clinit>() initializer but no static initializer
            // block in the sources, set the position to the type declaration.
            if ("<clinit>".equals(name) && (pos == null))
                pos = parent.pos;
            JvmMethod meth = new JvmMethod(pos, srcFile.getRelativePath(), name,
                    parent.matchId, returnType, irMethod.getId(), pNames,
                    pTypes, mp.isStatic(), irMethod.isInterface, mp.isAbstract(),
                    mp.isNative(), mp.isSynchronized(), mp.isFinal(), mp.isSynthetic(),
                    mp.isPublic(), mp.isProtected(), mp.isPrivate(), outerPos);
            meth.setAnnotations(annotations);
            symbol = meth;
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    /**
     * Return the low-level name of the method, useful to understand renamed
     * constructors.
     * @return   the low level name that executable code will see
     */
    public String getLowLevelName() {
        return (name != null && name.equals(parent.getUnqualifiedName())) ? "<init>" : name;
    }

    @Override
    public List<? extends AbstractMethodInvocation> getInvocations() {
        return invocations;
    }

    /**
     * Record a method invocation inside this method.
     * @param scope          the scope object of the parser
     * @param name           the name of the method called
     * @param arity          the number of arguments passed
     * @param pos            the position of the invocation in the sources
     * @param sourceFile     the source file
     */
    public void addInvocation(Scope scope, String name, int arity,
                              Position pos, SourceFile sourceFile) {
        boolean inIIB = scope.inInitializer || parent == null;
        JMethodInvocation invo = new JMethodInvocation(sourceFile, pos,
                name, arity, this, inIIB);
        if (parent == null)
            System.out.println("TODO: invocations in initializers");
        else {
            if (sourceFile.debug)
                System.out.println("Adding invocation: " + invo);
            invocations.add(invo);
        }
    }

    /**
     * Record an object allocation inside this method.
     * @param sourceFile     the source file
     * @param pos            the position of the invocation in the sources
     * @param simpleType     the simple type of the allocation (no package prefix)
     * @return               the recorded allocation
     */
    public JAllocation addAllocation(SourceFile sourceFile, Position pos,
                                     String simpleType) {
        JAllocation alloc = new JAllocation(sourceFile, pos, simpleType);
        if (parent == null)
            System.out.println("TODO: allocations in initializers");
        else {
            if (sourceFile.debug)
                System.out.println("Adding allocation: " + alloc);
            allocations.add(alloc);
        }
        return alloc;
    }
}
