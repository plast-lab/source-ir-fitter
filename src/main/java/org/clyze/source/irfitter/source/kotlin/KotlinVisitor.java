package org.clyze.source.irfitter.source.kotlin;

import java.util.*;
import org.antlr.v4.runtime.tree.*;
import org.clyze.source.irfitter.source.model.*;
import org.antlr.grammars.KotlinParser.*;
import org.clyze.persistent.model.Position;

/**
 * The AST visitor that reads Kotlin sources.
 * https://github.com/gfour/grammars-v4/blob/master/kotlin/kotlin-formal/KotlinParser.g4
 */
public class KotlinVisitor extends Scope implements ParseTreeVisitor<Object> {
    private final SourceFile sourceFile;

    public KotlinVisitor(SourceFile sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public Object visit(ParseTree parseTree) {
        visitTree(parseTree);
        return null;
    }

    private void processClassDeclaration(ClassDeclarationContext cdc) {
        TerminalNode classId = cdc.simpleIdentifier().Identifier();
        String name = classId.getText();
        if (sourceFile.debug)
            System.out.println("Class declaration: " + name);

        JType parent = getEnclosingType();
        Position pos = KotlinUtils.createPositionFromToken(classId.getSymbol());
        KotlinModifierPack mp = new KotlinModifierPack(sourceFile, cdc.modifiers());
        boolean isAnonymous = false;

        List<String> superTypes = new LinkedList<>();
        DelegationSpecifiersContext delegSpecs = cdc.delegationSpecifiers();
        if (delegSpecs != null)
            for (AnnotatedDelegationSpecifierContext aDSpec : delegSpecs.annotatedDelegationSpecifier())
                System.out.println("TODO: delegation specifier " + aDSpec.getText());

        JType jt = new JType(sourceFile, name, superTypes, mp.getAnnotations(),
                pos, getEnclosingElement(), parent, mp.isInner(), mp.isPublic(),
                mp.isPrivate(), mp.isProtected(), mp.isAbstract(), mp.isFinal(),
                isAnonymous);
        jt.typeUsages.addAll(mp.getAnnotationUses());
        sourceFile.jTypes.add(jt);

        typeScope.push(jt);
        ClassBodyContext cBody = cdc.classBody();
        if (cBody != null) {
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
                        processFunctionDeclaration(jt, funMemDecl);
                        continue;
                    }
                    ObjectDeclarationContext objMemDecl = decl.objectDeclaration();
                    if (objMemDecl != null) {
                        System.out.println("TODO: handle object members.");
                        continue;
                    }
                    PropertyDeclarationContext propMemDecl = decl.propertyDeclaration();
                    if (propMemDecl != null) {
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
        }
        typeScope.pop();
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

    @Override
    public Object visitChildren(RuleNode ruleNode) {
        visitTree(ruleNode);
        return null;
    }

    private void processFunctionDeclaration(JType jt, FunctionDeclarationContext funMemDecl) {
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
        jt.methods.add(new JMethod(jt.srcFile, fName, retType, parameters,
                mp.getAnnotations(), outerPos, jt, KotlinUtils.createPositionFromToken(fNameCtx.start)));
    }

    private String getType(TypeContext typeCtx) {
        return typeCtx == null ? null : Utils.simplifyType(typeCtx.getText());
    }

    private void visitTree(ParseTree tree) {
        if (tree instanceof KotlinFileContext) {
            KotlinFileContext kfc = (KotlinFileContext) tree;
            kfc.importList().importHeader().forEach(ihc -> ihc.accept(this));
            PackageHeaderContext phc = kfc.packageHeader();
            if (phc != null)
                sourceFile.packageName = getQualifiedName(phc.identifier());
            kfc.topLevelObject().forEach(this::visitChildren);
        } else if (tree instanceof TopLevelObjectContext) {
            TopLevelObjectContext tloc = (TopLevelObjectContext)tree;
            DeclarationContext dc = tloc.declaration();
            ClassDeclarationContext cdc = dc.classDeclaration();
            if (cdc != null)
                processClassDeclaration(cdc);
            else {
                if (dc.functionDeclaration() != null) {
                    System.out.println("WARNING: top-level functions are not yet supported.");
                }
                else if (dc.objectDeclaration() != null) {
                    System.out.println("WARNING: top-level objects are not yet supported.");
                } else
                    System.out.println("WARNING: unknown top-level declaration.");
            }
        } else if (tree instanceof ImportHeaderContext) {
            ImportHeaderContext ihc = (ImportHeaderContext) tree;
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
        } else
            System.out.println("WARNING: unsupported context " + tree.getClass().getName());
    }

    @Override
    public Object visitTerminal(TerminalNode terminalNode) {
        return null;
    }

    @Override
    public Object visitErrorNode(ErrorNode errorNode) {
        return null;
    }

    // Copy of similar method in Groovy parser.
    private static String getQualifiedName(IdentifierContext ctx) {
        StringJoiner sj = new StringJoiner(".");
        ctx.simpleIdentifier().forEach(e -> sj.add(e.getText()));
        return sj.toString();
    }
}
