package org.clyze.source.irfitter.ir.model;

public abstract class IRElement {
    final String id;
    /**
     * If true, this IR element has already matched a source element (and may
     * thus be ignored on subsequent checks).
     */
    public boolean matched = false;

    public IRElement(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }
}
