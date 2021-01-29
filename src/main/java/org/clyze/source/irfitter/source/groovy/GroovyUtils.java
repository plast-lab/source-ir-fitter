package org.clyze.source.irfitter.source.groovy;

import groovyjarjarantlr4.v4.runtime.Token;
import org.clyze.persistent.model.Position;

/** A collection of utilities used during parsing of Groovy sources. */
public class GroovyUtils {
    // Duplicate, similar method exists in Kotlin parser.
    public static Position createPositionFromToken(Token token) {
        int startLine = token.getLine();
        int startColumn = token.getCharPositionInLine() + 1;
        int endColumn = startColumn + token.getText().length();
        return new Position(startLine, startLine, startColumn, endColumn);
    }

    // Duplicate, similar method exists in Kotlin parser.
    public static Position createPositionFromTokens(Token start, Token end) {
        Position startPos = createPositionFromToken(start);
        Position endPos = createPositionFromToken(end);
        return new Position(startPos.getStartLine(), endPos.getEndLine(),
                startPos.getStartColumn(), endPos.getEndColumn());
    }
}
