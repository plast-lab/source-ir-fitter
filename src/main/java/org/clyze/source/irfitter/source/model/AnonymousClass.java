package org.clyze.source.irfitter.source.model;

import java.util.HashSet;
import java.util.List;
import org.clyze.persistent.model.Position;

/** An anonumous Java class. */
public class AnonymousClass extends JType {
    /** The number suffix of the type (based on visit order). */
    public final String n;

    public AnonymousClass(SourceFile srcFile, List<String> superTypes,
                          JType parentType, ElementWithPosition<?, ?> declaringElement,
                          Position pos, boolean isInner, int n) {
        // Anonymous classes cannot have annotations, so we pass an empty set.
        super(srcFile, null, superTypes, new HashSet<>(), pos, declaringElement, parentType, isInner, false,
                false, false, false, false, true, false, false, false);
        this.n = Integer.toString(n);
    }

    @Override
    public String getUnqualifiedName() {
        return n;
    }

    @Override
    public String getSimpleName() {
        if (parentType == null) {
            System.err.println("ERROR: anonymous class does not have a parent type.");
            return super.getSimpleName();
        }
        return parentType.getUnqualifiedName() + '$' + n;
    }
}
