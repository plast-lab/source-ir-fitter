package org.clyze.source.irfitter.source.groovy;

import groovyjarjarantlr4.v4.runtime.RuleContext;
import groovyjarjarantlr4.v4.runtime.Token;
import groovyjarjarantlr4.v4.runtime.tree.*;
import java.util.*;
import org.apache.groovy.parser.antlr4.GroovyParser.*;
import org.apache.groovy.parser.antlr4.GroovyParserBaseVisitor;
import org.clyze.persistent.model.Position;
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

    GroovyTreeVisitor(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
        // Add default imports.
        String[] defaultPackages = new String[] { "groovy.lang", "groovy.util" , "java.lang" , "java.util" , "java.net" , "java.io" };
        for (String defaultPackage : defaultPackages)
            sourceFile.imports.add(new Import(defaultPackage, true, false));
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
        logDebug("packageName=" + sourceFile.packageName);
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
            sourceFile.imports.add(new Import(id, isAsterisk, isStatic));
            return null;
        }

        TypeDeclarationContext typeDecl = ssc.typeDeclaration();
        if (typeDecl != null) {
            GroovyModifierPack mp = new GroovyModifierPack(typeDecl.classOrInterfaceModifiersOpt());
            processClassDeclaration(mp, typeDecl.classDeclaration());
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
            processFieldDeclaration(fieldCtx);
        ClassDeclarationContext classCtx = memCtx.classDeclaration();
        if (classCtx != null) {
            GroovyModifierPack mp = new GroovyModifierPack(memCtx.modifiersOpt());
            processClassDeclaration(mp, classCtx);
        }
        if (methCtx == null && fieldCtx == null && classCtx == null)
            System.out.println("WARNING: unhandled member node in " + scope.getEnclosingType());
        return null;
    }

    @Override
    public Void visitMethodDeclaration(MethodDeclarationContext ctx) {
        String name = ctx.methodName().identifier().getText();
        // Return types may be missing.
        ReturnTypeContext retCtx = ctx.returnType();
        String retType = retCtx == null ? null : Utils.simplifyType(retCtx.getText());
        System.out.println("Groovy method: " + name + ", return type: " + retType);
        List<JParameter> parameters = new LinkedList<>();
        FormalParametersContext paramsCtx = ctx.formalParameters();
        if (paramsCtx == null)
            System.err.println("WARNING: no formal parameters for " + name);
        else {
            FormalParameterListContext paramsListCtx = paramsCtx.formalParameterList();
            if (paramsListCtx != null) {
                for (FormalParameterContext frmCtx : paramsListCtx.formalParameter()) {
                    String paramName = frmCtx.variableDeclaratorId().identifier().getText();
                    String paramType = getType(frmCtx.type());
                    JParameter param = new JParameter(paramName, paramType);
                    logDebug("param: " + param);
                    parameters.add(param);
                }
            }
        }
        Position pos = createPositionFromToken(ctx.methodName().start);
        Position outerPos = createPositionFromTokens(ctx.start, ctx.stop);
        JType jt = scope.getEnclosingType();
        JMethod jm = new JMethod(sourceFile, name, retType, parameters, pos, outerPos, jt);
        if (jt == null)
            System.out.println("WARNING: top-level Groovy methods are not yet supported.");
        else {
            jt.methods.add(jm);
            scope.enterMethodScope(jm, (jm0 -> visitMethodBody(ctx.methodBody())));
        }

        return null;
    }

    @Override
    public Void visitMethodBody(MethodBodyContext methodBody) {
        if (methodBody == null)
            return null;
        BlockContext block = methodBody.block();
        if (block != null) {
            BlockStatementsOptContext blockStatementsOpt = block.blockStatementsOpt();
            if (blockStatementsOpt != null) {
                BlockStatementsContext blockStatements = blockStatementsOpt.blockStatements();
                if (blockStatements != null) {
                    for (BlockStatementContext blockStmt : blockStatements.blockStatement()) {
                        LocalVariableDeclarationContext localVar = blockStmt.localVariableDeclaration();
                        if (localVar != null) {
                            for (JVariable jVar : processVariableDeclaration(localVar.variableDeclaration()))
                                System.out.println("TODO: local variable " + jVar.name);
                        }
                        StatementContext stmt = blockStmt.statement();
                        if (stmt != null)
                            stmt.accept(this);
                    }
                }
            }
        }
        return null;
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
        System.out.println("visitExpression(): " + ctx.getClass().getSimpleName());
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
        for (JVariable jVar : processVariableDeclaration(ctx.variableDeclaration())) {
            System.out.println("Variable: " + jVar.name);
        }
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
            logDebug("primary = " + preview(primary));
            if (primary instanceof NewPrmrAltContext) {
                NewPrmrAltContext newPAC = (NewPrmrAltContext) primary;
                CreatorContext creator = newPAC.creator();
                logDebug("Creator = " + preview(creator));
                AnonymousInnerClassDeclarationContext anonDecl = creator.anonymousInnerClassDeclaration();
                if (anonDecl != null) {
                    CreatedNameContext createdName = creator.createdName();
                    String createdNameValue = createdName.getText();
                    logDebug("Anonymous class declaration: " + preview(anonDecl) + ", created-name = " + createdNameValue);
                    JType enclosingType = scope.getEnclosingType();
                    if (enclosingType == null)
                        System.out.println("TODO: anonymous classes outside class declarations");
                    else if (createdNameValue == null)
                        System.out.println("WARNING: no created-name information for anonymous class: " + preview(anonDecl));
                    else {
                        List<String> superTypes = Collections.singletonList(createdNameValue);
                        Position pos = createPositionFromToken(createdName.start);
                        JType anonymousType = enclosingType.createAnonymousClass(sourceFile,
                                superTypes, scope.getEnclosingElement(), pos, true);
                        logDebug("Adding type [anonymous]: " + anonymousType);
                        sourceFile.jTypes.add(anonymousType);
                        processClassBody(anonymousType, anonDecl.classBody());
                    }
                }
            }
            primary.accept(this);
        }
        List<? extends PathElementContext> pathElems = pathExpr.pathElement();
        String methodName = null;
        int methodArity = -1;
        for (PathElementContext pathElem : pathElems) {

            ClosureOrLambdaExpressionContext closureOrLambdaExpr = pathElem.closureOrLambdaExpression();
            if (closureOrLambdaExpr != null)
                System.out.println("TODO: closureOrLambdaExpr = " + closureOrLambdaExpr.getText());

            IndexPropertyArgsContext indexPropertyArgs = pathElem.indexPropertyArgs();
            if (indexPropertyArgs != null)
                System.out.println("TODO: indexPropertyArgs = " + indexPropertyArgs.getText());

            NamedPropertyArgsContext namedPropertyArgs = pathElem.namedPropertyArgs();
            if (namedPropertyArgs != null)
                System.out.println("TODO: namedPropertyArgs = " + namedPropertyArgs.getText());

            String text = pathElem.getText();
            logDebug("Path element = " + text);
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
                JMethodInvocation invo = new JMethodInvocation(sourceFile,
                        createPositionFromToken(pathExpr.start), methodName,
                        methodArity, jm, false);
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
        List<JVariable> ret = new LinkedList<>();
        if (vDeclCtxt == null)
            return ret;
        String vType = getType(vDeclCtxt.type());
        VariableDeclaratorsContext vDeclsCtx = vDeclCtxt.variableDeclarators();
        if (vDeclsCtx != null) {
            List<? extends VariableDeclaratorContext> vDecls = vDeclsCtx.variableDeclarator();
            for (VariableDeclaratorContext vDecl : vDecls) {
                IdentifierContext vId = vDecl.variableDeclaratorId().identifier();
                String vName = vId.getText();
                JVariable jv = new JVariable(sourceFile, createPositionFromToken(vId.start), vName, vType);
                VariableInitializerContext vInit = vDecl.variableInitializer();
                if (vInit != null) {
                    EnhancedStatementExpressionContext eStmtExpr = vInit.enhancedStatementExpression();
                    if (eStmtExpr != null) {
                        visitStatementExpression(eStmtExpr.statementExpression());
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

    private void processFieldDeclaration(FieldDeclarationContext fieldDeclCtx) {
        JType jt = scope.getEnclosingType();
        for (JVariable jVar : processVariableDeclaration(fieldDeclCtx.variableDeclaration()))
            jt.fields.add(new JField(sourceFile, jVar.type, jVar.name, jVar.pos, jt));
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
        Position pos = createPositionFromToken(classId.start);
        JType parent = scope.getEnclosingType();
        boolean isInner = parent != null && !mp.isStatic();
        String name = classId.getText();

        List<String> superTypes = new LinkedList<>();
        updateFromTypeList(superTypes, classDecl.scs);
        updateFromTypeList(superTypes, classDecl.is);
        JType jt = new JType(sourceFile, name, superTypes, parent, pos, scope.getEnclosingElement(), isInner,
                mp.isGroovyPublic(), mp.isPrivate(), mp.isProtected(),
                mp.isAbstract(), mp.isFinal(), false);
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

    private static void updateFromTypeList(List<String> target, TypeListContext tlc) {
        if (tlc == null)
            return;
        List<? extends TypeContext> types = tlc.type();
        if (types != null)
            for (TypeContext t : types)
                target.add(t.getText());
    }

    private static String getType(TypeContext typeCtx) {
        return typeCtx == null ? null : Utils.simplifyType(typeCtx.getText());
    }

    private Position createPositionFromToken(Token token) {
        int startLine = token.getLine();
        int startColumn = token.getCharPositionInLine() + 1;
        int endColumn = startColumn + token.getText().length();
        return new Position(startLine, startLine, startColumn, endColumn);
    }

    private Position createPositionFromTokens(Token start, Token end) {
        Position startPos = createPositionFromToken(start);
        Position endPos = createPositionFromToken(end);
        return new Position(startPos.getStartLine(), endPos.getEndLine(),
                startPos.getStartColumn(), endPos.getEndColumn());
    }

    private void logDebug(String s) {
        if (sourceFile.debug)
            System.out.println(s);
    }
}
