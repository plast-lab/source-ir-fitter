package org.clyze.source.irfitter.source.java;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithAccessModifiers;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithStaticModifier;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.source.model.*;

/** The AST visitor that reads Java sources. */
public class JavaVisitor extends VoidVisitorAdapter<SourceFile> {

    /** The scoping object. */
    private final Scope scope = new Scope();

    @Override
    public void visit(CompilationUnit n, SourceFile sourceFile) {
        // Add default Java imports.
        sourceFile.imports.add(new Import(null, "java.lang", true, false));
        super.visit(n, sourceFile);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration td, SourceFile sourceFile) {
        List<TypeUsage> superTypeUsages = new LinkedList<>();
        updateFromTypes(superTypeUsages, td.getExtendedTypes(), sourceFile);
        updateFromTypes(superTypeUsages, td.getImplementedTypes(), sourceFile);
        List<String> superTypes = superTypeUsages.stream().map(tu -> tu.type).collect(Collectors.toList());
        JType jt = recordType(td, sourceFile, superTypes, false, td.isInterface());
        jt.typeUsages.addAll(superTypeUsages);
        scope.enterTypeScope(jt, (jt0 -> super.visit(td, sourceFile)));
    }

    static void updateFromTypes(List<TypeUsage> types, NodeList<ClassOrInterfaceType> nl,
                                SourceFile sourceFile) {
        if (nl != null)
            for (ClassOrInterfaceType t : nl) {
                SimpleName name = t.getName();
                Position pos = JavaUtils.createPositionFromNode(name);
                types.add(new TypeUsage(name.asString(), pos, sourceFile));
            }
    }

    @Override
    public void visit(EnumDeclaration ed, SourceFile sourceFile) {
        JType jt = recordType(ed, sourceFile, Collections.singletonList("java.lang.Enum"), true, false);
        scope.enterTypeScope(jt, (jt0 -> super.visit(ed, sourceFile)));
    }

    @Override
    public void visit(AnnotationDeclaration ad, SourceFile sourceFile) {
        JType jt = recordType(ad, sourceFile, null, false, true);
        scope.enterTypeScope(jt, (jt0 -> super.visit(ad, sourceFile)));
    }

    private <U extends TypeDeclaration<?>, T extends TypeDeclaration<U> & NodeWithAccessModifiers<U> & NodeWithStaticModifier<U>>
    JType recordType(T decl, SourceFile srcFile, List<String> superTypes,
                     boolean isEnum, boolean isInterface) {
        JType jt = jTypeFromTypeDecl(decl, isEnum, isInterface, srcFile, superTypes, scope);
        if (srcFile.debug)
            System.out.println("Adding type: " + jt);
        srcFile.jTypes.add(jt);
        return jt;
    }

    @Override
    public void visit(ConstructorDeclaration cd, SourceFile sourceFile) {
        visit(cd, "void", null, sourceFile, (cd0 -> super.visit(cd, sourceFile)));
    }

    @Override
    public void visit(MethodDeclaration md, SourceFile sourceFile) {
        String retType = md.getTypeAsString();
        Collection<TypeUsage> retTypeUsages = new HashSet<>();
        addTypeUsagesFromType(retTypeUsages, md.getType(), sourceFile);
        visit(md, retType, retTypeUsages, sourceFile, (md0 -> super.visit(md, sourceFile)));
    }

