package org.clyze.source.irfitter.source.java;

import com.github.javaparser.Position;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import java.util.*;

/** A collection of utilities used during parsing of Java sources. */
public class JavaUtils {
    public static org.clyze.persistent.model.Position createPositionFromNode(NodeWithRange<?> sn) {
        int startLine, startColumn, endLine, endColumn;
        Optional<Position> begin = sn.getBegin();
        if (begin.isPresent()) {
            Position pos = begin.get();
            startLine = pos.line;
            startColumn = pos.column;
        } else {
            startLine = -1;
            startColumn = -1;
        }
        Optional<Position> end = sn.getEnd();
        if (end.isPresent()) {
            Position pos = end.get();
            endLine = pos.line;
            endColumn = pos.column + 1;
        } else {
            endLine = -1;
            endColumn = -1;
        }
        return new org.clyze.persistent.model.Position(startLine, endLine, startColumn, endColumn);
    }
}
