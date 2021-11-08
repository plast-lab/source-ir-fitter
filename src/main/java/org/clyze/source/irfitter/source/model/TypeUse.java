package org.clyze.source.irfitter.source.model;

import java.util.Collection;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.UsageKind;

/**
 * A reference to a type name (such as an annotation referencing an annotation type)
 * or a constant Class object (such as {@code C.class}).
 */
public class TypeUse extends ElementUse implements FuzzyTypes {
    public final String type;
    private Collection<String> cachedIds = null;

    public TypeUse(String type, Position position, SourceFile sourceFile) {
        super(sourceFile, position, UsageKind.TYPE);
        this.type = type;
    }

    @Override
    public SourceFile getSourceFile() {
        return sourceFile;
    }

    /**
     * Return the possible ids that this element can match.
     * @return              a collection of ids
     */
    public Collection<String> getIds() {
        if (cachedIds == null)
            cachedIds = resolveType(type);
        return cachedIds;
    }

    @Override
    public String toString() {
        return "type-use:: " + type + "@" + sourceFile.getRelativePath() + ", " + position;
    }
}
