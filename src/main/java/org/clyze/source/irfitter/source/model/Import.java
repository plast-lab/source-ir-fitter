package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;

import java.util.StringJoiner;

/** An import declaration at the top of a source file. */
public class Import {
    /** The name after the {@code import} keyword. */
    public final String name;
    /** If the import ends in ".*". */
    public final boolean isAsterisk;
    /** If this is a static import. */
    public final boolean isStatic;
    /** For non-static imports, also store the "simple" type (no package prefix). */
    public final String simpleType;
    /** The source code position that will be reported for this element. */
    public final Position pos;

    /**
     * Create a new import declaration.
     * @param pos          the source position of the import
     * @param name         the name after the {@code import} keyword
     * @param isAsterisk   if the import ends in ".*"
     * @param isStatic     if this is a static import
     */
    public Import(Position pos, String name, boolean isAsterisk, boolean isStatic) {
        this.pos = pos;
        this.name = name;
        this.isAsterisk = isAsterisk;
        this.isStatic = isStatic;
        this.simpleType = isStatic ? null : Utils.getSimpleType(name);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner("/");
        if (isAsterisk)
            sj.add("*");
        if (isStatic)
            sj.add("static");
        return "import " + name + (sj.length() > 0 ? "[" + sj.toString() + "]" : "");
    }

    public String getUniqueId(SourceFile sourceFile) {
        return "import-" + pos + "@" + sourceFile.getRelativePath();
    }
}
