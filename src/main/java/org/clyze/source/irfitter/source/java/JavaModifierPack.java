package org.clyze.source.irfitter.source.java;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithAccessModifiers;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithStaticModifier;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.source.model.SourceModifierPack;

/** Class/field/method modifiers for Java sources. */
public class JavaModifierPack extends SourceModifierPack {
    private final boolean isStatic;
    private final boolean isSynchronized;

    /**
     * Generate a modifier pack from a type/field/method declaration.
     * @param sourceFile    the source file containing the declaration
     * @param decl          the source code declaration
     * @param isEnum        true if this is an enum declaration
     * @param isInterface   true if this is an interface declaration
     * @param isVarArgs     true if some parameter accepts varargs (only valid
     *                      for methods, false otherwise)
     * @param <U>           the type of the declaration
     * @param <T>           the type of the declaration again (same as U)
     */
    public <U extends BodyDeclaration<?>, T extends BodyDeclaration<U> & NodeWithAccessModifiers<U> & NodeWithStaticModifier<U>>
    JavaModifierPack(SourceFile sourceFile, T decl, boolean isEnum, boolean isInterface, boolean isVarArgs) {
        this.isStatic = decl.isStatic();
        this.isPublic = decl.isPublic();
        this.isPrivate = decl.isPrivate();
        this.isProtected = decl.isProtected();
        this.isFinal = decl.hasModifier(Modifier.Keyword.FINAL);
        this.isAbstract = decl.hasModifier(Modifier.Keyword.ABSTRACT);
        this.isNative = decl.hasModifier(Modifier.Keyword.NATIVE);
        this.isSynchronized = decl.hasModifier(Modifier.Keyword.SYNCHRONIZED);
        this.isEnum = isEnum;
        this.isInterface = isInterface;
        this.isVarArgs = isVarArgs;
        registerAnnotations(decl.getAnnotations(), sourceFile);
    }

    /**
     * Generate a modifier pack from a parameter declaration.
     * @param sourceFile    the source file containing the declaration
     * @param param         the source code parameter
     */
    public JavaModifierPack(SourceFile sourceFile, Parameter param) {
        this.isFinal = param.isFinal();
        this.isStatic = false;
        this.isSynchronized = false;
        registerAnnotations(param.getAnnotations(), sourceFile);
    }

    /**
     * Generate a modifier pack from a list of annotations.
     * @param sourceFile    the source file containing the declaration
     * @param annotations   the annotations list
     */
    public JavaModifierPack(SourceFile sourceFile, NodeList<AnnotationExpr> annotations) {
        this.isStatic = false;
        this.isSynchronized = false;
        registerAnnotations(annotations, sourceFile);
    }

    private void registerAnnotations(NodeList<AnnotationExpr> annotations, SourceFile sourceFile) {
        for (AnnotationExpr annotationExpr : annotations) {
            String annotationType = annotationExpr.getName().toString();
            registerAnnotation(sourceFile, annotationType, JavaUtils.createPositionFromNode(annotationExpr));
        }
    }

    @Override
    public boolean isStatic() {
        return this.isStatic;
    }

    @Override
    public boolean isSynchronized() {
        return this.isSynchronized;
    }
}
