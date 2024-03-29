package org.clyze.source.irfitter.source.model;

import java.util.*;
import org.clyze.persistent.model.SymbolWithId;
import org.clyze.persistent.model.jvm.JvmClass;
import org.clyze.source.irfitter.base.ModifierPack;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.persistent.model.Position;
import org.clyze.utils.TypeUtils;

/**
 * A reference type (such as a class, enum, or interface) in the source code.
 */
public class JType extends ElementWithPosition<IRType, JvmClass> {
    private final String name;
    public final JType parentType;
    public final ElementWithPosition<?, ?> declaringElement;
    public final List<String> superTypes;
    public final Set<String> annotationTypes;
    public final boolean isInterface;
    public final boolean isEnum;
    public final boolean isPublic;
    private final boolean isPrivate;
    public final boolean isProtected;
    public final boolean isAbstract;
    public final boolean isFinal;
    public final boolean isAnonymous;
    public final boolean isLambdaType;
    public final boolean isInner;
    /** The class initializer ({@code <clinit>} in bytecode). */
    public final JInit classInitializer;
    /**
     * The instance initialization code that appears outside constructors
     * (such as initializer blocks or field initializers).
     */
    public final JInit instInitializer;
    public final List<JField> fields = new ArrayList<>();
    public final List<JMethod> methods = new ArrayList<>();
    public final List<TypeUse> typeUses = new ArrayList<>();
    private int anonymousClassCounter = 1;
    /**
     * If non-null, this records non-anonoymous nested classes declared inside
     * methods (which should follow a different fully-qualified name scheme).
     */
    public Map<String, Integer> methodTypeCounters = null;
    /** The optional prefix of this type (if declared inside a method). */
    public Integer methodTypeCounter = null;

    public JType(SourceFile srcFile, String name, List<String> superTypes,
                 Set<String> annotationTypes, Position pos,
                 ElementWithPosition<?, ?> declaringElement, JType parentType,
                 boolean isInner, boolean isPublic, boolean isPrivate,
                 boolean isProtected, boolean isAbstract, boolean isFinal,
                 boolean isAnonymous, boolean isLambdaType,
                 boolean isInterface, boolean isEnum) {
        super(srcFile, pos);
        this.name = name;
        this.superTypes = superTypes;
        this.annotationTypes = annotationTypes;
        this.parentType = parentType;
        this.declaringElement = declaringElement;
        this.isPublic = isPublic;
        this.isInner = isInner;
        this.isPrivate = isPrivate;
        this.isProtected = isProtected;
        this.isAbstract = isAbstract;
        this.isFinal = isFinal;
        this.isAnonymous = isAnonymous;
        this.isLambdaType = isLambdaType;
        this.isInterface = isInterface;
        this.isEnum = isEnum;
        // Add <clinit>() method.
        this.classInitializer = JInit.createClinit(srcFile, this);
        // Add instance initializer block.
        this.instInitializer = JInit.createInitBlock(srcFile, this);
    }

    /**
     * Return the part of the fully-qualified name without any prefix.
     * @return the bare unqualified type name
     */
    public String getUnqualifiedName() {
        return this.name;
    }

    /**
     * Returns a simple name to be shown to the user.
     * @return the simple type name
     */
    public String getSimpleName() {
        return this.name;
    }

    public String getFullyQualifiedName() {
        return getFullyQualifiedName(srcFile.packageName);
    }