    @Override
    public void visit(VariableDeclarationExpr vDecl, SourceFile sourceFile) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.err.println("ERROR: variable declaration outside type: " + vDecl.getRange());
            return;
        }
        jt.typeUsages.addAll((new JavaModifierPack(sourceFile, vDecl.getAnnotations())).getAnnotationUses());
        for (VariableDeclarator variable : vDecl.getVariables()) {
            addTypeUsagesFromType(jt.typeUsages, variable.getType(), sourceFile);
            Optional<Expression> initializer = variable.getInitializer();
            initializer.ifPresent(expression -> expression.accept(this, sourceFile));
        }
    }

    /**
     * Helper method that recursively traverses a type and records all type usages.
     * @param target        the collection to populate
     * @param type          the type to traverse
     * @param sourceFile    the source file object
     */
    private void addTypeUsagesFromType(Collection<TypeUsage> target, Type type,
                                       SourceFile sourceFile) {
        if (type.isPrimitiveType() || type.isVoidType())
            return;

        // Add all annotation type usages.
        target.addAll((new JavaModifierPack(sourceFile, type.getAnnotations())).getAnnotationUses());

        // Nothing else to do for unknown types.
        if (type.isUnknownType())
            return;

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrIntf = ((ClassOrInterfaceType) type);
            SimpleName name = classOrIntf.getName();
            TypeUsage tu = new TypeUsage(name.asString(), JavaUtils.createPositionFromNode(name), sourceFile);
            target.add(tu);
            if (sourceFile.debug)
                System.out.println("Added type usage: " + tu);
            classOrIntf.getTypeArguments().ifPresent(nl -> {
                for (Type typeArg : nl)
                    addTypeUsagesFromType(target, typeArg, sourceFile);
            });
        } else if (type.isArrayType())
            addTypeUsagesFromType(target, ((ArrayType) type).getComponentType(), sourceFile);
        else if (type.isIntersectionType())
            System.err.println("WARNING: intersection type usages are not yet recorded.");
        else if (type.isTypeParameter())
            System.err.println("WARNING: type parameter usages are not yet recorded.");
        else if (type.isUnionType()) {
            UnionType uType = (UnionType) type;
            uType.getElements().ifNonEmpty(nl -> nl.forEach(refType -> addTypeUsagesFromType(target, refType, sourceFile)));
        } else if (type.isVarType())
            System.err.println("WARNING: var-type usages are not yet recorded.");
        else if (type.isWildcardType()) {
            WildcardType wType = ((WildcardType)type);
            wType.getExtendedType().ifPresent(refType -> addTypeUsagesFromType(target, refType, sourceFile));
            wType.getSuperType().ifPresent(refType -> addTypeUsagesFromType(target, refType, sourceFile));
        } else
            System.err.println("WARNING: unknown type usage for element: " + type.getClass().getSimpleName());
    }

