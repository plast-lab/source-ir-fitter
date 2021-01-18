package org.clyze.source.irfitter.source.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import org.clyze.source.irfitter.base.ModifierPack;
import org.clyze.source.irfitter.ir.model.IRType;
import org.clyze.persistent.model.Class;
import org.clyze.persistent.model.Position;

/**
 * A reference type (such as a class, enum, or interface) in the source code.
 */
public class JType extends NamedElementWithPosition<IRType> {
    private final String name;
    public final JType parentType;
    public final NamedElementWithPosition<?> declaringElement;
    public final List<String> superTypes;
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
    private int anonymousClassCounter = 1;

    public JType(SourceFile srcFile, String name, List<String> superTypes,
                 JType parentType, Position pos, NamedElementWithPosition<?> declaringElement, boolean isInner, boolean isPublic,
                 boolean isPrivate, boolean isProtected, boolean isAbstract,
                 boolean isFinal, boolean isAnonymous) {
        super(srcFile, pos);
        this.name = name;
        this.superTypes = superTypes;
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
        this.classInitializer = new JMethod(srcFile, "<clinit>", "void", new ArrayList<>(0), null, null, this);
        this.methods.add(classInitializer);
    }

    public String getName() {
        return this.name;
    }

    public String getFullyQualifiedName() {
        return getFullyQualifiedName(srcFile.packageName);
    }

    public String getFullyQualifiedName(String packageName) {
        Stack<String> stack = new Stack<>();
        stack.push(getName());
        JType pn = parentType;
        while (pn != null) {
            stack.push("$");
            stack.push(pn.getName());
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
        return "type{name=" + getName() + ", " + fields.size() + " fields, " + methods.size() + " methods}@" + pos;
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
            Class c = new Class(pos, srcFile.getRelativePath(), getName(),
                    srcFile.packageName, doopId, isInterface, isEnum, isStatic,
                    isInner, isAnonymous, isAbstract, isFinal, isPublic, isProtected, isPrivate);
            c.setSuperTypes(superTypes);
            symbol = c;
        } else
            System.out.println("WARNING: symbol already initialized: " + symbol.getDoopId());
    }

    private void checkModifiers(String irId, String label, boolean irValue, boolean srcValue) {
        if (irValue != srcValue)
            System.out.println("WARNING: IR type '" + irId +
                    "' has different access modifier compared to source element: " +
                    label + "=" + irValue + "(IR) vs. " + srcValue + "(source)");

    }

    public JType createAnonymousClass(SourceFile srcFile, List<String> superTypes,
                                      NamedElementWithPosition<?> declaringElement,
                                      Position pos, boolean isInner) {
        return new AnonymousClass(srcFile, superTypes, this, declaringElement, pos, isInner, anonymousClassCounter++);
    }

    /**
     * Update the id of the declaring element (assumed to be already resolved).
     */
    public void updateDeclaringSymbolId() {
        if (declaringElement != null && declaringElement.matchId != null)
            ((Class)symbol).setDeclaringSymbolDoopId(declaringElement.matchId);
    }
}
