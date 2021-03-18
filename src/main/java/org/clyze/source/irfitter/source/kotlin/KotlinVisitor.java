package org.clyze.source.irfitter.source.kotlin;

import java.util.*;
import org.antlr.grammars.KotlinParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.*;
import org.clyze.source.irfitter.source.model.*;
import org.antlr.grammars.KotlinParser.*;
import org.clyze.persistent.model.Position;

/**
 * The AST visitor that reads Kotlin sources.
 * https://github.com/antlr/grammars-v4/blob/master/kotlin/kotlin-formal/KotlinParser.g4
 */
public class KotlinVisitor extends KotlinParserBaseVisitor<Void> {
    private static final String[] DEFAULT_IMPORTS = new String[] {
            // Taken from https://kotlinlang.org/docs/reference/packages.html
            "kotlin", "kotlin.annotation", "kotlin.collections",
            "kotlin.comparisons", "kotlin.io", "kotlin.ranges",
            "kotlin.sequences", "kotlin.text", "java.lang", "kotlin.jvm",
            "kotlin.js",
            // Used in the implementation
            "java.util"
    };

    private final SourceFile sourceFile;
    /** The scoping object. */
    private final Scope scope = new Scope();

    public KotlinVisitor(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
        for (String i : DEFAULT_IMPORTS)
            sourceFile.imports.add(new Import(i, true, false));
    }

    // Common functionality, shared by class and object declarations.
    private void visitTypeDeclaration(String name, Token positionToken,
                                      ModifiersContext mc, DelegationSpecifiersContext delegSpecs,
                                      PrimaryConstructorContext primaryConstr,
                                      ClassBodyContext cBody) {
        if (sourceFile.debug)
            System.out.println("Type declaration: " + name);
        JType parent = scope.getEnclosingType();
        Position pos = KotlinUtils.createPositionFromToken(positionToken);
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, mc);
        boolean isAnonymous = false;

        Set<TypeUsage> delegTypeUsages = new HashSet<>();
        List<String> superTypes = new LinkedList<>();
        if (delegSpecs != null)
            for (AnnotatedDelegationSpecifierContext aDSpec : delegSpecs.annotatedDelegationSpecifier()) {
                delegTypeUsages.addAll((new KotlinModifierPack(sourceFile, aDSpec.annotation())).getAnnotationUses());
                DelegationSpecifierContext dSpec = aDSpec.delegationSpecifier();
                UserTypeContext dSpecType = dSpec.userType();
                if (dSpecType != null) {
                    addTypeUsagesInUserType(delegTypeUsages, dSpecType);
                    superTypes.add(getType(dSpecType));
                }
            }

        JType jt = new JType(sourceFile, name, superTypes, mp.getAnnotations(),
                pos, scope.getEnclosingElement(), parent, mp.isInner(), mp.isPublic(),
                mp.isPrivate(), mp.isProtected(), mp.isAbstract(), mp.isFinal(),
                isAnonymous);
        jt.typeUsages.addAll(mp.getAnnotationUses());
        jt.typeUsages.addAll(delegTypeUsages);
        sourceFile.jTypes.add(jt);

        if (sourceFile.debug)
            System.out.println("Created type: " + jt);

