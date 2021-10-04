package org.clyze.source.irfitter.source.groovy;

import groovyjarjarantlr4.v4.runtime.ParserRuleContext;
import groovyjarjarantlr4.v4.runtime.RuleContext;
import groovyjarjarantlr4.v4.runtime.Token;
import groovyjarjarantlr4.v4.runtime.tree.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.groovy.parser.antlr4.GroovyParser.*;
import org.apache.groovy.parser.antlr4.GroovyParserBaseVisitor;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.base.AccessType;
import org.clyze.source.irfitter.base.ModifierPack;
import org.clyze.source.irfitter.source.model.*;

/**
 * A visitor of the Groovy AST. For the actual grammar, see:
 * https://github.com/danielsun1106/groovy-parser/blob/master/src/main/antlr/GroovyParser.g4
 */
public class GroovyTreeVisitor extends GroovyParserBaseVisitor<Void> {
    /** The scoping object. */
    private final Scope scope = new Scope();
    /** The source file visited. We assume one visitor instance per source file. */
    private final SourceFile sourceFile;
    private final boolean debug;

    GroovyTreeVisitor(SourceFile sourceFile, boolean debug) {
        this.sourceFile = sourceFile;
        this.debug = debug;
        // Add default imports.
        String[] defaultPackages = new String[] { "groovy.lang", "groovy.util" , "java.lang" , "java.util" , "java.net" , "java.io" };
        for (String defaultPackage : defaultPackages)
            sourceFile.imports.add(new Import(null, defaultPackage, true, false));
    }

    @Override
    public Void visit(ParseTree parseTree) {
        return null;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitContext ctx) {
        ctx.children.forEach(c -> c.accept(this));
        return null;
    }

    @Override
    public Void visitPackageDeclaration(PackageDeclarationContext ctx) {
        sourceFile.packageName = getQualifiedName(ctx.qualifiedName());
        logDebug(() -> "packageName=" + sourceFile.packageName);
        return null;
    }

    @Override
    public Void visitScriptStatements(ScriptStatementsContext ctx) {
        for (ScriptStatementContext ssc : ctx.scriptStatement())
            visitScriptStatement(ssc);
        return null;
    }

    @Override
    public Void visitScriptStatement(ScriptStatementContext ssc) {
        ImportDeclarationContext importDecl = ssc.importDeclaration();
        if (importDecl != null) {
            boolean isStatic = importDecl.STATIC() != null;
            boolean isAsterisk = importDecl.MUL() != null;
            String id = getQualifiedName(importDecl.qualifiedName());
            Position pos = GroovyUtils.createPositionFromTokens(importDecl.start, importDecl.stop);
            sourceFile.imports.add(new Import(pos, id, isAsterisk, isStatic));
            return null;
        }

        TypeDeclarationContext typeDecl = ssc.typeDeclaration();
        if (typeDecl != null) {
            ClassDeclarationContext classDecl = typeDecl.classDeclaration();
            GroovyModifierPack mp = new GroovyModifierPack(sourceFile, typeDecl.classOrInterfaceModifiersOpt(), classDecl);
            processClassDeclaration(mp, classDecl);
            return null;
        }

        MethodDeclarationContext methodDecl = ssc.methodDeclaration();
        if (methodDecl != null) {
            visitMethodDeclaration(methodDecl);
            return null;
        }

        return null;
    }

    @Override
    public Void visitMemberDeclaration(MemberDeclarationContext memCtx) {
        MethodDeclarationContext methCtx = memCtx.methodDeclaration();
        if (methCtx != null)
            visitMethodDeclaration(methCtx);
        FieldDeclarationContext fieldCtx = memCtx.fieldDeclaration();
        if (fieldCtx != null)
            visitFieldDeclaration(fieldCtx);
        ClassDeclarationContext classCtx = memCtx.classDeclaration();
        if (classCtx != null) {
            GroovyModifierPack mp = new GroovyModifierPack(sourceFile, memCtx.modifiersOpt());
            processClassDeclaration(mp, classCtx);
        }
        if (methCtx == null && fieldCtx == null && classCtx == null)
            System.out.println("WARNING: unhandled member node in " + scope.getEnclosingType());
        return null;
    }

