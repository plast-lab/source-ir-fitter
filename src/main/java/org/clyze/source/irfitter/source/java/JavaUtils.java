package org.clyze.source.irfitter.source.java;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import java.util.*;
import org.clyze.source.irfitter.source.model.JType;
import org.clyze.source.irfitter.source.model.Scope;
import org.clyze.source.irfitter.source.model.SourceFile;

/** A collection of utilities used during parsing of Java sources. */
public class JavaUtils {
    public static TypeDeclaration<?> getEnclosingType(Node node) {
        if (node == null)
            return null;
        Optional<Node> optParent = node.getParentNode();
        if (optParent.isPresent()) {
            Node parent = optParent.get();
            if (parent instanceof TypeDeclaration)
                return (TypeDeclaration<?>) parent;
            else
                return getEnclosingType(parent);
        }
        return null;
    }

    public static <T extends TypeDeclaration<?>>
    JType jTypeFromTypeDecl(T decl, SourceFile sourceFile, List<String> superTypes, Scope scope) {
        SimpleName name = decl.getName();
        JType parent = scope.getEnclosingType();
        boolean isStatic = decl.isStatic();
        boolean isPublic = decl.isPublic();
        boolean isPrivate = decl.isPrivate();
        boolean isProtected = decl.isProtected();
        boolean isFinal = decl.hasModifier(Modifier.Keyword.FINAL);
        boolean isAbstract = decl.hasModifier(Modifier.Keyword.ABSTRACT);
        boolean isInner = parent != null && !isStatic;
        return new JType(sourceFile, name.toString(), superTypes, parent,
                createPositionFromNode(name), scope.getEnclosingElement(),
                isInner, isPublic, isPrivate, isProtected, isAbstract, isFinal, false);
    }

    public static org.clyze.persistent.model.Position createPositionFromNode(NodeWithRange<?> sn) {
        int startLine, startColumn, endLine, endColumn;
        Optional<Position> begin = sn.getBegin();
        if (begin.isPresent()) {
            Position pos = begin.get();
            startLine = pos.line;
            startColumn = pos.column;
        } else {
            startLine = -1;
            startColumn = -1;
        }
        Optional<Position> end = sn.getEnd();
        if (end.isPresent()) {
            Position pos = end.get();
            endLine = pos.line;
            endColumn = pos.column + 1;
        } else {
            endLine = -1;
            endColumn = -1;
        }
        return new org.clyze.persistent.model.Position(startLine, endLine, startColumn, endColumn);
    }
}