//    @Override
//    public void visit(FieldAccessExpr fldAccess, SourceFile sourceFile) {
//        System.out.println("Field access: " + fldAccess.getName() +
//                ", scope: " + fldAccess.getScope().toString());
//        super.visit(fldAccess, sourceFile);
//    }

    @Override
    public void visit(InitializerDeclaration init, SourceFile sourceFile) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.out.println("ERROR: found initializer declaration outside type: " + init);
            return;
        }
        jt.typeUsages.addAll(new JavaModifierPack(sourceFile, init.getAnnotations()).getAnnotationUses());

        JInit initMethod = init.isStatic() ? jt.classInitializer : jt.initBlock;
        initMethod.setSource(true);
        Position outerPos = JavaUtils.createPositionFromNode(init);
        long startColumn = outerPos.getStartColumn();
        initMethod.pos = new Position(outerPos.getStartLine(), startColumn, startColumn + "static".length());
        initMethod.outerPos = outerPos;
        scope.enterInitializerScope(initMethod, (cl -> init.getBody().accept(this, sourceFile)));
    }

    private <T extends CallableDeclaration<?>>
    void visit(CallableDeclaration<T> md, String retType, Collection<TypeUsage> retTypeUsages,
               SourceFile sourceFile, Consumer<CallableDeclaration<T>> methodProcessor) {
        SimpleName name = md.getName();
        List<JParameter> parameters = new LinkedList<>();
        Collection<TypeUsage> paramTypeUsages = new HashSet<>();
        boolean isVarArgs = false;
        for (Parameter param : md.getParameters()) {
            if (param.isVarArgs())
                isVarArgs = true;
            Type pType = param.getType();
            addTypeUsagesFromType(paramTypeUsages, pType, sourceFile);
            Position paramPos = JavaUtils.createPositionFromNode(param);
            parameters.add(new JParameter(sourceFile, paramPos, param.getNameAsString(), pType.asString()));
        }
        JType jt = scope.getEnclosingType();
        JavaModifierPack mp = new JavaModifierPack(sourceFile, md, false, false, isVarArgs);
        JMethod jm = new JMethod(sourceFile, name.toString(), retType, parameters,
                mp.getAnnotations(), JavaUtils.createPositionFromNode(md), jt,
                JavaUtils.createPositionFromNode(name), isVarArgs);
        jt.typeUsages.addAll(mp.getAnnotationUses());
        Utils.addSigTypeRefs(jt, retTypeUsages, paramTypeUsages);
        for (ReferenceType thrownException : md.getThrownExceptions())
            addTypeUsagesFromType(jt.typeUsages, thrownException, sourceFile);
        if (sourceFile.debug)
            System.out.println("Adding method: " + jm);
        jt.methods.add(jm);

        // Set current method and visit method body.
        scope.enterMethodScope(jm, (jm0 -> methodProcessor.accept(md)));
    }

    @Override
    public void visit(FieldDeclaration fd, SourceFile sourceFile) {
        for (VariableDeclarator vd : fd.getVariables()) {
            Collection<TypeUsage> fieldTypeUsages = new ArrayList<>();
            addTypeUsagesFromType(fieldTypeUsages, vd.getType(), sourceFile);
            JType jt = scope.getEnclosingType();
            jt.typeUsages.addAll(fieldTypeUsages);

            JavaModifierPack mp = new JavaModifierPack(sourceFile, fd, false, false, false);
            JField srcField = new JField(sourceFile, typeOf(vd), vd.getNameAsString(),
                    mp.getAnnotations(), JavaUtils.createPositionFromNode(vd), jt);
            jt.typeUsages.addAll(mp.getAnnotationUses());
            if (sourceFile.debug)
                System.out.println("Adding field: " + srcField);
            jt.fields.add(srcField);
            Optional<Expression> optInitializer = vd.getInitializer();
            if (optInitializer != null && optInitializer.isPresent()) {
                final boolean isStaticField = mp.isStatic();
                Expression initExpr = optInitializer.get();
                if (isStaticField && fd.isFinal() && (initExpr instanceof StringLiteralExpr)) {
                    StringLiteralExpr s = (StringLiteralExpr) initExpr;
                    String sValue = s.getValue();
                    if (sourceFile.debug)
                        System.out.println("Java static final field points to string constant: " + sValue);
                    Position pos = JavaUtils.createPositionFromNode(s);
                    sourceFile.stringConstants.add(new JStringConstant<>(sourceFile, pos, srcField, sValue));
                } else {
                    JMethod initBlock = isStaticField ? jt.classInitializer : jt.initBlock;
                    scope.enterMethodScope(initBlock, init -> initExpr.accept(this, sourceFile));
                }
            }
        }
    }

    @Override
    public void visit(PackageDeclaration pd, SourceFile sourceFile) {
        sourceFile.packageName = pd.getNameAsString();
    }

    @Override
    public void visit(final ImportDeclaration id, final SourceFile sourceFile) {
        Position pos = JavaUtils.createPositionFromNode(id);
        sourceFile.imports.add(new Import(pos, id.getNameAsString(), id.isAsterisk(), id.isStatic()));
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
        Collection<TypeUsage> typeUsages = new ArrayList<>();
        addTypeUsagesFromType(typeUsages, objCExpr.getType(), sourceFile);
        JType enclosingType = scope.getEnclosingType();
        enclosingType.typeUsages.addAll(typeUsages);
        String simpleType = Utils.getSimpleType(typeOf(objCExpr));
        if (isAnonymousClassDecl) {
            List<String> superTypes = Collections.singletonList(simpleType);
            JType anonymousType = enclosingType.createAnonymousClass(sourceFile, superTypes, scope.getEnclosingElement(), pos, false);
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
            System.out.println("ERROR: allocations/invocations in object creation in initializers");
        else {
            parentMethod.addInvocation(scope, "<init>", objCExpr.getArguments().size(), pos, sourceFile);
            // If anonymous, add placeholder allocation, to be matched later.
            parentMethod.addAllocation(sourceFile, pos, isAnonymousClassDecl ? ":ANONYMOUS_CLASS:" : simpleType);
        }
        objCExpr.getArguments().forEach(p -> p.accept(this, sourceFile));
    }

    @Override
    public void visit(MethodReferenceExpr mRef, SourceFile sourceFile) {
        String id = mRef.getIdentifier();
        Expression baseExpr = mRef.getScope();
        if (id.equals("new"))
            id = "<init>";
        if (baseExpr instanceof TypeExpr) {
            // TypeExpr tScope = (TypeExpr) baseExpr;
            Position pos = JavaUtils.createPositionFromNode(mRef);
            JType jt = scope.getEnclosingType();
            if (jt == null)
                System.out.println("ERROR: found method reference outside type definition");
            else {
                JMethod jm = scope.getEnclosingMethod();
                if (jm == null) {
                    System.out.println("ERROR: found method reference outside method definition");
                } else
                    jm.addMethodRef(new JMethodRef(sourceFile, pos, id));
            }
        } else
            System.out.println("WARNING: could not handle method reference: " + mRef);
        baseExpr.accept(this, sourceFile);
        mRef.getTypeArguments().ifPresent(l -> l.forEach(v -> v.accept(this, sourceFile)));
    }

    @Override
    public void visit(ArrayCreationExpr arrayCreationExpr, SourceFile sourceFile) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.out.println("ERROR: array creation found outside type");
            return;
        }

        for (ArrayCreationLevel level : arrayCreationExpr.getLevels())
            jt.typeUsages.addAll((new JavaModifierPack(sourceFile, level.getAnnotations())).getAnnotationUses());

        JMethod parentMethod = scope.getEnclosingMethod();
        Position pos = JavaUtils.createPositionFromNode(arrayCreationExpr);
        if (parentMethod == null)
            System.out.println("TODO: array creation in initializers: " + sourceFile + ": " + pos);
        else
            parentMethod.addAllocation(sourceFile, pos, arrayCreationExpr.createdType().asString());

        arrayCreationExpr.getInitializer().ifPresent(initializer -> initializer.accept(this, sourceFile));
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
            System.out.println("TODO: invocations in initializers: " + sourceFile + ": " + pos);
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

    @Override
    public void visit(ClassExpr classExpr, SourceFile sourceFile) {
        Position pos = JavaUtils.createPositionFromNode(classExpr);
        TypeUsage tu = new TypeUsage(classExpr.getTypeAsString(), pos, sourceFile);
        if (sourceFile.debug)
            System.out.println("Registering type usage: " + tu);
        scope.getEnclosingType().typeUsages.add(tu);
    }

    @Override
    public void visit(final CatchClause cc, SourceFile sourceFile) {
        visit(cc.getParameter(), sourceFile);
        visit(cc.getBody(), sourceFile);
    }

    @Override
    public void visit(Parameter param, SourceFile sourceFile) {
        JType jt = scope.getEnclosingType();
        if (jt != null) {
            addTypeUsagesFromType(jt.typeUsages, param.getType(), sourceFile);
            jt.typeUsages.addAll(new JavaModifierPack(sourceFile, param).getAnnotationUses());
        } else
            System.err.println("WARNING: found parameter outside type: " + param);
    }

    @Override
    public void visit(CastExpr castExpr, SourceFile sourceFile) {
        JType jt = scope.getEnclosingType();
        if (jt != null)
            addTypeUsagesFromType(jt.typeUsages, castExpr.getType(), sourceFile);
        else
            System.err.println("WARNING: found cast outside type: " + castExpr);
        super.visit(castExpr, sourceFile);
    }

    @Override
    public void visit(AssignExpr assignExpr, SourceFile sourceFile) {
        Expression target = assignExpr.getTarget();
        if (target instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAcc = (FieldAccessExpr) target;
            // Any field accesses in the "scope" should be reads.
            fieldAcc.getScope().accept(this, sourceFile);
            // Record the final field as a "write".
            visitFieldAccess(fieldAcc, false, sourceFile);
        } else
            target.accept(this, sourceFile);
        assignExpr.getValue().accept(this, sourceFile);
    }

    private void visitFieldAccess(FieldAccessExpr fieldAccess, boolean read, SourceFile sourceFile) {
        SimpleName name = fieldAccess.getName();
        String fieldName = name.asString();
        Position pos = JavaUtils.createPositionFromNode(name);
        if (sourceFile.debug)
            System.out.println("Field access [" + (read ? "read" : "write") + "]: " + fieldName + "@" + sourceFile + ":" + pos);
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: field access outside method: " + fieldAccess + ": " + sourceFile);
        else
            parentMethod.fieldAccesses.add(new JFieldAccess(sourceFile, pos, read, fieldName));
        JType jt = scope.getEnclosingType();
        if (jt == null)
            System.out.println("ERROR: field access outside type: " + fieldAccess + ": " + sourceFile);
        else
            fieldAccess.getTypeArguments().ifPresent(nl -> {
                for (Type typeArg : nl)
                    addTypeUsagesFromType(jt.typeUsages, typeArg, sourceFile);
            });
    }

    @Override
    public void visit(FieldAccessExpr fieldAccess, SourceFile sourceFile) {
        // This method is assumed to only find reads and be overridden when visiting field writes.
        visitFieldAccess(fieldAccess, true, sourceFile);
    }

    private static String typeOf(NodeWithType<? extends Node, ? extends com.github.javaparser.ast.type.Type> node) {
        return Utils.simplifyType(node.getType().asString());
    }

    private static <U extends TypeDeclaration<?>, T extends TypeDeclaration<U> & NodeWithAccessModifiers<U> & NodeWithStaticModifier<U>>
    JType jTypeFromTypeDecl(T decl, boolean isEnum, boolean isInterface,
                            SourceFile sourceFile, List<String> superTypes, Scope scope) {
        SimpleName name = decl.getName();
        JType parent = scope.getEnclosingType();
        JavaModifierPack mp = new JavaModifierPack(sourceFile, decl, isEnum, isInterface, false);
        boolean isInner = parent != null && !mp.isStatic();
        JType jt = new JType(sourceFile, name.toString(), superTypes, mp.getAnnotations(),
                JavaUtils.createPositionFromNode(name), scope.getEnclosingElement(),
                parent, isInner, mp.isPublic(), mp.isPrivate(), mp.isProtected(),
                mp.isAbstract(), mp.isFinal(), false);
        jt.typeUsages.addAll(mp.getAnnotationUses());
        return jt;
    }
}
