package org.clyze.source.irfitter.source.model;

import java.util.*;

import org.clyze.persistent.model.UsageKind;
import org.clyze.persistent.model.jvm.JvmMethod;
import org.clyze.source.irfitter.base.AbstractMethod;
import org.clyze.source.irfitter.base.AbstractMethodInvocation;
import org.clyze.source.irfitter.ir.model.IRMethod;
import org.clyze.source.irfitter.ir.model.IRModifierPack;
import org.clyze.persistent.model.Position;

/** A method in the source code. */
public class JMethod extends FuzzyTypeElementWithPosition<IRMethod, JvmMethod>
implements AbstractMethod {
    public final String name;
    private final String retType;
    public final List<JVariable> parameters;
    public JVariable receiver = null;
    public final int arity;
    protected final JType parent;
    /** The span between the first and last character of the lambda. */
    public Position outerPos;
    /** The method invocations found in the method body. */
    public final List<JMethodInvocation> invocations = new ArrayList<>();
    /** The object allocations found in the method body. */
    public final List<JAllocation> allocations = new ArrayList<>();
    /** The field accesses (reads/writes) that appear in the method body. */
    public final List<JFieldAccess> fieldAccesses = new ArrayList<>();
    /** The annotations found in the source code. */
    public final Set<String> annotations;
    /** The blocks contained in the method. */
    public final List<JBlock> blocks = new ArrayList<>();
    /** The method references found in the source code. */
    public List<JMethodRef> methodRefs = null;
    /** The casts found in the source code. */
    public List<JCast> casts = null;
    /** The lambdas found in the method. */
    public List<JLambda> lambdas = null;
    /** The element uses found in the source code. */
    public List<ElementUse> elementUses = new ArrayList<>();
    private Collection<String> cachedIds = null;
    /** True if this method accepts varargs. */
    private final boolean isVarArgs;

    public JMethod(SourceFile srcFile, String name, String retType,
                   List<JVariable> parameters, Set<String> annotations,
                   Position outerPos, JType parent, Position pos, boolean isVarArgs) {
        super(srcFile, pos);
        this.name = name;
        this.retType = retType;
        this.parameters = parameters;
        this.annotations = new HashSet<>(annotations);
        this.parent = parent;
        this.arity = parameters.size();
        this.outerPos = outerPos;
        this.isVarArgs = isVarArgs;
    }

    @Override
    public Collection<String> getIds() {
        if (cachedIds == null) {
            cachedIds = new ArrayList<>();
            List<String[]> variants = new ArrayList<>();
            variants.add(resolveType(retType).toArray(new String[0]));
            for (JVariable param : parameters)
                variants.add(resolveType(param.type).toArray(new String[0]));
            cachedIds = flattenVariants(variants);
        }
        return cachedIds;
    }

    private List<String> flattenVariants(List<String[]> variants) {
        String packageName = getSourceFile().packageName;
        String[][] components = computeCartesianProduct(variants.toArray(new String[0][0]));
        List<String> ids = new ArrayList<>();
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
            sb.append(sj);
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
        for (JVariable param : parameters)
            sj.add(param.toString());
        String parentName = parent.getUnqualifiedName();
        String parentDesc = parentName == null ? parent.toString() : parentName;
        return "method{name=" + name + ", type=" + retType + "(" + sj + "), parent=" + parentDesc + ", low-level-name=" + getLowLevelName() + "}";
    }

    @Override
    public void initSymbolFromIRElement(IRMethod irMethod) {
        if (symbol == null) {
            List<String> paramNames = new ArrayList<>();
            List<String> paramTypes = new ArrayList<>();
            for (JVariable param : parameters) {
                paramNames.add(param.name);
                paramTypes.add(param.type);
            }
            // When a class has a <clinit>() initializer but no static initializer
            // block in the sources, set the position to the type declaration.
            if (JInit.CLINIT.equals(name) && (pos == null))
                pos = parent.pos;
            JvmMethod meth = fromIRMethod(irMethod, srcFile, name, paramNames, paramTypes, pos, outerPos, parent);
            meth.setAnnotations(annotations);
            symbol = meth;
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    /**
     * Helper method to create method objects from an IRMethod template.
     * @param irMethod    the IRMethod object
     * @param srcFile     the source file
     * @param name        the source name of the method
     * @param paramNames  the parameter names
     * @param paramTypes  the parameter types
     * @param pos         the method position
     * @param outerPos    the method "outer" position
     * @param parent      the parent type
     * @return            a method metadata object
     */
    public static JvmMethod fromIRMethod(IRMethod irMethod, SourceFile srcFile,
                                         String name, List<String> paramNames,
                                         List<String> paramTypes,
                                         Position pos, Position outerPos, JType parent) {
        IRModifierPack mp = irMethod.mp;
        String[] pNames = paramNames.toArray(new String[0]);
        String[] pTypes = srcFile.synthesizeTypes ? Utils.getSynthesizedTypes(paramTypes, irMethod.paramTypes, srcFile.debug) : paramTypes.toArray(new String[0]);
        if (pNames.length != pTypes.length)
            System.out.println("WARNING: arity mismatch, source names: " + Arrays.toString(pNames) +
                    " vs. IR types: " + Arrays.toString(pTypes));
        return new JvmMethod(pos, srcFile.getRelativePath(), true, name,
                parent.matchId, irMethod.returnType, irMethod.getId(), pNames,
                pTypes, mp.isStatic(), irMethod.isInterface, mp.isAbstract(),
                mp.isNative(), mp.isSynchronized(), mp.isFinal(), mp.isSynthetic(),
                mp.isPublic(), mp.isProtected(), mp.isPrivate(), outerPos);
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

    @Override
    public boolean isVarArgs() {
        return isVarArgs;
    }

    /**
     * Record a method invocation inside this method.
     * @param scope          the scope object of the parser
     * @param name           the name of the method called
     * @param arity          the number of arguments passed
     * @param pos            the position of the invocation in the sources
     * @param sourceFile     the source file
     * @param block          the containing block
     * @param base           the receiver variable (if it exists)
     * @return               the invocation that was added
     */
    public JMethodInvocation
    addInvocation(Scope scope, String name, int arity, Position pos,
                  SourceFile sourceFile, JBlock block, String base) {
        boolean inIIB = scope.inInitializer || parent == null;
        JMethodInvocation invo = new JMethodInvocation(sourceFile, pos,
                name, arity, this, inIIB, block, base);
        if (parent == null)
            System.out.println("TODO: invocations in initializers");
        else {
            if (sourceFile.debug)
                System.out.println("Adding invocation: " + invo);
            invocations.add(invo);
        }
        return invo;
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

    /**
     * Add a method reference.
     * @param ref    the method reference to add
     */
    public void addMethodRef(JMethodRef ref) {
        if (methodRefs == null)
            methodRefs = new ArrayList<>();
        methodRefs.add(ref);
    }

    /**
     * Add a cast expression.
     * @param cast    the cast to add
     */
    public void addCast(JCast cast) {
        if (casts == null)
            casts = new ArrayList<>();
        casts.add(cast);
    }

    /**
     * Add a lambda expression.
     * @param lam     the lambda to add
     */
    public void addLambda(JLambda lam) {
        if (lambdas == null)
            lambdas = new ArrayList<>();
        lambdas.add(lam);
    }

    /**
     * Helper method to filter out some special initializer methods.
     * @return true if this is a special initializer method
     */
    public boolean isSpecialInitializer() {
        return false;
    }

    /**
     * Sets up the hidden receiver parameter for instance methods.
     */
    public void setReceiver() {
        this.receiver = new JVariable(getSourceFile(), pos, "this", parent.getSimpleName(), false, null);
    }

    /**
     * Add a variabe access (read/write).
     * @param position      the position in the source code
     * @param kind          the kind (READ/WRITE)
     * @param var           the source variable
     */
    public void addVarAccess(Position position, UsageKind kind, JVariable var) {
        VarUse vu = new VarUse(srcFile, position, kind, var);
        if (srcFile.debug)
            System.out.println("Adding variable use: " + vu);
        elementUses.add(vu);
    }

    /**
     * Add a (read) access to "this".
     * @param position      the position of "this" in the source code
     */
    public void addThisAccess(Position position) {
        if (receiver == null)
            System.err.println("ERROR: found var 'this' in method without a receiver: " + this);
        else
            addVarAccess(position, UsageKind.DATA_READ, receiver);
    }
}