    @Override
    public Void visitMethodDeclaration(MethodDeclarationContext ctx) {
        MethodNameContext methodNameContext = ctx.methodName();
        ParserRuleContext nameContext = methodNameContext.identifier();
        if (nameContext == null)
            nameContext = methodNameContext.stringLiteral();
        if (nameContext == null)
            System.err.println("ERROR: could not process method name: " + GroovyUtils.createPositionFromTokens(ctx.start, ctx.stop));
        else {
            String name = nameContext.getText();
            // Return types may be missing.
            ReturnTypeContext retCtx = ctx.returnType();
            String retType = retCtx == null ? null : Utils.simplifyType(retCtx.getText());
            Collection<TypeUse> retTypeUses = new HashSet<>();
            if (retCtx != null)
                addTypeUsesInType(retTypeUses, retCtx.type());
            logDebug(() -> "Groovy method: " + name + ", return type: " + retType);
            List<JVariable> parameters = new ArrayList<>();
            Collection<TypeUse> paramTypeUses = new HashSet<>();
            FormalParametersContext paramsCtx = ctx.formalParameters();
            if (paramsCtx == null)
                System.err.println("WARNING: no formal parameters for " + name);
            else {
                FormalParameterListContext paramsListCtx = paramsCtx.formalParameterList();
                if (paramsListCtx != null) {
                    for (FormalParameterContext frmCtx : paramsListCtx.formalParameter()) {
                        String paramName = frmCtx.variableDeclaratorId().identifier().getText();
                        TypeContext frmType = frmCtx.type();
                        String paramType = getType(frmType);
                        addTypeUsesInType(paramTypeUses, frmType);
                        Position paramPos = GroovyUtils.createPositionFromTokens(frmCtx.start, frmCtx.stop);
                        GroovyModifierPack mp = new GroovyModifierPack(frmCtx.variableModifiersOpt());
                        JVariable param = new JVariable(sourceFile, paramPos, paramName, paramType, false, mp);
                        logDebug(() -> "param: " + param);
                        parameters.add(param);
                    }
                }
            }
            Position pos = GroovyUtils.createPositionFromToken(methodNameContext.start);
            Position outerPos = GroovyUtils.createPositionFromTokens(ctx.start, ctx.stop);
            JType jt = scope.getEnclosingType();
            GroovyModifierPack mp = new GroovyModifierPack(sourceFile, ctx.modifiersOpt());
            // TODO: handle varargs in Groovy
            boolean isVarArgs = false;
            JMethod jm = new JMethod(sourceFile, name, retType, parameters, mp.getAnnotations(), outerPos, jt, pos, isVarArgs);
            if (!mp.isStatic())
                jm.setReceiver();
            registerMethodSigTypeUses(ctx, jt, retTypeUses, paramTypeUses);

            if (jt == null)
                System.out.println("WARNING: top-level Groovy methods are not yet supported.");
            else {
                jt.methods.add(jm);
                scope.enterMethodScope(jm, (jm0 -> visitMethodBody(ctx.methodBody())));
            }
        }

        return null;
    }

    private void registerMethodSigTypeUses(MethodDeclarationContext ctx, JType jt,
                                           Collection<TypeUse> retTypeUses,
                                           Collection<TypeUse> paramTypeUses) {
        // Signature return/parameter types.
        jt.addSigTypeRefs(retTypeUses, paramTypeUses);
        // Thrown exception types.
        QualifiedClassNameListContext thrownQTypes = ctx.qualifiedClassNameList();
        if (thrownQTypes != null)
            for (AnnotatedQualifiedClassNameContext thrownQType : thrownQTypes.annotatedQualifiedClassName())
                addTypeUseFromQClassName(jt.typeUses, thrownQType.qualifiedClassName());
    }

    private void addTypeUseFromQClassName(Collection<TypeUse> target, QualifiedClassNameContext qClassName) {
        if (qClassName != null)
            addTypeUseFromName(target, qClassName.getText(), qClassName.start, qClassName.stop);
    }

    private void addTypeUseFromName(Collection<TypeUse> target, String name, Token start, Token stop) {
        TypeUse tu = new TypeUse(name, GroovyUtils.createPositionFromTokens(start, stop), sourceFile);
        if (debug)
            System.out.println("Adding type use: " + tu);
        target.add(tu);
    }