        scope.enterTypeScope(jt, (jt0 -> {
            if (primaryConstr != null)
                visitPrimaryConstructor(primaryConstr);
            if (cBody != null)
                visitClassBody(cBody);
        }));
    }

    @Override
    public Void visitObjectDeclaration(ObjectDeclarationContext objDecl) {
        TerminalNode idNode = objDecl.simpleIdentifier().Identifier();
        // This may happen when parsing fails for some source code segments.
        if (idNode == null)
            return null;
        String name = idNode.getText();
        Token positionToken = idNode.getSymbol();
        visitTypeDeclaration(name, positionToken, objDecl.modifiers(),
                objDecl.delegationSpecifiers(), null, objDecl.classBody());
        return null;
    }

    @Override
    public Void visitClassDeclaration(ClassDeclarationContext cdc) {
        TerminalNode idNode = cdc.simpleIdentifier().Identifier();
        String name = idNode.getText();
        Token positionToken = idNode.getSymbol();
        visitTypeDeclaration(name, positionToken, cdc.modifiers(),
                cdc.delegationSpecifiers(), cdc.primaryConstructor(), cdc.classBody());
        return null;
    }

    @Override
    public Void visitPrimaryConstructor(PrimaryConstructorContext primaryConstr) {
        ClassParametersContext classParams = primaryConstr.classParameters();
        if (classParams != null)
            for (ClassParameterContext classParam : classParams.classParameter()) {
                JType jt = scope.getEnclosingType();
                if (jt == null)
                    System.out.println("ERROR: primary constructor class parameter outside type: " + classParam.getText());
                else {
                    TypeContext fType = classParam.type();
                    System.out.println("fType=" + fType.getText());
                    SimpleIdentifierContext fId = classParam.simpleIdentifier();
                    String fName = fId.getText();
                    Set<String> annotations = (new KotlinModifierPack(sourceFile, classParam.modifiers())).getAnnotations();
                    Position pos = KotlinUtils.createPositionFromTokens(fId.start, fId.stop);
                    JField srcField = new JField(sourceFile, getType(fType), fName, annotations, pos, jt);
                    if (sourceFile.debug)
                        System.out.println("Adding field: " + srcField);
                    jt.fields.add(srcField);
                    addTypeUsagesInType(jt.typeUsages, fType);
                }
            }
        return null;
    }

    @Override
    public Void visitCompanionObject(CompanionObjectContext companionObj) {
        Token positionToken = companionObj.OBJECT().getSymbol();
        JType jt = scope.getEnclosingType();
        if (jt == null) {
            System.out.println("ERROR: top-level companion object found.");
            return null;
        }
        String name;
        try {
            name = companionObj.simpleIdentifier().Identifier().getText();
        } catch (Exception ignored) {
            name = "Companion";
        }
        if (sourceFile.debug)
            System.out.println("Registering companion type: " + name);
        visitTypeDeclaration(name, positionToken, companionObj.modifiers(),
                companionObj.delegationSpecifiers(), null, companionObj.classBody());
        return null;
    }

    @Override
    public Void visitDeclaration(DeclarationContext decl) {
        ClassDeclarationContext classMemDecl = decl.classDeclaration();
        if (classMemDecl != null)
            return visitClassDeclaration(classMemDecl);
        FunctionDeclarationContext funMemDecl = decl.functionDeclaration();
        if (funMemDecl != null)
            return visitFunctionDeclaration(funMemDecl);
        ObjectDeclarationContext objMemDecl = decl.objectDeclaration();
        if (objMemDecl != null) {
            System.out.println("WARNING: object members are not yet supported.");
            return null;
        }
        PropertyDeclarationContext propMemDecl = decl.propertyDeclaration();
        if (propMemDecl != null)
            return visitPropertyDeclaration(propMemDecl);
        TypeAliasContext typeAlias = decl.typeAlias();
        if (typeAlias != null) {
            System.out.println("WARNING: type aliases are not yet supported.");
            return null;
        }
        return null;
    }

    @Override
    public Void visitClassMemberDeclaration(ClassMemberDeclarationContext memDecl) {
        CompanionObjectContext companionObj = memDecl.companionObject();
        if (companionObj != null) {
            visitCompanionObject(companionObj);
            return null;
        }
        DeclarationContext decl = memDecl.declaration();
        if (decl != null)
            visitDeclaration(decl);
        AnonymousInitializerContext anonymousInit = memDecl.anonymousInitializer();
        if (anonymousInit != null) {
            System.out.println("WARNING: anonymous initializers are not yet supported.");
        }
        SecondaryConstructorContext secondaryConstructor = memDecl.secondaryConstructor();
        if (secondaryConstructor != null) {
            System.out.println("WARNING: secondary constructors are not yet supported.");
        }
        return null;
    }

    @Override
    public Void visitPropertyDeclaration(PropertyDeclarationContext propMemDecl) {
        if (sourceFile.debug)
            System.out.println("Visiting property declaration: " + propMemDecl);
        JType jt = scope.getEnclosingType();
        ModifiersContext modifiers = propMemDecl.modifiers();
        VariableDeclarationContext vDecl = propMemDecl.variableDeclaration();
        ExpressionContext vExpr = propMemDecl.expression();
        if (vDecl != null)
            processFieldDeclaration(jt, modifiers, vDecl, vExpr);
        else {
            MultiVariableDeclarationContext vDecls = propMemDecl.multiVariableDeclaration();
            for (VariableDeclarationContext vDecl0 : vDecls.variableDeclaration())
                processFieldDeclaration(jt, modifiers, vDecl0, vExpr);
        }
        return null;
    }

    @Override
    public Void visitClassBody(ClassBodyContext cBody) {
        ClassMemberDeclarationsContext memDecls = cBody.classMemberDeclarations();
        if (memDecls != null) {
            for (ClassMemberDeclarationContext memDecl : memDecls.classMemberDeclaration())
                visitClassMemberDeclaration(memDecl);
        }
        return null;
    }

    private void processFieldDeclaration(JType jt, ModifiersContext modifiers,
                                         VariableDeclarationContext fDecl,
                                         ExpressionContext vExpr) {
        SimpleIdentifierContext id = fDecl.simpleIdentifier();
        String fName = id.getText();
        TypeContext fType = fDecl.type();
        String fTypeName = getType(fType);
        if (sourceFile.debug)
            System.out.println("Visiting field declaration: " + fTypeName + " " + fName);
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, fDecl.annotation(), modifiers);
        JField srcField = new JField(sourceFile, fTypeName, fName, mp.getAnnotations(), KotlinUtils.createPositionFromToken(id.start), jt);
        if (sourceFile.debug)
            System.out.println("Found source field: " + srcField + " : " + mp.toString());
        if (jt == null)
            System.out.println("ERROR: top-level field found: " + srcField);
        else {
            jt.fields.add(srcField);
            addTypeUsagesInType(jt.typeUsages, fType);
        }
        if (vExpr != null) {
            if (mp.isConst()) {
                StringScanner<JField> stringScanner = new StringScanner<>(sourceFile, srcField);
                vExpr.accept(stringScanner);
                Collection<JStringConstant<JField>> stringConstants = stringScanner.strs;
                if (stringConstants != null) {
                    if (sourceFile.debug)
                        System.out.println("Field " + srcField + " is initialized by string constants: " + stringConstants);
                    sourceFile.stringConstants.addAll(stringConstants);
                }
            }
            vExpr.accept(this);
        }
    }

    @Override
    public Void visitFunctionDeclaration(FunctionDeclarationContext funMemDecl) {
        SimpleIdentifierContext fNameCtx = funMemDecl.simpleIdentifier();
        String fName = fNameCtx.getText();
        TypeContext fmdType = funMemDecl.type();
        String retType = getType(fmdType);
        Collection<TypeUsage> retTypeUsages = new HashSet<>();
        addTypeUsagesInType(retTypeUsages, fmdType);
        List<JParameter> parameters = new LinkedList<>();
        Collection<TypeUsage> paramTypeUsages = new HashSet<>();
        FunctionValueParametersContext funParamsCtx = funMemDecl.functionValueParameters();
        if (funParamsCtx != null) {
            for (FunctionValueParameterContext funParamCtx : funParamsCtx.functionValueParameter()) {
                ParameterContext funParam = funParamCtx.parameter();
                TypeContext funTypeCtx = funParam.type();
                String paramName = funParam.simpleIdentifier().getText();
                String funType = getType(funTypeCtx);
                Position paramPos = KotlinUtils.createPositionFromTokens(funParam.start, funParam.stop);
                parameters.add(new JParameter(paramName, funType, paramPos));
                addTypeUsagesInType(paramTypeUsages, funTypeCtx);
            }
        }
        Position outerPos = KotlinUtils.createPositionFromTokens(funMemDecl.start, funMemDecl.stop);
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, funMemDecl.modifiers());
        JType jt = scope.getEnclosingType();
        if (jt == null)
            System.out.println("TODO: top-level function " + fName);
        else {
            jt.methods.add(new JMethod(jt.srcFile, fName, retType, parameters,
                    mp.getAnnotations(), outerPos, jt,
                    KotlinUtils.createPositionFromToken(fNameCtx.start)));
            Utils.addSigTypeRefs(jt, retTypeUsages, paramTypeUsages);
        }
        return null;
    }

    /**
     * Simplify a type string.
     * @param typeCtx   a parse node that contains a type string
     * @return          the simplified type
     */
    private String getType(ParserRuleContext typeCtx) {
        return typeCtx == null ? null : Utils.simplifyType(typeCtx.getText());
    }

    @Override
    public Void visitKotlinFile(KotlinFileContext kfc) {
        kfc.importList().importHeader().forEach(ihc -> ihc.accept(this));
        PackageHeaderContext phc = kfc.packageHeader();
        if (phc != null) {
            IdentifierContext identifier = phc.identifier();
            if (identifier != null)
                sourceFile.packageName = getQualifiedName(identifier);
        }
        kfc.topLevelObject().forEach(this::visitChildren);
        return null;
    }

    private static boolean isColonColonClass(NavigationSuffixContext ctx) {
        if (ctx == null)
            return false;
        MemberAccessOperatorContext accessOp = ctx.memberAccessOperator();
        if (accessOp != null && accessOp.COLONCOLON() != null) {
            SimpleIdentifierContext simpleId = ctx.simpleIdentifier();
            if (simpleId != null) {
                TerminalNode id = simpleId.Identifier();
                return id != null && "class".equals(id.getText());
            }
        }
        return false;
    }

    private static String getClassType(PostfixUnaryExpressionContext ctx) {
        String type = getClassType(ctx.getText());
        if (type != null)
            return type;
        PrimaryExpressionContext primaryExpression = ctx.primaryExpression();
        List<PostfixUnarySuffixContext> unarySuffixList = ctx.postfixUnarySuffix();
        if (primaryExpression != null && unarySuffixList != null) {
            SimpleIdentifierContext simpleId = primaryExpression.simpleIdentifier();
            if (simpleId != null) {
                int listSize = unarySuffixList.size();
                if (listSize > 0) {
                    boolean isCC = isColonColonClass(unarySuffixList.get(0).navigationSuffix());
                    // Two cases for the suffix: "'::class" or "::class.java".
                    if ((listSize == 1 && isCC) || (listSize == 2 && isCC && ".java".equals(unarySuffixList.get(1).getText()))) {
                        type = simpleId.getText();
                    }
                }
            }
        }
        return type;
    }

    private static String getClassType(String text) {
        if (text == null)
            return null;
        if (text.endsWith("::class"))
            return text.substring(0, text.length() - "::class".length());
        if (text.endsWith("::class.java"))
            return text.substring(0, text.length() - "::class.java".length());
        return null;
    }

    @Override
    public Void visitPostfixUnaryExpression(PostfixUnaryExpressionContext ctx) {
        String type = getClassType(ctx);
        if (type != null) {
            if (sourceFile.debug)
                System.out.println("Encountered type reference: " + type);
            JType jt = scope.getEnclosingType();
            if (jt == null)
                System.out.println("ERROR: cannot process top-level usage for type " + type);
            else
                jt.typeUsages.add(new TypeUsage(type, KotlinUtils.createPositionFromTokens(ctx.start, ctx.stop), sourceFile));
        }
        return super.visitPostfixUnaryExpression(ctx);
    }

    @Override
    public Void visitCallableReference(CallableReferenceContext ctx) {
        System.out.println("Callable: " + ctx.getText());
        return super.visitCallableReference(ctx);
    }

    @Override
    public Void visitTopLevelObject(TopLevelObjectContext tloc) {
        DeclarationContext dc = tloc.declaration();
        ClassDeclarationContext cdc = dc.classDeclaration();
        if (cdc != null)
            visitClassDeclaration(cdc);
        else {
            if (dc.functionDeclaration() != null) {
                System.out.println("WARNING: top-level functions are not yet supported.");
            }
            else if (dc.objectDeclaration() != null) {
                System.out.println("WARNING: top-level objects are not yet supported.");
            } else
                System.out.println("WARNING: unknown top-level declaration.");
        }
        return null;
    }

    @Override
    public Void visitImportHeader(ImportHeaderContext ihc) {
        boolean isAsterisk = ihc.MULT() != null;
        String importName = ihc.identifier().getText();
        boolean isStatic = false;
        if (!isAsterisk) {
            // Assume imports of the form "import a.b.c" are static imports,
            // while "import a.b.C" are not.
            int dotIdx = importName.lastIndexOf('.');
            isStatic = dotIdx >= 0 && importName.length() > dotIdx && Character.isLowerCase(importName.charAt(dotIdx + 1));
        }
        Import srcImport = new Import(importName, isAsterisk, isStatic);
        if (sourceFile.debug)
            System.out.println("Found source import: " + srcImport);
        sourceFile.imports.add(srcImport);
        return null;
    }

    // Copy of similar method in Groovy parser.
    private static String getQualifiedName(IdentifierContext ctx) {
        StringJoiner sj = new StringJoiner(".");
        ctx.simpleIdentifier().forEach(e -> sj.add(e.getText()));
        return sj.toString();
    }

    private void addTypeUsagesInType(Collection<TypeUsage> target, TypeContext t) {
        if (t == null)
            return;
        addTypeUsagesInParenType(target, t.parenthesizedType());
        addTypeUsagesInNullableType(target, t.nullableType());
        addTypeUsagesInRefType(target, t.typeReference());

    }
    private void addTypeUsagesInParenType(Collection<TypeUsage> target, ParenthesizedTypeContext t) {
        if (t != null)
            addTypeUsagesInType(target, t.type());
    }
    private void addTypeUsagesInNullableType(Collection<TypeUsage> target, NullableTypeContext t) {
        if (t != null) {
            addTypeUsagesInParenType(target, t.parenthesizedType());
            addTypeUsagesInRefType(target, t.typeReference());
        }
    }
    private void addTypeUsagesInUserType(Collection<TypeUsage> target, UserTypeContext userType) {
        if (userType != null) {
            List<SimpleUserTypeContext> simpleUserTypes = userType.simpleUserType();
            if (simpleUserTypes != null)
                for (SimpleUserTypeContext simpleUserType : simpleUserTypes) {
                    SimpleIdentifierContext simpleId = simpleUserType.simpleIdentifier();
                    if (simpleId != null)
                        target.add(new TypeUsage(simpleId.getText(), KotlinUtils.createPositionFromTokens(simpleId.start, simpleId.stop), sourceFile));
                    TypeArgumentsContext typeArgs = simpleUserType.typeArguments();
                    if (typeArgs != null)
                        for (TypeProjectionContext typeProj : typeArgs.typeProjection())
                            addTypeUsagesInType(target, typeProj.type());
                }
        }

    }

    private void addTypeUsagesInRefType(Collection<TypeUsage> target, TypeReferenceContext t) {
        if (t != null)
            addTypeUsagesInUserType(target, t.userType());
    }
}
