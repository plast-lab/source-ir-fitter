package org.clyze.source.irfitter.source.model;

import java.util.*;

import org.clyze.persistent.model.jvm.JvmClass;
import org.clyze.source.irfitter.base.ModifierPack;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.persistent.model.Position;

/**
 * A reference type (such as a class, enum, or interface) in the source code.
 */
public class JType extends NamedElementWithPosition<IRType, JvmClass> {
    private final String name;
    public final JType parentType;
    public final NamedElementWithPosition<?, ?> declaringElement;
    public final List<String> superTypes;
    public final Set<String> annotationTypes;
    private final boolean isPublic;
    private final boolean isPrivate;
    private final boolean isProtected;
    private final boolean isAbstract;
    private final boolean isFinal;
    public final boolean isAnonymous;
    private final boolean isInner;
    public final JMethod classInitializer;
    public final List<JField> fields = new LinkedList<>();
    public final List<JMethod> methods = new LinkedList<>();
    public final List<TypeUsage> typeUsages = new LinkedList<>();
    private int anonymousClassCounter = 1;

    public JType(SourceFile srcFile, String name, List<String> superTypes,
                 Set<String> annotationTypes, Position pos,
                 NamedElementWithPosition<?, ?> declaringElement, JType parentType,
                 boolean isInner, boolean isPublic, boolean isPrivate,
                 boolean isProtected, boolean isAbstract, boolean isFinal,
                 boolean isAnonymous) {
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
        // Add <clinit>() method.
        this.classInitializer = new JMethod(srcFile, "<clinit>", "void", new ArrayList<>(0), new HashSet<>(), null, this, null);
        this.methods.add(classInitializer);
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
        return "type{name=" + getUnqualifiedName() + " (" + getSimpleName() + "), " + fields.size() + " fields, " + methods.size() + " methods}" + getLocation();
    }

    @Override
    public void initSymbolFromIRElement(IRType irType) {
        if (symbol == null) {
            String doopId = irType.getId();
            ModifierPack mods = irType.mp;
            boolean isAbstract = mods.isAbstract();
            boolean isFinal = mods.isFinal();
            boolean isProtected = mods.isProtected();
            boolean isPublic = mods.isPublic();
            boolean isStatic = parentType != null && !isInner;
            boolean isInterface = mods.isInterface();
            boolean isEnum = mods.isEnum();
            if (srcFile.debug) {
                System.out.println("Creating Class with doopId = " + doopId);
                checkModifiers(doopId, "abstract", mods.isAbstract(), this.isAbstract);
                checkModifiers(doopId, "final", mods.isFinal(), this.isFinal);
                checkModifiers(doopId, "private", mods.isPrivate(), this.isPrivate);
                checkModifiers(doopId, "protected", mods.isProtected(), this.isProtected);
                checkModifiers(doopId, "public", mods.isPublic(), this.isPublic);
            }
            symbol = new JvmClass(pos, srcFile.getRelativePath(), true, getSimpleName(),
                    srcFile.packageName, doopId, isInterface, isEnum, isStatic,
                    isInner, isAnonymous, isAbstract, isFinal, isPublic, isProtected, isPrivate);
            symbol.setSuperTypes(superTypes);
            symbol.setAnnotations(annotationTypes);
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
                                      NamedElementWithPosition<?, ?> declaringElement,
                                      Position pos, boolean isInner) {
        return new AnonymousClass(srcFile, superTypes, this, declaringElement, pos, isInner, anonymousClassCounter++);
    }

    /**
     * Update the id of the declaring element (assumed to be already resolved).
     */
    public void updateDeclaringSymbolId() {
        if (symbol == null) {
            System.out.println("ERROR: cannot update declaring symbol id in empty symbol for " + toString());
            return;
        }
        String declaringSymbolId = (declaringElement != null && declaringElement.matchId != null) ? declaringElement.matchId : "";
        symbol.setDeclaringSymbolId(declaringSymbolId);
    }
}
