package org.clyze.source.irfitter.source.java;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.source.model.*;

/** The AST visitor that reads Java sources. */
public class JavaVisitor extends VoidVisitorAdapter<SourceFile> {

    Scope scope = new Scope();

    @Override
    public void visit(CompilationUnit n, SourceFile sourceFile) {
        // Add default Java imports.
        sourceFile.imports.add(new Import("java.lang", true, false));
        super.visit(n, sourceFile);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration td, SourceFile sourceFile) {
        List<String> superTypes = new LinkedList<>();
        updateFromTypes(superTypes, td.getExtendedTypes());
        updateFromTypes(superTypes, td.getImplementedTypes());
        JType jt = recordType(td, sourceFile, superTypes);
        scope.enterTypeScope(jt, (jt0 -> super.visit(td, sourceFile)));
    }

    static void updateFromTypes(List<String> types, NodeList<ClassOrInterfaceType> nl) {
        if (nl != null)
            for (ClassOrInterfaceType t : nl) {
                types.add(t.getNameAsString());
            }
    }

    @Override
    public void visit(EnumDeclaration ed, SourceFile sourceFile) {
        JType jt = recordType(ed, sourceFile, Collections.singletonList("java.lang.Enum"));
        scope.enterTypeScope(jt, (jt0 -> super.visit(ed, sourceFile)));
    }

    @Override
    public void visit(AnnotationDeclaration ad, SourceFile sourceFile) {
        JType jt = recordType(ad, sourceFile, null);
        scope.enterTypeScope(jt, (jt0 -> super.visit(ad, sourceFile)));
    }

    private <T extends TypeDeclaration<?>>
    JType recordType(T decl, SourceFile srcFile, List<String> superTypes) {
        JType jt = JavaUtils.jTypeFromTypeDecl(decl, srcFile, superTypes, scope);
        if (srcFile.debug)
            System.out.println("Adding type: " + jt);
        srcFile.jTypes.add(jt);
        return jt;
    }

    @Override
    public void visit(ConstructorDeclaration cd, SourceFile sourceFile) {
        visit(cd, "void", sourceFile, (cd0 -> super.visit(cd, sourceFile)));
    }

    @Override
    public void visit(MethodDeclaration md, SourceFile sourceFile) {
        visit(md, md.getTypeAsString(), sourceFile, (md0 -> super.visit(md, sourceFile)));
    }

//    @Override
//    public void visit(FieldAccessExpr fldAccess, SourceFile sourceFile) {
//        System.out.println("Field access: " + fldAccess.getName() +
//                ", scope: " + fldAccess.getScope().toString());
//        super.visit(fldAccess, sourceFile);
//    }

    @Override
    public void visit(InitializerDeclaration init, SourceFile sourceFile) {
        scope.inInitializer = true;
        if (init.isStatic()) {
            JMethod clinit = scope.getEnclosingType().classInitializer;
            Position outerPos = JavaUtils.createPositionFromNode(init);
            long startColumn = outerPos.getStartColumn();
            clinit.pos = new Position(outerPos.getStartLine(), startColumn, startColumn + "static".length());
            clinit.outerPos = outerPos;
        } else
            System.out.println("WARNING: non-static initializer is not yet supported.");
        super.visit(init, sourceFile);
        scope.inInitializer = false;
    }

    private <T extends CallableDeclaration<?>>
    void visit(CallableDeclaration<T> md, String retType, SourceFile sourceFile,
               Consumer<CallableDeclaration<T>> methodProcessor) {
        SimpleName name = md.getName();
        List<JParameter> parameters = new LinkedList<>();
        for (Parameter param : md.getParameters())
            parameters.add(new JParameter(param.getNameAsString(), param.getTypeAsString()));
        JType jt = scope.getEnclosingType();
        JMethod jm = new JMethod(sourceFile, name.toString(), retType,
                parameters, JavaUtils.createPositionFromNode(name),
                JavaUtils.createPositionFromNode(md), jt);
        if (sourceFile.debug)
            System.out.println("Adding method: " + jm);
        jt.methods.add(jm);

        // Set current method and visit method body.
        scope.enterMethodScope(jm, (jm0 -> methodProcessor.accept(md)));
    }

