package org.clyze.source.irfitter.source.model;

/**
 * The interface of code elements that go though matching.
 */
public interface Matchable {
    /**
     * Check if the code element has been successfully matched against the IR.
     *
     * @return  true if the element has been matched by a corresponding IR element
     */
    boolean hasBeenMatched();
}
