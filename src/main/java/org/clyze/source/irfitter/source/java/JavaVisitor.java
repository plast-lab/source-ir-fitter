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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.source.model.*;

/** The AST visitor that reads Java sources. */
public class JavaVisitor extends VoidVisitorAdapter<JBlock> {

    /** The scoping object. */
    private final Scope scope = new Scope();
    /** The mapping from AST nodes to heap sites. */
    private final Map<Expression, JAllocation> heapSites = new HashMap<>();
    /** The mapping from AST nodes to call sites. */
    private final Map<Node, JMethodInvocation> callSites = new HashMap<>();
    /** The mapping from AST nodes to call sites. */
    private final Map<Expression, JCast> castSites = new HashMap<>();
    /** The source code file. */
    private final SourceFile sourceFile;
    /** Debugging mode. */
    private final boolean debug;

    public JavaVisitor(SourceFile sourceFile, boolean debug) {
        this.sourceFile = sourceFile;
        this.debug = debug;
    }

    @Override
    public void visit(CompilationUnit cu, JBlock block) {
        // Add default Java imports.
        sourceFile.imports.add(new Import(null, "java.lang", true, false));
        super.visit(cu, block);

        if (debug) {
            System.out.println("Compilation unit: " + sourceFile);
            System.out.println(new YamlPrinter(true).output(cu));
        }
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration td, JBlock block) {
        List<TypeUse> superTypeUses = new ArrayList<>();
        updateFromTypes(superTypeUses, td.getExtendedTypes());
        updateFromTypes(superTypeUses, td.getImplementedTypes());
        List<String> superTypes = superTypeUses.stream().map(tu -> tu.type).collect(Collectors.toList());
        JType jt = recordType(td, superTypes, false, td.isInterface());
        jt.typeUses.addAll(superTypeUses);
        ElementWithPosition<?, ?> parentElem = scope.getEnclosingElement();
        JType parentType = scope.getEnclosingType();
        if (parentElem instanceof JMethod) {
            if (parentType == null)
                System.err.println("ERROR: no parent type for type " + jt);
            else
                jt.methodTypeCounter = parentType.getNextMethodTypeNumber(jt.getSimpleName());
        }
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
        if (debug)
            System.out.println("Adding type: " + jt);
        sourceFile.jTypes.add(jt);
        return jt;
    }

    @Override
    public void visit(ConstructorDeclaration cd, JBlock block) {
        visit(cd, "void", null, block, ((block0) -> super.visit(cd, block0)));
    }

