package org.clyze.source.irfitter.source.model;

import java.util.List;
import org.clyze.persistent.model.Position;

/** An anonumous Java class. */
public class AnonymousClass extends JType {
    /** The number suffix of the type (based on visit order). */
    public final String n;

    public AnonymousClass(SourceFile srcFile, List<String> superTypes,
                          JType parent, NamedElementWithPosition<?> declaringElement,
                          Position pos, boolean isInner, int n) {
        super(srcFile, null, superTypes, parent, pos, declaringElement, isInner, false,
                false, false, false, false, true);
        this.n = Integer.toString(n);
    }

    @Override
    public String getName() {
        return n;
    }
}
