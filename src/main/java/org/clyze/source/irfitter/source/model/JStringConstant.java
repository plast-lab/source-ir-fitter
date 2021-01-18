package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.StringConstant;

/**
 * A string constant that can be inlined (and thus missing from the IR), sot
 * it should be preserved.
 */
public class JStringConstant {
    private final SourceFile sourceFile;
    private final Position pos;
    private final JField srcField;
    private final String value;

    public JStringConstant(SourceFile sourceFile, Position pos, JField srcField, String value) {
        this.sourceFile = sourceFile;
        this.pos = pos;
        this.srcField = srcField;
        this.value = value;
    }

    public StringConstant getStringConstant() {
        String fieldId = srcField.matchId;
        if (fieldId == null)
            System.out.println("WARNING: string constant references unresolved field: " + srcField);
        return new StringConstant(pos, sourceFile.getRelativePath(), fieldId, value);
    }
}
