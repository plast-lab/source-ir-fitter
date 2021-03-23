package org.clyze.source.irfitter.source.model;

import java.util.ArrayList;
import java.util.HashSet;
import org.clyze.persistent.model.jvm.JvmMethod;

/**
 * A class initializer ({@code <clinit>()} in bytecode). This method can be
 * either compiler-generated (from static field initializers) or correspond to
 * <code>static { ... }</code> blocks in the source.
 */
public class JClassInit extends JMethod {
    /**
     * True if this initializer corresponds to a "static" block in the source.
     */
    public boolean source = false;

    /**
     * Create a class initializer method.
     * @param srcFile         the source file where this method may appear
     * @param declaringType   the type declaring this initializer method
     */
    public JClassInit(SourceFile srcFile, JType declaringType) {
        super(srcFile, "<clinit>", "void", new ArrayList<>(0), new HashSet<>(), null, declaringType, null);
    }

    @Override
    public JvmMethod getSymbol() {
        JvmMethod jm = super.getSymbol();
        jm.setSource(this.source);
        return jm;
    }
}
