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
import com.github.javaparser.printer.YamlPrinter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.source.model.*;

/** The AST visitor that reads Java sources. */
public class JavaVisitor extends VoidVisitorAdapter<JBlock> {

    /** The scoping object. */
    private final Scope scope = new Scope();
    /** The mapping from AST nodes to heap sites. */
    private final Map<Expression, JAllocation> heapSites = new HashMap<>();
    /** The mapping from AST nodes to call sites. */
    private final Map<Node, JMethodInvocation> callSites = new HashMap<>();
    /** The source code file. */
    private final SourceFile sourceFile;

    public JavaVisitor(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public void visit(CompilationUnit cu, JBlock block) {
        // Add default Java imports.
        sourceFile.imports.add(new Import(null, "java.lang", true, false));
        super.visit(cu, block);

        if (sourceFile.debug)
            System.out.println(new YamlPrinter(true).output(cu));
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration td, JBlock block) {
        List<TypeUse> superTypeUses = new ArrayList<>();
        updateFromTypes(superTypeUses, td.getExtendedTypes());
        updateFromTypes(superTypeUses, td.getImplementedTypes());
        List<String> superTypes = superTypeUses.stream().map(tu -> tu.type).collect(Collectors.toList());
        JType jt = recordType(td, superTypes, false, td.isInterface());
        jt.typeUses.addAll(superTypeUses);
        scope.enterTypeScope(jt, (jt0 -> super.visit(td, block)));
    }

    void updateFromTypes(List<TypeUse> types, NodeList<ClassOrInterfaceType> nl) {
        if (nl != null)
            for (ClassOrInterfaceType t : nl) {
                SimpleName name = t.getName();
                Position pos = JavaUtils.createPositionFromNode(name);
                types.add(new TypeUse(name.asString(), pos, sourceFile));
            }
    }

    @Override
    public void visit(EnumDeclaration ed, JBlock block) {
        JType jt = recordType(ed, Collections.singletonList("java.lang.Enum"), true, false);
        scope.enterTypeScope(jt, (jt0 -> super.visit(ed, block)));
    }

    @Override
    public void visit(AnnotationDeclaration ad, JBlock block) {
        JType jt = recordType(ad, null, false, true);
        scope.enterTypeScope(jt, (jt0 -> super.visit(ad, block)));
    }

    private <U extends TypeDeclaration<?>, T extends TypeDeclaration<U> & NodeWithAccessModifiers<U> & NodeWithStaticModifier<U>>
    JType recordType(T decl, List<String> superTypes,
                     boolean isEnum, boolean isInterface) {
        JType jt = jTypeFromTypeDecl(decl, isEnum, isInterface, superTypes, scope);
        if (sourceFile.debug)
            System.out.println("Adding type: " + jt);
        sourceFile.jTypes.add(jt);
        return jt;
    }

    @Override
    public void visit(ConstructorDeclaration cd, JBlock block) {
        visit(cd, "void", null, (() -> super.visit(cd, block)));
    }

    @Override
    public void visit(MethodDeclaration md, JBlock block) {
        String retType = md.getTypeAsString();
        Collection<TypeUse> retTypeUses = new HashSet<>();
        addTypeUsesFromType(retTypeUses, md.getType());
        visit(md, retType, retTypeUses, (() -> super.visit(md, block)));
    }

    @Override
    public void visit(VariableDeclarationExpr vDecl, JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.err.println("ERROR: variable declaration outside type: " + vDecl.getRange());
            return;
        }
        JavaModifierPack mp = new JavaModifierPack(sourceFile, vDecl);
        jt.typeUses.addAll(mp.getAnnotationUses());
        for (VariableDeclarator variable : vDecl.getVariables()) {
            Type type = variable.getType();
            addTypeUsesFromType(jt.typeUses, type);
            Position pos = JavaUtils.createPositionFromNode(variable);
            String name = variable.getNameAsString();
            if (block == null) {
                System.err.println("ERROR: null block in " + pos);
                continue;
            }
            JVariable v = new JVariable(sourceFile, pos, name, type.asString(), true, mp);
            if (sourceFile.debug)
                System.out.println("Adding variable [" + v + "] to block [" + block + "]");
            block.addVariable(v);
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isPresent()) {
                Expression initExpr = initializer.get();
                initExpr.accept(this, block);
                registerPossibleTarget(() -> v, initExpr);
            }
        }
    }

    /**
     * Helper method that recursively traverses a type and records all type uses.
     * @param target        the collection to populate
     * @param type          the type to traverse
     */
    private void addTypeUsesFromType(Collection<TypeUse> target, Type type) {
        if (type.isPrimitiveType() || type.isVoidType())
            return;

        // Add all annotation type uses.
        target.addAll((new JavaModifierPack(sourceFile, type.getAnnotations())).getAnnotationUses());

        // Nothing else to do for unknown types.
        if (type.isUnknownType())
            return;

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrIntf = ((ClassOrInterfaceType) type);
            SimpleName name = classOrIntf.getName();
            TypeUse tu = new TypeUse(name.asString(), JavaUtils.createPositionFromNode(name), sourceFile);
            target.add(tu);
            if (sourceFile.debug)
                System.out.println("Added type use: " + tu);
            classOrIntf.getTypeArguments().ifPresent(nl -> {
                for (Type typeArg : nl)
                    addTypeUsesFromType(target, typeArg);
            });
        } else if (type.isArrayType())
            addTypeUsesFromType(target, ((ArrayType) type).getComponentType());
        else if (type.isIntersectionType())
            System.err.println("WARNING: intersection type uses are not yet recorded.");
        else if (type.isTypeParameter())
            System.err.println("WARNING: type parameter uses are not yet recorded.");
        else if (type.isUnionType()) {
            UnionType uType = (UnionType) type;
            uType.getElements().ifNonEmpty(nl -> nl.forEach(refType -> addTypeUsesFromType(target, refType)));
        } else if (type.isVarType())
            System.err.println("WARNING: var-type uses are not yet recorded.");
        else if (type.isWildcardType()) {
            WildcardType wType = ((WildcardType)type);
            wType.getExtendedType().ifPresent(refType -> addTypeUsesFromType(target, refType));
            wType.getSuperType().ifPresent(refType -> addTypeUsesFromType(target, refType));
        } else
            System.err.println("WARNING: unknown type use for element: " + type.getClass().getSimpleName());
    }

