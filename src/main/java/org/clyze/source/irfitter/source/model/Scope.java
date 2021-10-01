package org.clyze.source.irfitter.source.model;

import java.util.*;
import java.util.function.Consumer;

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

    public JType getEnclosingType() {
        try {
            return typeScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
    }

    public JMethod getEnclosingMethod() {
        try {
            return methodScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
    }

    public ElementWithPosition<?, ?> getEnclosingElement() {
        try {
            return elementScope.peek();
        } catch (EmptyStackException ignored) {
            return null;
        }
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
}
