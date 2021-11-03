package org.clyze.source.irfitter.source.model;

/**
 * This class expresses a position inside a method body.
 * Since source methods are expressed as sets of collections of
 * disjoint elements (invocations, allocations, etc.), this class defines
 * a position as a frontier (tuple of indexes, one for each element type).
 *
 * For example, position P in the following (simplified) method body:
 *
 *   void m() {
 *     alloc1
 *     alloc2
 *     invo1
 *     field-access-1
 *     <--------- position P
 *     alloc3
 *     invo2
 *     field-access-2
 *   }
 *
 * is represented as the index-tuple (2, 1, 1), assuming:
 *
 *   allocations = { alloc1, alloc2, alloc3 }
 *   invocations = { invo1, invo2 }
 *   field-accesses = { field-access-1, field-access-2 }
 *
 * This class is used to add instance initializer blocks to existing constructors.
 */
class MethodBodyFrontier {
    final int allocIndex, invoIndex, fieldAccIndex, methodRefsIndex, castsIndex, lambdasIndex, elementUsesIndex;

    MethodBodyFrontier(int allocIndex, int invoIndex, int fieldAccIndex, int methodRefsIndex, int castsIndex, int lambdasIndex, int elementUsesIndex) {
        this.allocIndex = allocIndex;
        this.invoIndex = invoIndex;
        this.fieldAccIndex = fieldAccIndex;
        this.methodRefsIndex = methodRefsIndex;
        this.castsIndex = castsIndex;
        this.lambdasIndex = lambdasIndex;
        this.elementUsesIndex = elementUsesIndex;
    }

    @Override
    public String toString() {
        return "method-body-pos:: alloc=" + allocIndex +
                ", invo=" + invoIndex + ", fieldAcc=" + fieldAccIndex +
                ", methodRefs=" + methodRefsIndex + ", casts=" + castsIndex +
                ", lambdas=" + lambdasIndex + ", elementUses=" + elementUsesIndex;
    }
}