    @Override
    public void visit(MethodDeclaration md, JBlock block) {
        String retType = md.getTypeAsString();
        Collection<TypeUse> retTypeUses = new HashSet<>();
        addTypeUsesFromType(retTypeUses, md.getType());
        visit(md, retType, retTypeUses, block, ((block0) -> super.visit(md, block0)));
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
            if (debug)
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
            // Replace dots by dollars to aid IR type matching.
            TypeUse tu = new TypeUse(Utils.dotsToDollars(classOrIntf.asString()), JavaUtils.createPositionFromNode(name), sourceFile);
            target.add(tu);
            if (debug)
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

    @Override
    public void visit(final InstanceOfExpr instanceOfExpr, final JBlock block) {
        proccessNameAccess(instanceOfExpr.getExpression(), instanceOfExpr, block, AccessType.READ);
        super.visit(instanceOfExpr, block);
    }

    @Override
    public void visit(final ReturnStmt retStmt, final JBlock block) {
        retStmt.getExpression().ifPresent(expr -> proccessNameAccess(expr, retStmt, block, AccessType.READ));
        super.visit(retStmt, block);
    }

    @Override
    public void visit(final ThrowStmt throwStmt, final JBlock block) {
        proccessNameAccess(throwStmt.getExpression(), throwStmt, block, AccessType.READ);
        super.visit(throwStmt, block);
    }

    @Override
    public void visit(final UnaryExpr uExpr, final JBlock block) {
        proccessNameAccess(uExpr.getExpression(), uExpr, block, AccessType.READ);
        super.visit(uExpr, block);
    }

    @Override
    public void visit(final BinaryExpr bExpr, final JBlock block) {
        proccessNameAccess(bExpr.getLeft(), bExpr, block, AccessType.READ);
        proccessNameAccess(bExpr.getRight(), bExpr, block, AccessType.READ);
        super.visit(bExpr, block);
    }

    @Override
    public void visit(final YieldStmt yStmt, final JBlock block) {
        proccessNameAccess(yStmt.getExpression(), yStmt, block, AccessType.READ);
        super.visit(yStmt, block);
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
               JBlock block, Consumer<JBlock> methodProcessor) {
        SimpleName name = md.getName();
        List<JVariable> parameters = new ArrayList<>();
        Collection<TypeUse> paramTypeUses = new HashSet<>();
        boolean isVarArgs = false;
        Position methodPos = JavaUtils.createPositionFromNode(name);
        JType jt = scope.getEnclosingType();
        JBlock argsBlock = new JBlock(methodPos, block, jt);
        for (Parameter param : md.getParameters()) {
            if (param.isVarArgs())
                isVarArgs = true;
            Type pType = param.getType();
            addTypeUsesFromType(paramTypeUses, pType);
            Position paramPos = JavaUtils.createPositionFromNode(param);
            JavaModifierPack mp = new JavaModifierPack(sourceFile, param);
            JVariable paramVar = new JVariable(sourceFile, paramPos, param.getNameAsString(), pType.asString(), false, mp);
            parameters.add(paramVar);
            argsBlock.addVariable(paramVar);
        }
        JavaModifierPack mp = new JavaModifierPack(sourceFile, md, false, false, isVarArgs);
        JMethod jm = new JMethod(sourceFile, name.toString(), retType, parameters,
                mp.getAnnotations(), JavaUtils.createPositionFromNode(md), jt,
                methodPos, isVarArgs);
        if (!mp.isStatic())
            jm.setReceiver();
        jt.typeUses.addAll(mp.getAnnotationUses());
        jt.addSigTypeRefs(retTypeUses, paramTypeUses);
        for (ReferenceType thrownException : md.getThrownExceptions())
            addTypeUsesFromType(jt.typeUses, thrownException);
        if (debug)
            System.out.println("Adding method: " + jm);
        jt.methods.add(jm);

        // Set current method and visit method body.
        scope.enterMethodScope(jm, (jm0 -> methodProcessor.accept(argsBlock)));
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
            if (debug)
                System.out.println("Adding field: " + srcField);
            jt.fields.add(srcField);
            Optional<Expression> optInitializer = vd.getInitializer();
            if (optInitializer != null && optInitializer.isPresent()) {
                final boolean isStaticField = mp.isStatic();
                Expression initExpr = optInitializer.get();
                if (isStaticField && fd.isFinal() && (initExpr.isStringLiteralExpr())) {
                    StringLiteralExpr s = (StringLiteralExpr) initExpr;
                    String sValue = s.getValue();
                    if (debug)
                        System.out.println("Java static final field points to string constant: " + sValue);
                    Position pos = JavaUtils.createPositionFromNode(s);
                    sourceFile.stringConstants.add(new JStringConstant<>(sourceFile, pos, srcField, sValue));
                } else {
                    JMethod initBlock = isStaticField ? jt.classInitializer : jt.initBlock;
                    JBlock methodBlock = new JBlock(initBlock.name, block, jt);
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
        NodeList<Expression> arguments = constrInvo.getArguments();
        if (parentMethod == null)
            System.out.println("TODO: explicit constructors in initializers");
        else {
            JMethodInvocation invo = parentMethod.addInvocation(scope, "<init>", arguments.size(), pos, sourceFile, block, null);
            callSites.put(constrInvo, invo);
        }
        constrInvo.getExpression().ifPresent(expr -> proccessNameAccess(expr, constrInvo, block, AccessType.READ));
        processNameReadsInArgs(arguments, constrInvo, block);
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
        String allocationType = typeOf(objCExpr);
        if (isAnonymousClassDecl) {
            List<String> superTypes = Collections.singletonList(allocationType);
            JType anonymousType = enclosingType.createAnonymousClass(sourceFile, superTypes, scope.getEnclosingElement(), pos, false);
            if (debug)
                System.out.println("Adding type [anonymous]: " + anonymousType);
            sourceFile.jTypes.add(anonymousType);
            JavaVisitor jv = this;
            scope.enterTypeScope(anonymousType, jt0 -> {
                for (BodyDeclaration<?> bodyDeclaration : anonymousClassBody.get())
                    bodyDeclaration.accept(jv, block);
            });
        }
        NodeList<Expression> arguments = objCExpr.getArguments();
        if (parentMethod == null)
            System.out.println("ERROR: allocations/invocations in object creation in initializers");
        else {
            String base = getName(objCExpr.getScope());
            JMethodInvocation invo = parentMethod.addInvocation(this.scope, "<init>", arguments.size(), pos, sourceFile, block, base);
            callSites.put(objCExpr, invo);
            // If anonymous, add placeholder allocation, to be matched later.
            JAllocation alloc = parentMethod.addAllocation(sourceFile, pos, isAnonymousClassDecl ? ":ANONYMOUS_CLASS:" : allocationType);
            heapSites.put(objCExpr, alloc);
        }
        processNameReadsInArgs(arguments, objCExpr, block);
        arguments.forEach(p -> p.accept(this, block));
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
            call.getScope().ifPresent(scopeExpr -> proccessNameAccess(scopeExpr, call, block, AccessType.READ));
            processNameReadsInArgs(call.getArguments(), call, block);
        }
        super.visit(call, block);
    }

    @Override
    public void visit(BlockStmt n, JBlock block) {
        if (debug)
            for (Statement statement : n.getStatements())
                System.out.println("STATEMENT: " + statement.getClass().getSimpleName());
        super.visit(n, JavaUtils.newBlock(n, block, scope.getEnclosingMethod(), null));
    }

    @Override
    public void visit(ClassExpr classExpr, JBlock block) {
        Position pos = JavaUtils.createPositionFromNode(classExpr);
        TypeUse tu = new TypeUse(classExpr.getTypeAsString(), pos, sourceFile);
        if (debug)
            System.out.println("Registering type use: " + tu);
        scope.getEnclosingType().typeUses.add(tu);
    }

    @Override
    public void visit(final CatchClause cc, JBlock block) {
        JBlock catchBlock = new JBlock(JavaUtils.createPositionFromNode(cc), block, null);
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
        if (jt != null) {
            Type type = castExpr.getType();
            addTypeUsesFromType(jt.typeUses, type);
            JMethod enclosingMethod = scope.getEnclosingMethod();
            if (enclosingMethod != null) {
                JCast cast = new JCast(sourceFile, JavaUtils.createPositionFromNode(type), type.asString());
                enclosingMethod.addCast(cast);
                castSites.put(castExpr, cast);
            } else
                System.err.println("WARNING: found cast outside method: " + castExpr);
        } else
            System.err.println("WARNING: found cast outside type: " + castExpr);
        proccessNameAccess(castExpr.getExpression(), castExpr, block, AccessType.READ);
        super.visit(castExpr, block);
    }

    private void proccessNameAccess(Expression expr, Node parentNode, JBlock block,
                                    AccessType accessType) {
        if (expr == null || !expr.isNameExpr() || block == null)
            return;
        NameExpr nameExpr = expr.asNameExpr();
        JBlock.Result lookupRes = block.lookup(nameExpr.getNameAsString());
        if (debug)
            System.out.println("processNameAccess(): nameExpr=" + nameExpr + ", parentNode=" + parentNode + ", lookupRes=" + lookupRes + ", accessType=" + accessType);
        if (lookupRes == null)
            return;
        JVariable localVar = lookupRes.variable;
        if (localVar == null) {
            JType jt = scope.getEnclosingType();
            if (jt == null)
                System.err.println("ERROR: no enclosing type for expression: " + parentNode);
            else {
                SimpleName name = nameExpr.getName();
                JField matchingField = lookupRes.field;
                if (matchingField != null) {
                    visitFieldAccess(parentNode, name, accessType, matchingField);
                }
                else
                    System.err.println("WARNING: ignoring expresssion involving name '" + name + "' from nested scope: " + parentNode + ", position: " + JavaUtils.createPositionFromNode(name));
            }
        } else {
            JMethod enclosingMethod = scope.getEnclosingMethod();
            if (enclosingMethod != null)
                enclosingMethod.addVarAccess(JavaUtils.createPositionFromNode(nameExpr),
                        accessType.kind, localVar);
            else
                System.err.println("WARNING: found variable use outside method: " + parentNode);
        }
    }

    private void processNameReadsInArgs(NodeList<Expression> args, Node parentExpr, JBlock block) {
        if (args == null)
            return;
        for (Expression argument : args)
            proccessNameAccess(argument, parentExpr, block, AccessType.READ);
    }

    @Override
    public void visit(final SynchronizedStmt syncStmt, final JBlock block) {
        if (debug)
            System.out.println("Visiting synchronized statement: " + syncStmt);
        Expression expr = syncStmt.getExpression();
        proccessNameAccess(expr, expr, block, AccessType.READ);
        super.visit(syncStmt, block);
    }

    private void visitAssignmentTarget(Expression target, Node parentNode, JBlock block) {
        if (target.isFieldAccessExpr()) {
            FieldAccessExpr fieldAcc = (FieldAccessExpr) target;
            // Any field accesses in the "scope" should be reads.
            fieldAcc.getScope().accept(this, block);
            // Record the final field as a "write".
            visitFieldAccess(fieldAcc, AccessType.WRITE);
        } else if (target.isNameExpr()) {
            NameExpr nameExpr = (NameExpr) target;
            proccessNameAccess(nameExpr, parentNode, block, AccessType.WRITE);
        } else if (target.isArrayAccessExpr()) {
            ArrayAccessExpr arrayAcc = (ArrayAccessExpr) target;
            Expression name = arrayAcc.getName();
            System.out.println("ARRAY_ACCESS: name = " + name + ", class = " + name.getClass().getSimpleName());
            visitAssignmentTarget(name, parentNode, block);
            // Any index accesses in the "scope" should be reads. This ignores
            // mutating expressions such as "i++".
            Expression indexExpr = arrayAcc.getIndex();
            proccessNameAccess(indexExpr, parentNode, block, AccessType.READ);
            indexExpr.accept(this, block);
        } else
            target.accept(this, block);
    }

    @Override
    public void visit(AssignExpr assignExpr, JBlock block) {
        if (debug)
            System.out.println("Visiting assignment: " + assignExpr);
        Expression target = assignExpr.getTarget();
        visitAssignmentTarget(target, assignExpr, block);
        Expression value = assignExpr.getValue();
        proccessNameAccess(value, assignExpr, block, AccessType.READ);
        value.accept(this, block);

        if ((block != null) && (target.isNameExpr()))
            registerPossibleTarget((() -> {
                JBlock.Result lookup = block.lookup(((NameExpr) target).getNameAsString());
                return lookup != null && lookup.variable != null ? lookup.variable : null;
            }), value);
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
            registerTarget(castSites, target, value);
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
        if (debug)
            System.out.println("TARGET_ASSIGNMENT: " + v + " := " + element);
    }

    private void visitFieldAccess(FieldAccessExpr fieldAccess, AccessType accessType) {
        visitFieldAccess(fieldAccess, fieldAccess.getName(), accessType, null);
        JType jt = scope.getEnclosingType();
        if (jt == null)
            System.out.println("ERROR: field access outside type: " + fieldAccess + ": " + sourceFile);
        else
            fieldAccess.getTypeArguments().ifPresent(nl -> {
                for (Type typeArg : nl)
                    addTypeUsesFromType(jt.typeUses, typeArg);
            });
    }

    private void visitFieldAccess(Node fieldAccess, SimpleName name,
                                  AccessType accType, JField target) {
        String fieldName = name.asString();
        Position pos = JavaUtils.createPositionFromNode(name);
        if (debug)
            System.out.println("Field access [" + (accType.name()) + "]: " + fieldName + "@" + sourceFile + ":" + pos);
        JMethod parentMethod = scope.getEnclosingMethod();
        if (parentMethod == null)
            System.out.println("TODO: field access outside method: " + fieldAccess + ": " + sourceFile);
        else
            parentMethod.fieldAccesses.add(new JFieldAccess(sourceFile, pos, accType, fieldName, target));
    }

    @Override
    public void visit(FieldAccessExpr fieldAccess, JBlock block) {
        // This method is assumed to only find reads and be overridden when visiting field writes.
        visitFieldAccess(fieldAccess, AccessType.READ);
    }

    @Override
    public void visit(NameExpr nameExpr, JBlock block) {
        if (debug)
            if (nameExpr != null && block != null)
                System.out.println("nameExpr=" + nameExpr + ", lookup=" + block.lookup(nameExpr.getNameAsString()));
        super.visit(nameExpr, block);
    }

    private JType findOuterClassWithName(JType parent, String className) {
        if (parent == null || parent.getSimpleName().equals(className))
            return parent;
        else
            return findOuterClassWithName(parent.parentType, className);
    }

    @Override
    public void visit(ThisExpr thisExpr, JBlock block) {
        JMethod enclosingMethod = scope.getEnclosingMethod();
        if (enclosingMethod == null)
            System.err.println("ERROR: found variable 'this' outside method: " + thisExpr);
        else if (thisExpr.getTypeName().isPresent()) {
            // To support "C.this", we traverse the outer classes and find "C",
            // then we record this access. This is later resolved, during matching.
            JType thisType = scope.getEnclosingType();
            if (thisType == null)
                System.err.println("ERROR: no enclosing type information available for qualified 'this': " + thisExpr);
            else {
                JType outerClass = findOuterClassWithName(thisType.parentType, thisExpr.getTypeName().get().asString());
                if (outerClass == null)
                    System.err.println("ERROR: qualified 'this' is not supported yet, outer class not found: " + thisExpr);
                else
                    enclosingMethod.addOuterThisAccess(new OuterThis(sourceFile, JavaUtils.createPositionFromNode(thisExpr), outerClass), debug);
            }
        } else
            enclosingMethod.addThisAccess(JavaUtils.createPositionFromNode(thisExpr));
        super.visit(thisExpr, block);
    }

    @Override
    public void visit(final SwitchEntry switchEntry, final JBlock block) {
        switchEntry.getLabels().forEach(lab -> proccessNameAccess(lab, switchEntry, block, AccessType.READ));
        super.visit(switchEntry, block);
    }

    @Override
    public void visit(final LambdaExpr lambdaExpr, final JBlock block) {
        JType jt = scope.getEnclosingType();
        if (jt != null) {
            Position outerPos = JavaUtils.createPositionFromNode(lambdaExpr);
            Optional<Expression> bodyOpt = lambdaExpr.getExpressionBody();
            Position pos = bodyOpt.map(JavaUtils::createPositionFromNode).orElse(outerPos);
            JMethod jm = scope.getEnclosingMethod();
            if (jm != null) {
                List<TypeUse> typeUses = new ArrayList<>();
                List<JVariable> parameters = new ArrayList<>();
                for (Parameter parameter : lambdaExpr.getParameters()) {
                    Position paramPos = JavaUtils.createPositionFromNode(parameter);
                    Type parameterType = parameter.getType();
                    String pType;
                    if (parameterType == null)
                        pType = "java.lang.Object";
                    else {
                        pType = parameter.getTypeAsString();
                        typeUses.add(new TypeUse(pType, JavaUtils.createPositionFromNode(parameterType), sourceFile));
                    }
                    JVariable param = new JVariable(sourceFile, paramPos, parameter.getName().asString(), pType, false, new JavaModifierPack(sourceFile, parameter));
                    parameters.add(param);
                }
                jt.addSigTypeRefs(null, typeUses);
                JLambda lam = new JLambda(sourceFile, "lambda@" + pos, parameters, outerPos, jt, pos);
                JBlock lambdaBlock = JavaUtils.newBlock(lambdaExpr, block, lam, jt);
                for (JVariable parameter : parameters)
                    lambdaBlock.addVariable(parameter);

                if (debug)
                    System.out.println("Found source lambda: " + lam);
                // Add lambda both to method for custom matching and to type for generic traversal.
                jm.addLambda(lam);
                jt.methods.add(lam);
                scope.enterMethodScope(lam, (lam0 -> lambdaExpr.getBody().accept(this, lambdaBlock)));
            } else {
                System.err.println("ERROR: found lambda outside method definition.");
            }
        } else
            System.err.println("ERROR: cannot handle lambda outside type: " + lambdaExpr);
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
                mp.isAbstract(), mp.isFinal(), false, false, mp.isInterface(), mp.isEnum());
        jt.typeUses.addAll(mp.getAnnotationUses());
        return jt;
    }
}