    @Override
    public Void visitBlock(BlockContext grBlock) {
        if (grBlock != null) {
            GroovyTreeVisitor thisVisitor = this;
            scope.enterBlockScope(GroovyUtils.createPositionFromToken(grBlock.start), null, ((JBlock block) -> {
                BlockStatementsOptContext blockStatementsOpt = grBlock.blockStatementsOpt();
                if (blockStatementsOpt != null) {
                    BlockStatementsContext blockStatements = blockStatementsOpt.blockStatements();
                    if (blockStatements != null) {
                        for (BlockStatementContext blockStmt : blockStatements.blockStatement()) {
                            LocalVariableDeclarationContext localVar = blockStmt.localVariableDeclaration();
                            if (localVar != null && debug) {
                                for (JVariable jVar : processVariableDeclaration(localVar.variableDeclaration()))
                                    block.addVariable(jVar);
                            }
                            StatementContext stmt = blockStmt.statement();
                            if (stmt != null)
                                stmt.accept(thisVisitor);
                        }
                    }
                }
            }));
        }
        return null;
    }

    @Override
    public Void visitMethodBody(MethodBodyContext methodBody) {
        if (methodBody == null)
            return null;
        return visitBlock(methodBody.block());
    }

    @Override
    public Void visitExpressionStmtAlt(ExpressionStmtAltContext ctx) {
        visitStatementExpression(ctx.statementExpression());
        return null;
    }

    @Override
    public Void visitStatementExpression(StatementExpressionContext ctx) {
        ctx.accept(this);
        return null;
    }

    @Override
    public Void visitCommandExprAlt(CommandExprAltContext ctx) {
        this.visitCommandExpression(ctx.commandExpression());
        return null;
    }

