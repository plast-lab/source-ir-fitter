package org.clyze.source.irfitter.ir.model;

import java.util.Collection;
import java.util.List;

public abstract class IRElement {
    final String id;
    /**
     * If true, this IR element has already matched a source element (and may
     * thus be ignored on subsequent checks).
     */
    public boolean matched = false;

    public IRElement(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    /**
     * This method should be overridden by IR elements that may reference types
     * and thus should be able to report all fully-qualified names used.
     * @param target  a collection of IR type names to use for writing
     */
    public void addReferencedTypesTo(Collection<String> target) { }

    /**
     * Helper for reusing type references from element lists.
     * @param target    the target set to populate
     * @param elements  the elements that may contain type references
     */
    protected static void addTypeRefs(Collection<String> target, List<? extends IRElement> elements) {
        for (IRElement element : elements)
            element.addReferencedTypesTo(target);
    }
}
