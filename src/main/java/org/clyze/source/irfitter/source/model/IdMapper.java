package org.clyze.source.irfitter.source.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This data structure holds all mappings between ids and source code
 * elements of different types.
 */
public class IdMapper {
    public final Map<String, Collection<JType>> typeMap = new HashMap<>();
    public final Map<String, Collection<JField>> fieldMap = new HashMap<>();
    public final Map<String, Collection<JMethod>> methodMap = new HashMap<>();
    public final Map<String, Collection<JMethodInvocation>> invocationMap = new HashMap<>();
    public final Map<String, Collection<JAllocation>> allocationMap = new HashMap<>();
    public final Map<String, Collection<JFieldAccess>> fieldAccessMap = new HashMap<>();
    public final Map<String, Collection<JMethodRef>> methodRefMap = new HashMap<>();
    public final Map<String, Collection<JParameter>> variableMap = new HashMap<>();

    /**
     * Merge all element information into a single mapping.
     * @return   a mapping containing the correspondence for all source elements
     */
    public Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> get() {
        Map<String, Collection<? extends NamedElementWithPosition<?, ?>>> mapping = new HashMap<>();
        mapping.putAll(typeMap);
        mapping.putAll(fieldMap);
        mapping.putAll(methodMap);
        mapping.putAll(invocationMap);
        mapping.putAll(allocationMap);
        mapping.putAll(fieldAccessMap);
        mapping.putAll(methodRefMap);
        mapping.putAll(variableMap);
        return mapping;
    }
}