//    @Override
//    public void visit(FieldAccessExpr fldAccess, JBlock block) {
//        System.out.println("Field access: " + fldAccess.getName() +
//                ", scope: " + fldAccess.getScope().toString());
//        super.visit(fldAccess, block);
//    }

    @Override
    public void visit(InitializerDeclaration init, JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.out.println("ERROR: found initializer declaration outside type: " + init);
            return;
        }
        jt.typeUses.addAll(new JavaModifierPack(sourceFile, init.getAnnotations()).getAnnotationUses());

        JInit initMethod = init.isStatic() ? jt.classInitializer : jt.initBlock;
        initMethod.setSource(true);
        Position outerPos = JavaUtils.createPositionFromNode(init);
        long startColumn = outerPos.getStartColumn();
        initMethod.pos = new Position(outerPos.getStartLine(), startColumn, startColumn + "static".length());
        initMethod.outerPos = outerPos;
        scope.enterInitializerScope(initMethod, (cl -> init.getBody().accept(this, block)));
    }

    private <T extends CallableDeclaration<?>>
    void visit(CallableDeclaration<T> md, String retType, Collection<TypeUse> retTypeUses,
               Runnable methodProcessor) {
        SimpleName name = md.getName();
        List<JVariable> parameters = new ArrayList<>();
        Collection<TypeUse> paramTypeUses = new HashSet<>();
        boolean isVarArgs = false;
        for (Parameter param : md.getParameters()) {
            if (param.isVarArgs())
                isVarArgs = true;
            Type pType = param.getType();
            addTypeUsesFromType(paramTypeUses, pType);
            Position paramPos = JavaUtils.createPositionFromNode(param);
            JavaModifierPack mp = new JavaModifierPack(sourceFile, param);
            parameters.add(new JVariable(sourceFile, paramPos, param.getNameAsString(), pType.asString(), false, mp));
        }
        JType jt = scope.getEnclosingType();
        JavaModifierPack mp = new JavaModifierPack(sourceFile, md, false, false, isVarArgs);
        JMethod jm = new JMethod(sourceFile, name.toString(), retType, parameters,
                mp.getAnnotations(), JavaUtils.createPositionFromNode(md), jt,
                JavaUtils.createPositionFromNode(name), isVarArgs);
        if (!mp.isStatic())
            jm.setReceiver();
        jt.typeUses.addAll(mp.getAnnotationUses());
        Utils.addSigTypeRefs(jt, retTypeUses, paramTypeUses);
        for (ReferenceType thrownException : md.getThrownExceptions())
            addTypeUsesFromType(jt.typeUses, thrownException);
        if (sourceFile.debug)
            System.out.println("Adding method: " + jm);
        jt.methods.add(jm);

        // Set current method and visit method body.
        scope.enterMethodScope(jm, (jm0 -> methodProcessor.run()));
    }

    @Override
    public void visit(FieldDeclaration fd, JBlock block) {
        for (VariableDeclarator vd : fd.getVariables()) {
            Collection<TypeUse> fieldTypeUses = new ArrayList<>();
            addTypeUsesFromType(fieldTypeUses, vd.getType());
            JType jt = scope.getEnclosingType();
            jt.typeUses.addAll(fieldTypeUses);

            JavaModifierPack mp = new JavaModifierPack(sourceFile, fd, false, false, false);
            JField srcField = new JField(sourceFile, typeOf(vd), vd.getNameAsString(),
                    mp.getAnnotations(), JavaUtils.createPositionFromNode(vd), jt);
            jt.typeUses.addAll(mp.getAnnotationUses());
            if (sourceFile.debug)
                System.out.println("Adding field: " + srcField);
            jt.fields.add(srcField);
            Optional<Expression> optInitializer = vd.getInitializer();
            if (optInitializer != null && optInitializer.isPresent()) {
                final boolean isStaticField = mp.isStatic();
                Expression initExpr = optInitializer.get();
                if (isStaticField && fd.isFinal() && (initExpr.isStringLiteralExpr())) {
                    StringLiteralExpr s = (StringLiteralExpr) initExpr;
                    String sValue = s.getValue();
                    if (sourceFile.debug)
                        System.out.println("Java static final field points to string constant: " + sValue);
                    Position pos = JavaUtils.createPositionFromNode(s);
                    sourceFile.stringConstants.add(new JStringConstant<>(sourceFile, pos, srcField, sValue));
                } else {
                    JMethod initBlock = isStaticField ? jt.classInitializer : jt.initBlock;
                    JBlock methodBlock = new JBlock(initBlock.name, block);
                    scope.enterMethodScope(initBlock, init -> initExpr.accept(this, methodBlock));
                }
            }
        }
    }

    @Override
    public void visit(PackageDeclaration pd, JBlock block) {
        sourceFile.packageName = pd.getNameAsString();
    }

    @Override
    public void visit(final ImportDeclaration id, final JBlock block) {
        Position pos = JavaUtils.createPositionFromNode(id);
        sourceFile.imports.add(new Import(pos, id.getNameAsString(), id.isAsterisk(), id.isStatic()));
    }

    @Override
    public void visit(IfStmt ifStmt, JBlock block) {
        // Reimplement parent to control visit order.
        ifStmt.getCondition().accept(this, block);
        ifStmt.getThenStmt().accept(this, block);
        ifStmt.getElseStmt().ifPresent(l -> l.accept(this, block));
    }

    @Override
    public void visit(ExplicitConstructorInvocationStmt constrInvo, JBlock block) {
        Position pos = JavaUtils.createPositionFromNode(constrInvo);
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: explicit constructors in initializers");
        else {
            JMethodInvocation invo = parentMethod.addInvocation(scope, "<init>", constrInvo.getArguments().size(), pos, sourceFile, block, null);
            callSites.put(constrInvo, invo);
        }
        super.visit(constrInvo, block);
    }

    @Override
    public void visit(ObjectCreationExpr objCExpr, JBlock block) {
        Position pos = JavaUtils.createPositionFromNode(objCExpr);
        JMethod parentMethod = scope.getEnclosingMethod();
        Optional<NodeList<BodyDeclaration<?>>> anonymousClassBody = objCExpr.getAnonymousClassBody();
        boolean isAnonymousClassDecl = anonymousClassBody.isPresent();
        Collection<TypeUse> typeUses = new ArrayList<>();
        addTypeUsesFromType(typeUses, objCExpr.getType());
        JType enclosingType = scope.getEnclosingType();
        enclosingType.typeUses.addAll(typeUses);
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
                    bodyDeclaration.accept(jv, block);
            });
        }
        if (parentMethod == null)
            System.out.println("ERROR: allocations/invocations in object creation in initializers");
        else {
            String base = getName(objCExpr.getScope());
            JMethodInvocation invo = parentMethod.addInvocation(this.scope, "<init>", objCExpr.getArguments().size(), pos, sourceFile, block, base);
            callSites.put(objCExpr, invo);
            // If anonymous, add placeholder allocation, to be matched later.
            JAllocation alloc = parentMethod.addAllocation(sourceFile, pos, isAnonymousClassDecl ? ":ANONYMOUS_CLASS:" : simpleType);
            heapSites.put(objCExpr, alloc);
        }
        objCExpr.getArguments().forEach(p -> p.accept(this, block));
    }

    private static String getName(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Expression> sc) {
        if (sc.isPresent()) {
            Expression expr = sc.get();
            if (expr.isNameExpr())
                return expr.asNameExpr().getNameAsString();
        }
        return null;
    }

    @Override
    public void visit(MethodReferenceExpr mRef, JBlock block) {
        String id = mRef.getIdentifier();
        Expression baseExpr = mRef.getScope();
        if (id.equals("new"))
            id = "<init>";
        if (baseExpr.isTypeExpr()) {
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
        baseExpr.accept(this, block);
        mRef.getTypeArguments().ifPresent(l -> l.forEach(v -> v.accept(this, block)));
    }

    @Override
    public void visit(ArrayCreationExpr arrayCreationExpr, JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.out.println("ERROR: array creation found outside type");
            return;
        }

        for (ArrayCreationLevel level : arrayCreationExpr.getLevels())
            jt.typeUses.addAll((new JavaModifierPack(sourceFile, level.getAnnotations())).getAnnotationUses());

        JMethod parentMethod = scope.getEnclosingMethod();
        Position pos = JavaUtils.createPositionFromNode(arrayCreationExpr);
        if (parentMethod == null)
            System.out.println("TODO: array creation in initializers: " + sourceFile + ": " + pos);
        else {
            JAllocation alloc = parentMethod.addAllocation(sourceFile, pos, arrayCreationExpr.createdType().asString());
            heapSites.put(arrayCreationExpr, alloc);
        }

        arrayCreationExpr.getInitializer().ifPresent(initializer -> initializer.accept(this, block));
    }

    @Override
    public void visit(MethodCallExpr call, JBlock block) {
        int arity = call.getArguments().size();
        Position pos = JavaUtils.createPositionFromNode(call);
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: invocations in initializers: " + sourceFile + ": " + pos);
        else {
            JMethodInvocation invo = parentMethod.addInvocation(scope, call.getName().getIdentifier(), arity, pos, sourceFile, block, getName(call.getScope()));
            callSites.put(call, invo);
        }
        super.visit(call, block);
    }

    @Override
    public void visit(BlockStmt n, JBlock block) {
        if (sourceFile.debug)
            for (Statement statement : n.getStatements())
                System.out.println("STATEMENT: " + statement.getClass().getSimpleName());
        super.visit(n, JavaUtils.newBlock(n, block, scope.getEnclosingMethod()));
    }

    @Override
    public void visit(ClassExpr classExpr, JBlock block) {
        Position pos = JavaUtils.createPositionFromNode(classExpr);
        TypeUse tu = new TypeUse(classExpr.getTypeAsString(), pos, sourceFile);
        if (sourceFile.debug)
            System.out.println("Registering type use: " + tu);
        scope.getEnclosingType().typeUses.add(tu);
    }

    @Override
    public void visit(final CatchClause cc, JBlock block) {
        JBlock catchBlock = new JBlock(JavaUtils.createPositionFromNode(cc), block);
        visit(cc.getParameter(), catchBlock);
        visit(cc.getBody(), catchBlock);
    }

    @Override
    public void visit(Parameter param, JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt != null) {
            addTypeUsesFromType(jt.typeUses, param.getType());
            jt.typeUses.addAll(new JavaModifierPack(sourceFile, param).getAnnotationUses());
        } else
            System.err.println("WARNING: found parameter outside type: " + param);
    }

    @Override
    public void visit(CastExpr castExpr, JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt != null)
            addTypeUsesFromType(jt.typeUses, castExpr.getType());
        else
            System.err.println("WARNING: found cast outside type: " + castExpr);
        super.visit(castExpr, block);
    }

    @Override
    public void visit(AssignExpr assignExpr, JBlock block) {
        Expression target = assignExpr.getTarget();
        if (target.isFieldAccessExpr()) {
            FieldAccessExpr fieldAcc = (FieldAccessExpr) target;
            // Any field accesses in the "scope" should be reads.
            fieldAcc.getScope().accept(this, block);
            // Record the final field as a "write".
            visitFieldAccess(fieldAcc, false);
        } else
            target.accept(this, block);
        Expression value = assignExpr.getValue();
        value.accept(this, block);

        if ((block != null) && (target.isNameExpr()))
            registerPossibleTarget((() -> block.lookup(((NameExpr) target).getNameAsString())), value);
    }

    /**
     * Record "x" variable information ("x = new ..." / "x = m()" / "x = (T) m()").
     * Ordering matters: this method must be called after visiting the
     * expression/statement that assigns the value to the target variable.
     */
    private void registerPossibleTarget(Supplier<JVariable> target, Expression value) {
        if (value.isObjectCreationExpr() || value.isArrayCreationExpr())
            registerTarget(heapSites, target, value);
        else if (value.isMethodCallExpr())
            registerTarget(callSites, target, value);
        else if (value.isCastExpr())
            registerPossibleTarget(target, value.asCastExpr().getExpression());
    }

    private void registerTarget(Map<? extends Node, ? extends Targetable> map,
                                Supplier<JVariable> target, Expression value) {
        JVariable v = target.get();
        if (v == null)
            return;
        Targetable element = map.get(value);
        if (element == null)
            return;
        element.setTarget(v);
        if (sourceFile.debug)
            System.out.println("TARGET_ASSIGNMENT: " + v + " := " + element);
    }

    private void visitFieldAccess(FieldAccessExpr fieldAccess, boolean read) {
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
                    addTypeUsesFromType(jt.typeUses, typeArg);
            });
    }

    @Override
    public void visit(FieldAccessExpr fieldAccess, JBlock block) {
        // This method is assumed to only find reads and be overridden when visiting field writes.
        visitFieldAccess(fieldAccess, true);
    }

    @Override
    public void visit(ThisExpr thisExpr, JBlock block) {
        JMethod enclosingMethod = scope.getEnclosingMethod();
        if (enclosingMethod == null)
            System.err.println("ERROR: found variable 'this' outside method: " + thisExpr);
        else if (thisExpr.getTypeName().isPresent())
            System.err.println("ERROR: qualified 'this' is not supported yet: " + thisExpr);
        else
            enclosingMethod.addThisAccess(JavaUtils.createPositionFromNode(thisExpr));
        super.visit(thisExpr, block);
    }

    private static String typeOf(NodeWithType<? extends Node, ? extends com.github.javaparser.ast.type.Type> node) {
        return Utils.simplifyType(node.getType().asString());
    }

    private <U extends TypeDeclaration<?>, T extends TypeDeclaration<U> & NodeWithAccessModifiers<U> & NodeWithStaticModifier<U>>
    JType jTypeFromTypeDecl(T decl, boolean isEnum, boolean isInterface,
                            List<String> superTypes, Scope scope) {
        SimpleName name = decl.getName();
        JType parent = scope.getEnclosingType();
        JavaModifierPack mp = new JavaModifierPack(sourceFile, decl, isEnum, isInterface, false);
        boolean isInner = parent != null && !mp.isStatic();
        JType jt = new JType(sourceFile, name.toString(), superTypes, mp.getAnnotations(),
                JavaUtils.createPositionFromNode(name), scope.getEnclosingElement(),
                parent, isInner, mp.isPublic(), mp.isPrivate(), mp.isProtected(),
                mp.isAbstract(), mp.isFinal(), false);
        jt.typeUses.addAll(mp.getAnnotationUses());
        return jt;
    }
}
