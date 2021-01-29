package org.clyze.source.irfitter.source.kotlin;

import org.antlr.v4.runtime.Token;
import org.clyze.persistent.model.Position;

/** A collection of utilities used during parsing of Kotlin sources. */
public class KotlinUtils {
    // Duplicate, similar method exists in Groovy parser.
    public static Position createPositionFromToken(Token token) {
        int startLine = token.getLine();
        int startColumn = token.getCharPositionInLine() + 1;
        int endColumn = startColumn + token.getText().length();
        return new Position(startLine, startLine, startColumn, endColumn);
    }

    // Duplicate, similar method exists in Groovy parser.
    public static Position createPositionFromTokens(Token start, Token end) {
        Position startPos = createPositionFromToken(start);
        Position endPos = createPositionFromToken(end);
        return new Position(startPos.getStartLine(), endPos.getEndLine(),
                startPos.getStartColumn(), endPos.getEndColumn());
    }
}
