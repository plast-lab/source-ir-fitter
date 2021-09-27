package org.clyze.source.irfitter.source.model;

import java.util.List;

/** Utilities. */
public class Utils {
    public static String dotsToDollars(String n) {
        int dotIdx = n.indexOf(".");
        if (dotIdx == -1)
            return n;

        char[] chars = n.toCharArray();
        boolean capital = Character.isUpperCase(chars[0]);
        int i = 1;
        int len = n.length();
        while (i < len - 1) {
            char c = chars[i];
            if (c == '.') {
                if (capital) {
                    chars[i] = '$';
                    i++;
                }
                if (Character.isUpperCase(chars[i+1])) {
                    capital = true;
                    i++;
                }
            }
            i++;
        }
        return new String(chars);
    }

    /**
     * Transform away parts of a type that are not necessary for matching types
     * (generics, Kotlin "?" nullable types).
     * @param s    the original type
     * @return     the simplified type
     */
    public static String simplifyType(String s) {
        int generics = s.indexOf('<');
        String noGenerics = generics == -1 ? s : s.substring(0, generics);
        return noGenerics.endsWith("?") ? noGenerics.substring(0, noGenerics.length() - 1) : noGenerics;
    }

    /**
     * Synthesize full types from partial source types and erased IR types.
     * @param srcTypes   the source types (may contain generics or lack package prefix)
     * @param irTypes    the IR types (generics have been erased)
     * @param debug      if true, print debug information
     * @return           the list of synthesized types (generics are left unchanged)
     */
    public static String[] getSynthesizedTypes(List<String> srcTypes, List<String> irTypes, boolean debug) {
        int srcTypesCount = srcTypes.size();
        if (srcTypesCount != irTypes.size()) {
            System.out.println("ERROR: source and IR types differ in length: " + srcTypes + " vs. " + irTypes);
            return irTypes.toArray(new String[0]);
        }
        String[] ret = new String[srcTypesCount];
        for (int i = 0; i < srcTypesCount; i++)
            ret[i] = getSynthesizedType(srcTypes.get(i), irTypes.get(i), debug);
        return ret;
    }

    /**
     * Synthesize a full type from a partial source type and an erased IR type.
     * @param srcType    the source types (may contain generics or lack package prefix)
     * @param irType     the IR types (generics have been erased)
     * @param debug      if true, print debug information
     * @return           the synthesized type (generics are left unchanged)
     */
    public static String getSynthesizedType(String srcType, String irType, boolean debug) {
        String irSimpleType = getSimpleType(irType);
        String srcSimpleType = getSimpleSourceType(srcType);
        if (irType.endsWith(srcSimpleType)) {
            String prefix = irType.substring(0, irType.length() - srcSimpleType.length());
            if (debug)
                System.out.println("Synthesized type: irType = " + irType +
                        " (" + irSimpleType + "), srcType = " + srcType +
                        " (" + srcSimpleType + "), prefix = " + prefix);
            return prefix + srcType;
        }
        return irType;
    }

    /**
     * Simplify a source type.
     * @param srcType    a source type (e.g. {@code a.b.C<D>})
     * @return           a simplified type ({@code C})
     */
    public static String getSimpleSourceType(String srcType) {
        return getSimpleType(simplifyType(srcType));
    }

    /**
     * Simplify an IR type.
     * @param irType    an IR type (e.g. {@code a.B$C<D>})
     * @return          a simplified IR type ({@code C})
     */
    public static String getSimpleIrType(String irType) {
        int irDollarIdx = irType.indexOf("$");
        return getSimpleType(irDollarIdx == -1 ? irType : irType.substring(irDollarIdx + 1));
    }

    /**
     * Compare a source type against an IR type.
     * @param srcType    source type
     * @param irType     IR type
     * @return           true if the simple names of the two types are equal
     */
    public static boolean simpleTypesAreEqual(String srcType, String irType) {
        return getSimpleSourceType(srcType).equals(getSimpleIrType(irType));
    }

    /**
     * Return the simple type of a fully-qualified type.
     * @param type  a JVM type
     * @return      the part of the type without the package prefix
     */
    public static String getSimpleType(String type) {
        int dotIdx = type.lastIndexOf('.');
        return dotIdx >= 0 ? type.substring(dotIdx + 1) : type;
    }

    /**
     * Strips the (matching) single/double quotes around a string. If no such
     * quotes exist, the input string is returned.
     * @param s   the original string
     * @return    the string without quotes
     */
    public static String stripQuotes(String s) {
        if ((s.startsWith("\"") && s.endsWith("\"")) ||
                (s.startsWith("'") && s.endsWith("'")))
            return s.substring(1, s.length() - 1);
        else
            return s;
    }
}
