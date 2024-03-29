package org.clyze.source.irfitter.source.model;

import org.clyze.utils.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This interface describes elements that contain incomplete type information
 * and may thus match more than one IR elements.
 */
public interface FuzzyTypes {
    SourceFile getSourceFile();

    /**
     * Given a simple type, compute possible fully-qualified names.
     * @param type  the source-level type
     * @return      a collection of possible fully-qualified type names
     */
    default Collection<String> resolveType(String type) {
        SourceFile sourceFile = getSourceFile();
        if (type == null)
            return Collections.singletonList("void");
        type = Utils.simplifyType(type);
        if (TypeUtils.isPrimitiveType(type))
            return Collections.singletonList(Utils.dotsToDollars(type));

        // Search for local/nested class declarations in the whole source file, pick first.
        String packageName = sourceFile.packageName;
        for (JType jt : sourceFile.jTypes)
            if (!jt.isAnonymous && type.equals(jt.getUnqualifiedName()))
                return Collections.singletonList(Utils.dotsToDollars(jt.getFullyQualifiedName(packageName)));

        // Search for exact import matches, pick first.
        for (Import id : sourceFile.imports) {
            if (!id.isAsterisk && !id.isStatic) {
                String fqn = id.name;
                if (fqn.substring(fqn.lastIndexOf(".") + 1).equals(type))
                    return Collections.singletonList(Utils.dotsToDollars(fqn));
                // Handle import of type "C" and use of nested type "C.D".
                String st = id.simpleType;
                if (st != null && type.startsWith(st)) {
                    int stLength = st.length();
                    if (type.length() > stLength) {
                        char delim = type.charAt(stLength);
                        // Handle both nested type syntax variants: C.D, C$D.
                        if (delim == '.' || delim == '$') {
                            String fqn0 = fqn + "." + type.substring(stLength + 1);
                            return Collections.singletonList(Utils.dotsToDollars(fqn0));
                        }
                    }
                }
            }
        }

        // Fuzzy, with wildcards.
        Collection<String> results = new ArrayList<>();
        results.add(type);
        if (packageName != null && !packageName.equals(""))
            results.add(packageName + "." + type);
        for (Import id : sourceFile.imports)
            if (!id.isStatic && id.isAsterisk)
                results.add(id.name + "." + type);

        return results.stream().map(Utils::dotsToDollars).collect(Collectors.toList());
    }
}