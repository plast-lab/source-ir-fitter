package org.clyze.source.irfitter.ir.model;

import java.util.*;

import org.clyze.source.irfitter.base.ModifierPack;

/**
 * A low-level representation of a type.
 */
public class IRType extends IRElement {
    public final List<String> superTypes;
    public final List<IRField> fields = new ArrayList<>();
    public final List<IRMethod> methods = new ArrayList<>();
    public final ModifierPack mp;
    /** For true inner classes, this contains their outer classes. */
    public List<String> outerTypes = null;

    public IRType(String id, List<String> superTypes, ModifierPack mp) {
        super(id);
        this.superTypes = superTypes;
        this.mp = mp;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public void addReferencedTypesTo(Collection<String> target) {
        addTypeRefs(target, fields);
        addTypeRefs(target, methods);
        if (superTypes != null)
            target.addAll(superTypes);
    }

    /**
     * Add an outer type of this inner class.
     * @param typeId  the type id
     */
    private void addOuterType(String typeId) {
        if (outerTypes == null)
            outerTypes = new ArrayList<>();
        outerTypes.add(typeId);
    }

    public void addField(IRField field) {
        fields.add(field);
        if (field.name.equals("this$0"))
            addOuterType(field.type);
    }

    public boolean declaresMethod(String retType, String name, String paramTypes) {
        for (IRMethod method : methods) {
            if (method.name.equals(name) && method.returnType.equals(retType) && method.getParamTypesAsString().equals(paramTypes))
                return true;
        }
        return false;
    }
}
