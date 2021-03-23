package org.clyze.source.irfitter.source.model;

import java.util.Collections;

import org.clyze.persistent.model.jvm.JvmMethod;

/**
 * A class initializer ({@code <clinit>()} in bytecode) or instance initializer.
 * This method can be
 * either compiler-generated (from static/instance field initializers) or
 * correspond to static (<code>static { ... }</code>) or instance (<code>{ ... }</code>)
 * init blocks in the source.
 */
public class JInit extends JMethod {
    /** The low-level name of class initializers. */
    public static final String CLINIT = "<clinit>";
    /**
     * A name for the pseudo-method that represents instance initializer blocks
     * and instance field initializers.
     */
    public static final String INIT_BLOCK = "<init>-block";

    /** True if this initializer corresponds to an init block in the source. */
    public boolean source = false;
    /** If true, this is a static init block. */
    public final boolean isStatic;

    /**
     * Create a class initializer method.
     * @param srcFile         the source file where this method may appear
     * @param declaringType   the type declaring this initializer method
     * @param name            the low-level name of the generated method
     * @param isStatic        true if this is a static init block
     */
    private JInit(SourceFile srcFile, JType declaringType, String name, boolean isStatic) {
        super(srcFile, name, "void", Collections.emptyList(), Collections.emptySet(), null, declaringType, null, false);
        this.isStatic = isStatic;
    }

    public static JInit createClinit(SourceFile srcFile, JType declaringType) {
        return new JInit(srcFile, declaringType, CLINIT, true);
    }

    public static JInit createInitBlock(SourceFile srcFile, JType declaringType) {
        return new JInit(srcFile, declaringType, INIT_BLOCK, false);
    }

    /**
     * Set the "source" flag on the matched symbol. This will fail if no
     * matching symbol has already been configured.
     * @param source   true if the symbol is to be characterized as "source",
     *                 false otherwise
     */
    public void setSource(boolean source) {
        this.source = source;
    }

    @Override
    public JvmMethod getSymbol() {
        JvmMethod jm = super.getSymbol();
        jm.setSource(this.source);
        return jm;
    }

    @Override
    public boolean isSpecialInitializer() {
        return true;
    }

    /**
     * Helper method to find special initializer methods.
     * @param name   a method name
     * @return       true if the method name is a class/instance initializer
     */
    public static boolean isInitName(String name) {
        return CLINIT.equals(name) || INIT_BLOCK.equals(name);
    }
}
