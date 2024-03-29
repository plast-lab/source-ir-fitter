package org.clyze.source.irfitter.source.model;

import java.util.*;
import java.util.function.Consumer;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AccessType;

/** Nested scopes functionality. */
public class Scope {
    /** The stack of enclosing types, top is closer enclosing type. */
    protected final Stack<JType> typeScope = new Stack<>();
    /** The stack of enclosing methods, top is closer enclosing method. */
    protected final Stack<JMethod> methodScope = new Stack<>();
    /** The stack of enclosing elements. */
    protected final Stack<ElementWithPosition<?, ?>> elementScope = new Stack<>();
    /** True if the current scope is inside an initializer. */
    public boolean inInitializer = false;
    /** The block scope (used in Groovy mode). */
    private final Stack<JBlock> blocks = new Stack<>();

    public JType getEnclosingType() {
        try {
            return typeScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
    }

    /**
     * Returns the inner-most outer method at this point.
     * @return   a method or null on failure
     */
    public JMethod getEnclosingMethod() {
        try {
            return methodScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
    }

    /**
     * Returns the inner-most outer block at this point.
     * @return   a block or null on failure
     */
    public JBlock getEnclosingBlock() {
        return blocks.isEmpty() ? null : blocks.peek();
    }

    /**
     * Returns the inner-most element at this point.
     * @return   an element or null on failure
     */
    public ElementWithPosition<?, ?> getEnclosingElement() {
        try {
            return elementScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
    }

    /**
     * Returns the method that directly contains the current block.
     * @return   a method or null on failure
     */
    public JMethod getDirectEnclosingMethod() {
        ElementWithPosition<?, ?> enclosingElement = getEnclosingElement();
        return enclosingElement instanceof JMethod ? (JMethod) enclosingElement : null;
    }

    public void enterTypeScope(JType jt, Consumer<JType> scopeProcessor) {
        typeScope.push(jt);
        enterElementScope(jt, scopeProcessor);
        typeScope.pop();
    }

    public void enterMethodScope(JMethod jm, Consumer<JMethod> scopeProcessor) {
        methodScope.push(jm);
        enterElementScope(jm, scopeProcessor);
        methodScope.pop();
    }

    public void enterInitializerScope(JInit initializer, Consumer<JMethod> scopeProcessor) {
        this.inInitializer = true;
        enterMethodScope(initializer, scopeProcessor);
        this.inInitializer = false;
    }

    private <T extends ElementWithPosition<?, ?>>
    void enterElementScope(T elem, Consumer<T> elemProcessor) {
        elementScope.push(elem);
        elemProcessor.accept(elem);
        elementScope.pop();
    }

    public void enterBlockScope(Position blockPos, JType enclosingType, Consumer<JBlock> scopeProcessor) {
        JBlock parentBlock = blocks.isEmpty() ? null : blocks.peek();
        JBlock block = new JBlock(blockPos, parentBlock, enclosingType);
        blocks.push(block);
        scopeProcessor.accept(block);
        blocks.pop();
    }

    /**
     * Registers an access to a field in the current scope.
     * @param astFieldAccess    the AST node to use for debugging messages
     * @param staticTypeName    the name of the type declaring the (static) field
     * @param fieldName         the name of the field
     * @param pos               the source position of the access
     * @param sourceFile        the source file
     * @param accType           the access type
     * @param target            the target field
     * @param debug             if true, print debugging information
     */
    public void registerFieldAccess(Object astFieldAccess, String staticTypeName,
                                    String fieldName, Position pos, SourceFile sourceFile,
                                    AccessType accType, JField target, boolean debug) {
        if (debug)
            System.out.println("Field access [" + (accType.name()) + "]: " + fieldName +
                    "@" + sourceFile + ":" + pos + ", astFieldAccess=" + astFieldAccess);
        JMethod parentMethod = getDirectEnclosingMethod();
        JFieldAccess fieldAccess = new JFieldAccess(sourceFile, pos, accType, staticTypeName, fieldName, target);
        if (parentMethod == null)
            sourceFile.fieldAccesses.add(fieldAccess);
        else
            parentMethod.fieldAccesses.add(fieldAccess);
    }

    /**
     * Registers an access to a variable in the current scope.
     * @param localVar       the local variable representation
     * @param pos            the source position of the access
     * @param accessType     the access type
     * @param parentNode     the parent AST node (to use for debugging messages)
     */
    public void registerVarAccess(JVariable localVar, Position pos, AccessType accessType, Object parentNode) {
        JMethod enclosingMethod = getEnclosingMethod();
        if (enclosingMethod != null)
            enclosingMethod.addVarAccess(pos, accessType.kind, localVar);
        else
            System.err.println("WARNING: found variable use outside method: " + parentNode);
    }
}
