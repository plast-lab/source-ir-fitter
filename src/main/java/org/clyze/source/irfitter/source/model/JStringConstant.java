package org.clyze.source.irfitter.source.model;

import org.clyze.persistent.model.Position;
import org.clyze.persistent.model.jvm.JvmStringConstant;

/**
 * A string constant that can be inlined (and thus missing from the IR), so
 * it should be preserved.
 *
 * @param <T> the type of the element (field, variable) initialized by the constant
 */
public class JStringConstant<T> {
    private final SourceFile sourceFile;
    /** The position of the string constant in the source code. */
    public final Position pos;
    private final T srcElement;
    /** The string value (without quotes). */
    public final String value;

    /**
     * Create a string constant element.
     * @param sourceFile   the source file where the constant appears
     * @param pos          the position of the string constant
     * @param srcElement   the element initialized by the constant
     * @param value        the string value
     */
    public JStringConstant(SourceFile sourceFile, Position pos, T srcElement, String value) {
        this.sourceFile = sourceFile;
        this.pos = pos;
        this.srcElement = srcElement;
        this.value = value;
    }

    /**
     * Generates the string constant metadata object, to be serialized to JSON.
     * @return  the string constant object
     */
    public JvmStringConstant getStringConstant() {
        if (srcElement instanceof JField) {
            JField srcField = (JField) srcElement;
            String fieldId = srcField.matchId;
            if (fieldId == null)
                System.out.println("WARNING: string constant references unresolved field: " + srcField);
            return new JvmStringConstant(pos, sourceFile.getRelativePath(), fieldId, value);
        } else {
            System.out.println("WARNING: string constant is ignored for non-field element: " + srcElement);
            return null;
        }
    }

    @Override
    public String toString() {
        return "StringConstant[\"" + value + "\"]@" + pos;
    }
}
