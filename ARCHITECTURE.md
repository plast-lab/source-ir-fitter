# Architecture #

This file documents the architecture of the source-ir-fitter tool. For
the javadoc documentation, run `./gradlew javadoc` in the top-level
directory of this repository.

This tool takes as input a Java/Kotlin/Groovy program, in both source
and IR (bytecode/dex) format.

## Pipeline #

The following is a high-level view of the processing pipeline:

1. The IR is parsed and an appropriate IR model is constructed
   (classes in package `org.clyze.source.irfitter.ir.model`). This
   model represents as much of the IR as needed to aid source-element
   resolution. In particular, types are fully resolved
   (fully-qualified). Some element nesting is preserved: e.g. methods
   are associated with their containing classes while method
   invocation instructions are associated with their containing methods.

2. The source is parsed and an appropriate source model is constructed
   (classes in package `org.clyze.source.irfitter.source.model`).
   * As in the previous step, the tool records that some elements may be
   nested under other elements.
   * On the other hand, elements such as types may not be fully-qualified
     (i.e. `String` instead of `java.lang.String`).
   * Element usages (such as annotation usages or class constants) are
     also recorded.

3. Type matching is performed, per source file (class `SourceFile`):
   each source type is compared with the IR types, until a match
   happens. Unqualified source type resolution uses source code
   imports to match against the IR types.

4. For every source-IR type match, their nested source-IR members
   (fields/methods) are also matched. Fields match by name while
   methods may match by name, number of arguments, or full signature.

5. For every source-IR method match, method invocation instructions
   are also matched between the source and the IR.

6. For every source-IR method match, `new T()` instructions ("heap
   allocations") are also matched between the source and the IR.

7. When all elements have been resolved, element usages are resolved.

## JSON mode ##

In JSON output mode, every source code element generates its
[metadata-model](https://github.com/clyze/metadata-model)-equivalent
object.

## Doop mode ##

In Doop analysis mapping mode, the Doop results are read back via
Doop's "code-processor" library. These results are then rewritten to
reference source elements instead of IR elements. The result is
written out in SARIF format.

## Parsers

* Java: [JavaParser](https://javaparser.org/)
* Groovy: [Parrot parser](https://github.com/danielsun1106/groovy-parser)
* Kotlin: [kotlin-formal](https://github.com/antlr/grammars-v4/tree/master/kotlin/kotlin-formal)
