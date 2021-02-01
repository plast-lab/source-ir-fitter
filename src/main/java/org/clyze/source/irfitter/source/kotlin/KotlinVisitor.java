package org.clyze.source.irfitter.source.kotlin;

import java.util.*;
import org.antlr.grammars.KotlinParserBaseVisitor;
import org.antlr.v4.runtime.tree.*;
import org.clyze.source.irfitter.source.model.*;
import org.antlr.grammars.KotlinParser.*;
import org.clyze.persistent.model.Position;

/**
 * The AST visitor that reads Kotlin sources.
 * https://github.com/gfour/grammars-v4/blob/master/kotlin/kotlin-formal/KotlinParser.g4
 */
public class KotlinVisitor extends KotlinParserBaseVisitor<Void> {
    private final SourceFile sourceFile;
    /** The scoping object. */
    private final Scope scope = new Scope();

    public KotlinVisitor(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
    }

    // Common functionality, shared by class and object declarations.
    private void visitTypeDeclaration(SimpleIdentifierContext id,
                                      ModifiersContext mc, DelegationSpecifiersContext delegSpecs, ClassBodyContext cBody) {
        TerminalNode idNode = id.Identifier();
        String name = idNode.getText();
        if (sourceFile.debug)
            System.out.println("Type declaration: " + name);
        JType parent = scope.getEnclosingType();
        Position pos = KotlinUtils.createPositionFromToken(idNode.getSymbol());
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, mc);
        boolean isAnonymous = false;

        if (delegSpecs != null)
            for (AnnotatedDelegationSpecifierContext aDSpec : delegSpecs.annotatedDelegationSpecifier())
                System.out.println("TODO: delegation specifier " + aDSpec.getText());

        // TODO: super types
        List<String> superTypes = new LinkedList<>();
        JType jt = new JType(sourceFile, name, superTypes, mp.getAnnotations(),
                pos, scope.getEnclosingElement(), parent, mp.isInner(), mp.isPublic(),
                mp.isPrivate(), mp.isProtected(), mp.isAbstract(), mp.isFinal(),
                isAnonymous);
        jt.typeUsages.addAll(mp.getAnnotationUses());
        sourceFile.jTypes.add(jt);

        if (cBody != null)
            scope.enterTypeScope(jt, (jt0 -> visitClassBody(cBody)));
    }

    @Override
    public Void visitObjectDeclaration(ObjectDeclarationContext objDecl) {
        visitTypeDeclaration(objDecl.simpleIdentifier(), objDecl.modifiers(),
                objDecl.delegationSpecifiers(), objDecl.classBody());
        return null;
    }

    @Override
    public Void visitClassDeclaration(ClassDeclarationContext cdc) {
        visitTypeDeclaration(cdc.simpleIdentifier(), cdc.modifiers(),
                cdc.delegationSpecifiers(), cdc.classBody());
        return null;
    }

    @Override
    public Void visitClassBody(ClassBodyContext cBody) {
        ClassMemberDeclarationsContext memDecls = cBody.classMemberDeclarations();
        if (memDecls != null) {
            for (ClassMemberDeclarationContext memDecl : memDecls.classMemberDeclaration()) {
                if (memDecl.companionObject() != null) {
                    System.out.println("TODO: handle companion objects.");
                    continue;
                }
                DeclarationContext decl = memDecl.declaration();
                ClassDeclarationContext classMemDecl = decl.classDeclaration();
                if (classMemDecl != null) {
                    System.out.println("TODO: handle nested classes.");
                    continue;
                }
                FunctionDeclarationContext funMemDecl = decl.functionDeclaration();
                if (funMemDecl != null) {
                    visitFunctionDeclaration(funMemDecl);
                    continue;
                }
                ObjectDeclarationContext objMemDecl = decl.objectDeclaration();
                if (objMemDecl != null) {
                    System.out.println("TODO: handle object members.");
                    continue;
                }
                PropertyDeclarationContext propMemDecl = decl.propertyDeclaration();
                if (propMemDecl != null) {
                    JType jt = scope.getEnclosingType();
                    VariableDeclarationContext vDecl = propMemDecl.variableDeclaration();
                    if (vDecl != null)
                        processFieldDeclaration(jt, vDecl);
                    else {
                        MultiVariableDeclarationContext vDecls = propMemDecl.multiVariableDeclaration();
                        for (VariableDeclarationContext vDecl0 : vDecls.variableDeclaration())
                            processFieldDeclaration(jt, vDecl0);
                    }
                    System.out.println("TODO: fully handle property members.");
                    continue;
                }
                TypeAliasContext typeAlias = decl.typeAlias();
                if (classMemDecl != null) {
                    System.out.println("TODO: handle type aliases.");
                }
            }
        }
        return null;
    }

    private void processFieldDeclaration(JType jt, VariableDeclarationContext fDecl) {
        SimpleIdentifierContext id = fDecl.simpleIdentifier();
        String fName = id.getText();
        String fType = getType(fDecl.type());
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, fDecl.annotation());
        JField srcField = new JField(sourceFile, fType, fName, mp.getAnnotations(), KotlinUtils.createPositionFromToken(id.start), jt);
        if (sourceFile.debug)
            System.out.println("Found source field: " + srcField);
        jt.fields.add(srcField);
    }

//    @Override
//    public Void visitChildren(RuleNode ruleNode) {
//        visitTree(ruleNode);
//        return null;
//    }

    @Override
    public Void visitFunctionDeclaration(FunctionDeclarationContext funMemDecl) {
        SimpleIdentifierContext fNameCtx = funMemDecl.simpleIdentifier();
        String fName = fNameCtx.getText();
        String retType = getType(funMemDecl.type());
        List<JParameter> parameters = new LinkedList<>();
        FunctionValueParametersContext funParamsCtx = funMemDecl.functionValueParameters();
        if (funParamsCtx != null) {
            for (FunctionValueParameterContext funParamCtx : funParamsCtx.functionValueParameter()) {
                ParameterContext funParam = funParamCtx.parameter();
                TypeContext funTypeCtx = funParam.type();
                String paramName = funParam.simpleIdentifier().getText();
                String funType = getType(funTypeCtx);
                parameters.add(new JParameter(paramName, funType));
            }
        }
        Position outerPos = KotlinUtils.createPositionFromTokens(funMemDecl.start, funMemDecl.stop);
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, funMemDecl.modifiers());
        JType jt = scope.getEnclosingType();
        if (jt == null)
            System.out.println("TODO: top-level function " + fName);
        else
            jt.methods.add(new JMethod(jt.srcFile, fName, retType, parameters,
                    mp.getAnnotations(), outerPos, jt,
                    KotlinUtils.createPositionFromToken(fNameCtx.start)));
        return null;
    }

    private String getType(TypeContext typeCtx) {
        return typeCtx == null ? null : Utils.simplifyType(typeCtx.getText());
    }

    @Override
    public Void visitKotlinFile(KotlinFileContext kfc) {
        kfc.importList().importHeader().forEach(ihc -> ihc.accept(this));
        PackageHeaderContext phc = kfc.packageHeader();
        if (phc != null)
            sourceFile.packageName = getQualifiedName(phc.identifier());
        kfc.topLevelObject().forEach(this::visitChildren);
        return null;
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
}
