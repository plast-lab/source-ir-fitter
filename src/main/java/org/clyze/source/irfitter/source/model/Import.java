package org.clyze.source.irfitter.source.model;

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

    /**
     * Create a new import declaration.
     * @param name         the name after the {@code import} keyword
     * @param isAsterisk   if the import ends in ".*"
     * @param isStatic     if this is a static import
     */
    public Import(String name, boolean isAsterisk, boolean isStatic) {
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
}
