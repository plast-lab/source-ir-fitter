package org.clyze.source.irfitter.source.kotlin;

import java.util.ArrayList;
import java.util.Collection;
import org.antlr.grammars.KotlinParser;
import org.antlr.grammars.KotlinParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.clyze.persistent.model.Position;
import org.clyze.source.irfitter.source.model.JStringConstant;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.clyze.source.irfitter.source.model.Utils;

/**
 * This visitor returns the string literals found in a node of the AST. These
 * string literals are then related to an existing element (such as the root
 * of the visited tree).
 * @param <T>   the type of the element to connect with the string literals
 */
public class StringScanner<T> extends KotlinParserBaseVisitor<Void> {
    private final SourceFile sourceFile;
    private final T srcElement;
    public Collection<JStringConstant<T>> strs = null;

    public StringScanner(SourceFile sourceFile, T srcElement) {
        this.sourceFile = sourceFile;
        this.srcElement = srcElement;
    }

    @Override
    public Void visitStringLiteral(KotlinParser.StringLiteralContext ctx) {
        return saveText(ctx);
    }

    @Override
    public Void visitLineStringLiteral(KotlinParser.LineStringLiteralContext ctx) {
        return saveText(ctx);
    }

    @Override
    public Void visitMultiLineStringLiteral(KotlinParser.MultiLineStringLiteralContext ctx) {
        return saveText(ctx);
    }

    @SuppressWarnings("SameReturnValue")
    private Void saveText(ParserRuleContext ctx) {
        Position pos = KotlinUtils.createPositionFromTokens(ctx.start, ctx.stop);
        String value = Utils.stripQuotes(ctx.getText());
        if (strs == null)
            strs = new ArrayList<>();
        strs.add(new JStringConstant<>(sourceFile, pos, srcElement, value));
        return null;
    }
}
