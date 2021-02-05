package org.clyze.source.irfitter.source.model;

import java.util.Collection;
import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.Usage;
import org.clyze.persistent.model.UsageKind;

/**
 * A usage of a type name (such as an annotation referencing an annotation type)
 * or a constant Class object (such as {@code C.class}).
 */
public class TypeUsage implements FuzzyTypes {
    public final String type;
    public final Position position;
    public final SourceFile sourceFile;
    public String matchId = null;
    private Collection<String> cachedIds = null;

    public TypeUsage(String type, Position position, SourceFile sourceFile) {
        this.type = type;
        this.position = position;
        this.sourceFile = sourceFile;
    }

    /**
     * Build the final metadata object.
     * @return the (type) usage object
     */
    public Usage getUsage() {
        if (matchId == null)
            System.out.println("ERROR: Usage not matched: " + toString());
        return new Usage(position, sourceFile.getRelativePath(), matchId, UsageKind.TYPE);
    }

    @Override
    public SourceFile getSourceFile() {
        return sourceFile;
    }

    @Override
    public Collection<String> getIds() {
        if (cachedIds == null)
            cachedIds = resolveType(type);
        return cachedIds;
    }

    @Override
    public String toString() {
        return "TYPE-USE: " + type + "@" + sourceFile + ", " + position;
    }
}