    public String getFullyQualifiedName(String packageName) {
        Stack<String> stack = new Stack<>();
        stack.push(getUnqualifiedName());
        if (methodTypeCounter != null)
            stack.push(methodTypeCounter.toString());
        JType pn = parentType;
        while (pn != null) {
            stack.push("$");
            stack.push(pn.getUnqualifiedName());
            pn = pn.parentType;
        }
        if (packageName != null && !packageName.equals("")) {
            stack.push(".");
            stack.push(packageName);
        }
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty())
            sb.append(stack.pop());
        return sb.toString();
    }

    @Override
    public String toString() {
        return "type:: name=" + getUnqualifiedName() + " (" + getSimpleName() + "), " + fields.size() + " fields, " + methods.size() + " methods}" + getLocation();
    }

    /**
     * Create a JvmClass object for this type. This is used both when good
     * information is available (due to matching with the IR) and when such
     * information is missing (and thus partial data may be generated).
     *
     * @param symbolId          the fully-qualified name of the type
     * @param isInterface       true if this is an interface
     * @param isEnum            true if this is an enum
     * @param isInner           true if this is an inner class
     * @param isAnonymous       true if this is an anonymous class
     * @param isAbstract        true if this is an abstract class
     * @param isFinal           true if this is a final class
     * @param isPublic          true if this is a public class
     * @param isProtected       true if this is a protected class
     * @return                  a JvmClass object matching this type
     */
    public JvmClass getJvmClassWith(String symbolId, boolean isInterface, boolean isEnum,
                                    boolean isInner, boolean isAnonymous, boolean isAbstract,
                                    boolean isFinal, boolean isPublic, boolean isProtected) {
        boolean isStatic = parentType != null && !isInner;
        JvmClass jc = new JvmClass(pos, srcFile.getRelativePath(), true, srcFile.artifact, getSimpleName(),
                srcFile.packageName, symbolId, isInterface, isEnum, isStatic,
                isInner, isAnonymous, isAbstract, isFinal, isPublic, isProtected, isPrivate);
        jc.setAnnotations(annotationTypes);
        return jc;
    }

    @Override
    public void initSymbolFromIRElement(IRType irType) {
        if (symbol == null) {
            String symbolId = irType.getId();
            ModifierPack mods = irType.mp;
            boolean isAbstract = mods.isAbstract();
            boolean isFinal = mods.isFinal();
            boolean isProtected = mods.isProtected();
            boolean isPublic = mods.isPublic();
            boolean isInterface = mods.isInterface();
            boolean isEnum = mods.isEnum();
            if (srcFile.debug) {
                System.out.println("Creating Class with symbolId = " + symbolId);
                checkModifiers(symbolId, "abstract", mods.isAbstract(), this.isAbstract);
                checkModifiers(symbolId, "final", mods.isFinal(), this.isFinal);
                checkModifiers(symbolId, "private", mods.isPrivate(), this.isPrivate);
                checkModifiers(symbolId, "protected", mods.isProtected(), this.isProtected);
                checkModifiers(symbolId, "public", mods.isPublic(), this.isPublic);
            }
            symbol = getJvmClassWith(symbolId, isInterface, isEnum, isInner,
                    isAnonymous, isAbstract, isFinal, isPublic, isProtected);
            symbol.setSuperTypes(irType.superTypes);
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getSymbolId());
    }

    private void checkModifiers(String irId, String label, boolean irValue, boolean srcValue) {
        if (irValue != srcValue)
            System.out.println("WARNING: IR type '" + irId +
                    "' has different access modifier compared to source element: " +
                    label + "=" + irValue + "(IR) vs. " + srcValue + "(source)");

    }

    public JType createAnonymousClass(SourceFile srcFile, List<String> superTypes,
                                      ElementWithPosition<?, ?> declaringElement,
                                      Position pos, boolean isInner) {
        return new AnonymousClass(srcFile, superTypes, this, declaringElement, pos, isInner, anonymousClassCounter++);
    }

    /**
     * Record type references found in method signatures.
     * @param retTypeUses   the type uses found in the return type (may be null)
     * @param paramTypeUses the type uses found in the parameter types
     */
    public void addSigTypeRefs(Iterable<TypeUse> retTypeUses,
                               Collection<TypeUse> paramTypeUses) {
        if (paramTypeUses != null)
            typeUses.addAll(paramTypeUses);
        if (retTypeUses != null)
            for (TypeUse retTypeUse : retTypeUses)
                if (!TypeUtils.isPrimitiveType(retTypeUse.type))
                    typeUses.add(retTypeUse);
    }

    /**
     * Compute the next counter prefix to use when naming types declared inside
     * methods.
     * @param type    the type name
     * @return        the number to use
     */
    public Integer getNextMethodTypeNumber(String type) {
        if (methodTypeCounters == null)
            methodTypeCounters = new HashMap<>();
        Integer counter = methodTypeCounters.get(type);
        Integer value = (counter == null) ? 1 : counter + 1;
        methodTypeCounters.put(type, counter);
        return value;
    }

    @Override
    public SymbolWithId generatePartialMetadata(Map<String, IRType> irTypeLookup) {
        System.out.println("Generating partial metadata for type: " + this);
        symbol = getJvmClassWith(getFullyQualifiedName(), isInterface, isEnum,
                isInner, isAnonymous, isAbstract, isFinal, isPublic, isProtected);
        return symbol;
    }

    /**
     * Update the id of the declaring element (assumed to be already resolved).
     */
    public void updateDeclaringSymbolId() {
        if (symbol == null) {
            System.out.println("ERROR: cannot update declaring symbol id in empty symbol for " + this);
            return;
        }
        String declaringSymbolId = (declaringElement != null && declaringElement.matchId != null) ? declaringElement.matchId : "";
        symbol.setDeclaringSymbolId(declaringSymbolId);
    }

    /**
     * Process initializer blocks:
     * (a) fuse static initializer blocks to a single {@code <clinit>} method and
     * (b) insert instance initialization code in all constructors (or
     *     declare default constructor if no constructors exist).
     */
    public void processInitBlocks() {
        // Non-empty class initialization code becomes a "<clinit>" method.
        if (!classInitializer.isEmpty())
            this.methods.add(classInitializer);

        // Non-empty instance initialization code must be inserted in every
        // constructor, after the call to super(). If no constructors are found,
        // an appropriate constructor is created.
        if (!instInitializer.isEmpty()) {
            boolean foundConstructors = false;
            for (JMethod jm : methods) {
                if (jm.isConstructor()) {
                    foundConstructors = true;
                    List<JMethodInvocation> invos = jm.invocations;
                    Optional<JMethodInvocation> invo = invos.stream().filter((JMethodInvocation i) -> i.explicitConstructor != null).findAny();
                    if (invo.isPresent()) {
                        // Do not insert initializer code in constructors starting with this().
                        JMethodInvocation.ExplicitConstructor ec0 = invo.get().explicitConstructor;
                        if (ec0.equals(JMethodInvocation.ExplicitConstructor.THIS))
                            break;
                    } else {
                        // If no explicit constructors exist, prefix body with a call to super().
                        invos.add(0, new JMethodInvocation(jm.srcFile, jm.pos, JInit.INIT, 0, jm, false, jm.blocks.get(0), null, JMethodInvocation.ExplicitConstructor.SUPER));
                        jm.explicitConstrEnd = new MethodBodyFrontier(0, 1, 0, 0, 0, 0, 0);
                    }
                    MethodBodyFrontier explicitConstrEnd = jm.explicitConstrEnd;
                    // If no constructor end is found, it must be reconstructed.
                    if (explicitConstrEnd == null)
                        System.out.println("ERROR: super() is missing position information: " + instInitializer + ", jm=" + jm);
                    else {
                        if (srcFile.debug)
                            System.out.println("Inserting " + instInitializer + " at " + explicitConstrEnd);
                        insertFromInit(explicitConstrEnd.allocIndex, instInitializer.allocations, jm.allocations);
                        insertFromInit(explicitConstrEnd.invoIndex, instInitializer.invocations, invos);
                        insertFromInit(explicitConstrEnd.fieldAccIndex, instInitializer.fieldAccesses, jm.fieldAccesses);
                        if (jm.methodRefs == null)
                            jm.methodRefs = new ArrayList<>();
                        insertFromInit(explicitConstrEnd.methodRefsIndex, instInitializer.methodRefs, jm.methodRefs);
                        if (jm.casts == null)
                            jm.casts = new ArrayList<>();
                        insertFromInit(explicitConstrEnd.castsIndex, instInitializer.casts, jm.casts);
                        if (jm.lambdas == null)
                            jm.lambdas = new ArrayList<>();
                        insertFromInit(explicitConstrEnd.lambdasIndex, instInitializer.lambdas, jm.lambdas);
                        insertFromInit(explicitConstrEnd.elementUsesIndex, instInitializer.elementUses, jm.elementUses);
                    }
                }
            }
            if (!foundConstructors)
                this.methods.add(instInitializer);
        }
    }

    private <E> void insertFromInit(int start, List<E> from, List<E> target) {
        if (from == null) {
            if (srcFile.debug)
                System.out.println("Added 0 elements.");
            return;
        }
        int fromSize = from.size();
        if (target == null)
            target = new ArrayList<>();
        target.addAll(start, from);
        if (srcFile.debug)
            System.out.println("Added " + fromSize + " elements.");
    }
}