    @Override
    public Void visitCommandExpression(CommandExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {
        if (ctx == null)
            return null;
        logDebug(() -> "visitExpression(): " + ctx.getClass().getSimpleName());
        return super.visitExpression(ctx);
    }

    @Override
    public Void visitPostfixExprAlt(PostfixExprAltContext ctx) {
        return visitPostfixExpression(ctx.postfixExpression());
    }

    @Override
    public Void visitAssignmentExprAlt(AssignmentExprAltContext ctx) {
        return visitExpression(ctx.expression());
    }

    @Override
    public Void visitReturnStmtAlt(ReturnStmtAltContext ctx) {
        return visitExpression(ctx.expression());
    }

    @Override
    public Void visitLocalVariableDeclarationStmtAlt(LocalVariableDeclarationStmtAltContext ctx) {
        return visitLocalVariableDeclaration(ctx.localVariableDeclaration());
    }

    @Override
    public Void visitLocalVariableDeclaration(LocalVariableDeclarationContext ctx) {
        for (JVariable jVar : processVariableDeclaration(ctx.variableDeclaration()))
            logDebug(() -> "Variable: " + jVar.name);
        return null;
    }

    /**
     * Shows the first characters of a program fragment.
     * @param rc    the program fragment rule context
     * @return      the first characters
     */
    private static String preview(RuleContext rc) {
        if (rc == null)
            return null;
        final int MAX_LEN = 100;
        String text = rc.getText();
        return (text.length() > MAX_LEN) ? text.substring(0, MAX_LEN) + " ..." :  text;
    }

    @Override
    public Void visitPathExpression(PathExpressionContext pathExpr) {
        if (pathExpr == null)
            return null;
        PrimaryContext primary = pathExpr.primary();
        if (primary != null) {
            logDebug(() -> "primary = " + preview(primary));
            if (primary instanceof NewPrmrAltContext) {
                NewPrmrAltContext newPAC = (NewPrmrAltContext) primary;
                CreatorContext creator = newPAC.creator();
                logDebug(() -> "Creator = " + preview(creator));
                AnonymousInnerClassDeclarationContext anonDecl = creator.anonymousInnerClassDeclaration();
                if (anonDecl != null) {
                    CreatedNameContext createdName = creator.createdName();
                    String createdNameValue = createdName.getText();
                    logDebug(() -> "Anonymous class declaration: " + preview(anonDecl) + ", created-name = " + createdNameValue);
                    JType enclosingType = scope.getEnclosingType();
                    if (enclosingType == null)
                        System.out.println("TODO: anonymous classes outside class declarations");
                    else if (createdNameValue == null)
                        System.out.println("WARNING: no created-name information for anonymous class: " + preview(anonDecl));
                    else {
                        List<String> superTypes = Collections.singletonList(createdNameValue);
                        Position pos = GroovyUtils.createPositionFromToken(createdName.start);
                        JType anonymousType = enclosingType.createAnonymousClass(sourceFile,
                                superTypes, scope.getEnclosingElement(), pos, true);
                        logDebug(() -> "Adding type [anonymous]: " + anonymousType);
                        sourceFile.jTypes.add(anonymousType);
                        anonymousType.typeUses.add(new TypeUse(createdNameValue, GroovyUtils.createPositionFromTokens(createdName.start, createdName.stop), sourceFile));
                        processClassBody(anonymousType, anonDecl.classBody());
                    }
                }
            } else if (primary instanceof ThisPrmrAltContext && ((ThisPrmrAltContext)primary).THIS() != null) {
                JMethod enclosingMethod = scope.getEnclosingMethod();
                Position position = GroovyUtils.createPositionFromTokens(primary.start, primary.stop);
                if (enclosingMethod == null)
                    System.err.println("ERROR: 'this' outside method at " + position);
                else
                    enclosingMethod.addThisAccess(position);
            } else
	            primary.accept(this);
        }
        List<? extends PathElementContext> pathElems = pathExpr.pathElement();
        String methodName = null;
        int methodArity = -1;
        for (PathElementContext pathElem : pathElems) {

            ClosureOrLambdaExpressionContext closureOrLambdaExpr = pathElem.closureOrLambdaExpression();
            if (closureOrLambdaExpr != null)
                System.out.println("TODO: closureOrLambdaExpr = " + preview(closureOrLambdaExpr));

            IndexPropertyArgsContext indexPropertyArgs = pathElem.indexPropertyArgs();
            if (indexPropertyArgs != null)
                System.out.println("TODO: indexPropertyArgs = " + preview(indexPropertyArgs));

            NamedPropertyArgsContext namedPropertyArgs = pathElem.namedPropertyArgs();
            if (namedPropertyArgs != null)
                System.out.println("TODO: namedPropertyArgs = " + preview(namedPropertyArgs));

            logDebug(() -> "Path element = " + preview(pathElem));
            String text = pathElem.getText();
            if (text.startsWith("."))
                methodName = text.substring(1);

            ArgumentsContext args = pathElem.arguments();
            if (args != null) {
                EnhancedArgumentListInParContext eArgList = args.enhancedArgumentListInPar();
                if (eArgList != null) {
                    List<? extends EnhancedArgumentListElementContext> eArgs = eArgList.enhancedArgumentListElement();
                    if (eArgs != null) {
                        methodArity = eArgs.size();
                        for (EnhancedArgumentListElementContext eElem : eArgs) {
                            ExpressionListElementContext exprListElem = eElem.expressionListElement();
                            if (exprListElem != null)
                                exprListElem.expression().accept(this);
                        }
                    }
                }
                if (methodArity == -1)
                    methodArity = 0;
            }
            if (methodName != null && methodArity >= 0) {
                JMethod jm = scope.getEnclosingMethod();
                // TODO: containing block, base variable
                JMethodInvocation invo = new JMethodInvocation(sourceFile,
                        GroovyUtils.createPositionFromToken(pathExpr.start), methodName,
                        methodArity, jm, false, null, null);
                if (jm == null)
                    System.out.println("TODO: handle invocations outside methods");
                else
                    jm.invocations.add(invo);
                methodName = null;
                methodArity = -1;
            }
        }
        return null;
    }

    @Override
    public Void visitPostfixExpression(PostfixExpressionContext ctx) {
        if (ctx == null)
            return null;
        return visitPathExpression(ctx.pathExpression());
    }

//    @Override
//    public Void visitChildren(RuleNode ruleNode) {
//        int childCount = ruleNode.getChildCount();
//        for (int i = 0; i < childCount; i++) {
//            ParseTree pt = ruleNode.getChild(i);
//            System.out.println("pt: " + pt.getClass().getSimpleName());
//            pt.accept(this);
//        }
//        return null;
//    }

    /**
     * Process variable declarations (this may include field or parameter
     * declarations).
     * @param vDeclCtxt   the variable declaration context
     * @return            the list of all variables declared
     */
    private List<JVariable> processVariableDeclaration(VariableDeclarationContext vDeclCtxt) {
        List<JVariable> ret = new ArrayList<>();
        if (vDeclCtxt == null)
            return ret;

        GroovyModifierPack mp = new GroovyModifierPack(sourceFile, vDeclCtxt.modifiers());
        String vType = getType(vDeclCtxt.type());
        VariableDeclaratorsContext vDeclsCtx = vDeclCtxt.variableDeclarators();
        if (vDeclsCtx != null) {
            List<? extends VariableDeclaratorContext> vDecls = vDeclsCtx.variableDeclarator();
            for (VariableDeclaratorContext vDecl : vDecls) {
                IdentifierContext vId = vDecl.variableDeclaratorId().identifier();
                String vName = vId.getText();
                JVariable jv = new JVariable(sourceFile, GroovyUtils.createPositionFromToken(vId.start), vName, vType, true, mp);
                VariableInitializerContext vInit = vDecl.variableInitializer();
                if (vInit != null) {
                    EnhancedStatementExpressionContext eStmtExpr = vInit.enhancedStatementExpression();
                    if (eStmtExpr != null) {
                        StatementExpressionContext stmtExpr = eStmtExpr.statementExpression();
                        JStringConstant<JVariable> stringConstant = getInitialStringConstant(jv, stmtExpr);
                        if (stringConstant != null)
                            jv.initStringValue = stringConstant;
                        visitStatementExpression(stmtExpr);
                        StandardLambdaExpressionContext lambdaExpr = eStmtExpr.standardLambdaExpression();
                        if (lambdaExpr != null)
                            System.out.println("TODO: handle lambda expressions");
                    }
                }
                ret.add(jv);
            }
        }
        return ret;
    }

    private <T> JStringConstant<T> getInitialStringConstant(T decl, StatementExpressionContext stmtExpr) {
        if (stmtExpr instanceof CommandExprAltContext) {
            CommandExpressionContext cmdExpr = ((CommandExprAltContext) stmtExpr).commandExpression();
            if (cmdExpr != null) {
                ExpressionContext expr = cmdExpr.expression();
                if (expr instanceof PostfixExprAltContext) {
                    PostfixExprAltContext pExprAlt = (PostfixExprAltContext) expr;
                    PrimaryContext primary = pExprAlt.postfixExpression().pathExpression().primary();
                    if (primary instanceof LiteralPrmrAltContext) {
                        LiteralContext literal = ((LiteralPrmrAltContext) primary).literal();
                        if (literal instanceof StringLiteralAltContext) {
                            StringLiteralContext stringLiteral = ((StringLiteralAltContext) literal).stringLiteral();
                            String s = Utils.stripQuotes(stringLiteral.StringLiteral().getText());
                            if (debug)
                                System.out.println("Found string literal in initializer: " + s);
                            Position pos = GroovyUtils.createPositionFromTokens(stringLiteral.start, stringLiteral.stop);
                            return new JStringConstant<>(sourceFile, pos, decl, s);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Void visitIdentifier(IdentifierContext ctx) {
        if (ctx == null || ctx.Identifier() == null)
            return null;
        String name = ctx.Identifier().getText();
        JBlock block = scope.getEnclosingBlock();
        if (block != null) {
            JBlock.Result lookup = block.lookup(name);
            System.out.println("READ_ACCESS: " + lookup);
            if (lookup != null) {
                Position pos = GroovyUtils.createPositionFromTokens(ctx.start, ctx.stop);
                if (lookup.variable != null) {
                    scope.registerVarAccess(lookup.variable, pos, AccessType.READ, ctx);
                } else {
                    JField field = lookup.field;
                    if (field != null)
                        scope.registerFieldAccess(ctx, field.name, pos, sourceFile, AccessType.READ, field, debug);
                    else
                        System.err.println("Internal error during Groovy identifier lookup: " + lookup);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitFieldDeclaration(FieldDeclarationContext fieldDeclCtx) {
        JType jt = scope.getEnclosingType();
        VariableDeclarationContext varDecl = fieldDeclCtx.variableDeclaration();
        Collection<TypeUse> typeUses = new ArrayList<>();
        addTypeUsesInType(typeUses, varDecl.type());
        jt.typeUses.addAll(typeUses);
        for (JVariable jVar : processVariableDeclaration(varDecl)) {
            ModifierPack mp = jVar.mp;
            JField field = new JField(sourceFile, jVar.type, jVar.name, mp.getAnnotations(), jVar.pos, jt);
            jt.fields.add(field);
            if (mp.isStatic() && mp.isFinal()) {
                JStringConstant<JVariable> initStringValue = jVar.initStringValue;
                if (initStringValue != null) {
                    System.out.println("Adding initial string constant: " + initStringValue);
                    sourceFile.stringConstants.add(new JStringConstant<>(sourceFile, initStringValue.pos, field, initStringValue.value));
                }
            }
        }
        return null;
    }

    @Override
    public Void visitTerminal(TerminalNode terminalNode) {
        return null;
    }

    @Override
    public Void visitErrorNode(ErrorNode errorNode) {
        return null;
    }

    private static String getQualifiedName(QualifiedNameContext ctx) {
        StringJoiner sj = new StringJoiner(".");
        ctx.qualifiedNameElement().forEach(e -> sj.add(e.identifier().getText()));
        return sj.toString();
    }

    private void processClassDeclaration(GroovyModifierPack mp, ClassDeclarationContext classDecl) {
        IdentifierContext classId = classDecl.identifier();
        Position pos = GroovyUtils.createPositionFromToken(classId.start);
        JType parent = scope.getEnclosingType();
        boolean isInner = parent != null && !mp.isStatic();
        String name = classId.getText();

        List<TypeUse> superTypeUses = new ArrayList<>();
        addTypeUsesInTypeList(superTypeUses, classDecl.scs);
        addTypeUsesInTypeList(superTypeUses, classDecl.is);
        List<String> superTypes = superTypeUses.stream().map(tu -> tu.type).collect(Collectors.toList());
        JType jt = new JType(sourceFile, name, superTypes, mp.getAnnotations(),
                pos, scope.getEnclosingElement(), parent, isInner,
                mp.isGroovyPublic(), mp.isPrivate(), mp.isProtected(),
                mp.isAbstract(), mp.isFinal(), false, false, mp.isInterface(), mp.isEnum());
        jt.typeUses.addAll(mp.getAnnotationUses());
        jt.typeUses.addAll(superTypeUses);
        sourceFile.jTypes.add(jt);

        processClassBody(jt, classDecl.classBody());
    }

    /**
     * Auxiliary processor for (named/anonymous) class bodies.
     * @param jt          the source type representation
     * @param classBody   the class body
     */
    private void processClassBody(JType jt, ClassBodyContext classBody) {
        scope.enterTypeScope(jt, ((JType jt0) -> {
            for (ClassBodyDeclarationContext cDecl : classBody.classBodyDeclaration())
                cDecl.memberDeclaration().accept(this);
        }));
    }

    private void addTypeUsesInTypeList(List<TypeUse> target, TypeListContext tlc) {
        if (tlc == null)
            return;
        List<? extends TypeContext> types = tlc.type();
        if (types != null)
            for (TypeContext t : types)
                addTypeUsesInType(target, t);
    }

    private void addTypeUsesInType(Collection<TypeUse> target, TypeContext t) {
        if (t == null)
            return;
        ClassOrInterfaceTypeContext classOrIntfType = t.classOrInterfaceType();
        if (classOrIntfType != null) {
            QualifiedStandardClassNameContext stdClassName = classOrIntfType.qualifiedStandardClassName();
            if (stdClassName != null)
                addTypeUseFromName(target, stdClassName.getText(), stdClassName.start, stdClassName.stop);
            addTypeUseFromQClassName(target, classOrIntfType.qualifiedClassName());
            TypeArgumentsContext typeArgs = classOrIntfType.typeArguments();
            if (typeArgs != null)
                for (TypeArgumentContext typeArg : typeArgs.typeArgument())
                    addTypeUsesInType(target, typeArg.type());
        }
    }

    private static String getType(TypeContext typeCtx) {
        return typeCtx == null ? null : Utils.simplifyType(typeCtx.getText());
    }

    private void logDebug(Supplier<String> s) {
        if (debug)
            System.out.println(s.get());
    }
}