    @Override
    public void visit(FieldDeclaration fd, SourceFile sourceFile) {
//        fd.getModifiers().forEach(p -> p.accept(this, sourceFile));
        for (VariableDeclarator vd : fd.getVariables()) {
            String fieldType = typeOf(vd);
            String fieldName = vd.getNameAsString();
            JType jt = scope.getEnclosingType();
            JField srcField = new JField(sourceFile, fieldType, fieldName, JavaUtils.createPositionFromNode(vd), jt);
            if (sourceFile.debug)
                System.out.println("Adding field: " + srcField);
            jt.fields.add(srcField);
            Optional<Expression> optInitializer = vd.getInitializer();
            if (optInitializer != null && optInitializer.isPresent()) {
                Expression initExpr = optInitializer.get();
                if (fd.isStatic() && fd.isFinal() && (initExpr instanceof LiteralStringValueExpr)) {
                    LiteralStringValueExpr s = (LiteralStringValueExpr) initExpr;
                    String sValue = s.getValue();
                    if (sourceFile.debug)
                        System.out.println("Java static final field points to string constant: " + sValue);
                    Position pos = JavaUtils.createPositionFromNode(s);
                    sourceFile.stringConstants.add(new JStringConstant(sourceFile, pos, srcField, sValue));
                } else
                    initExpr.accept(this, sourceFile);
            }
        }
    }

    @Override
    public void visit(PackageDeclaration pd, SourceFile sourceFile) {
        sourceFile.packageName = pd.getNameAsString();
    }

    @Override
    public void visit(final ImportDeclaration id, final SourceFile sourceFile) {
        sourceFile.imports.add(new Import(id.getNameAsString(), id.isAsterisk(), id.isStatic()));
    }

    @Override
    public void visit(IfStmt ifStmt, SourceFile sourceFile) {
        // Reimplement parent to control visit order.
        ifStmt.getCondition().accept(this, sourceFile);
        ifStmt.getThenStmt().accept(this, sourceFile);
        ifStmt.getElseStmt().ifPresent(l -> l.accept(this, sourceFile));
    }

    @Override
    public void visit(ExplicitConstructorInvocationStmt constrInvo, SourceFile sourceFile) {
        Position pos = JavaUtils.createPositionFromNode(constrInvo);
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: explicit constructors in initializers");
        else
            parentMethod.addInvocation(scope, "<init>", constrInvo.getArguments().size(), pos, sourceFile);
        super.visit(constrInvo, sourceFile);
    }

    @Override
    public void visit(ObjectCreationExpr objCExpr, SourceFile sourceFile) {
        Position pos = JavaUtils.createPositionFromNode(objCExpr);
        JMethod parentMethod = scope.getEnclosingMethod();
        Optional<NodeList<BodyDeclaration<?>>> anonymousClassBody = objCExpr.getAnonymousClassBody();
        boolean isAnonymousClassDecl = anonymousClassBody.isPresent();
        String simpleType = Utils.getSimpleType(typeOf(objCExpr));
        if (isAnonymousClassDecl) {
            List<String> superTypes = Collections.singletonList(simpleType);
            JType anonymousType = scope.getEnclosingType().createAnonymousClass(sourceFile, superTypes, scope.getEnclosingElement(), pos, false);
            if (sourceFile.debug)
                System.out.println("Adding type [anonymous]: " + anonymousType);
            sourceFile.jTypes.add(anonymousType);
            JavaVisitor jv = this;
            scope.enterTypeScope(anonymousType, jt0 -> {
                for (BodyDeclaration<?> bodyDeclaration : anonymousClassBody.get())
                    bodyDeclaration.accept(jv, sourceFile);
            });
        }
        if (parentMethod == null)
            System.out.println("TODO: allocations/invocations in object creation in initializers");
        else {
            parentMethod.addInvocation(scope, "<init>", objCExpr.getArguments().size(), pos, sourceFile);
            // If anonymous, add placeholder allocation, to be matched later.
            parentMethod.addAllocation(sourceFile, pos, isAnonymousClassDecl ? ":ANONYMOUS_CLASS:" : simpleType);
        }
    }

    @Override
    public void visit(MethodCallExpr call, SourceFile sourceFile) {
        int arity = call.getArguments().size();
        Position pos = JavaUtils.createPositionFromNode(call);
        recordInvocation(call.getName().getIdentifier(), arity, pos, sourceFile);
        super.visit(call, sourceFile);
    }

    private void recordInvocation(String name, int arity, Position pos, SourceFile sourceFile) {
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: invocations in initializers");
        else
            parentMethod.addInvocation(scope, name, arity, pos, sourceFile);
    }

    @Override
    public void visit(BlockStmt n, SourceFile sourceFile) {
        if (sourceFile.debug)
            for (Statement statement : n.getStatements())
                System.out.println("STATEMENT: " + statement.getClass().getSimpleName());
        super.visit(n, sourceFile);
    }

    static String typeOf(NodeWithType<? extends Node, ? extends com.github.javaparser.ast.type.Type> node) {
        return Utils.simplifyType(node.getTypeAsString());
    }
}