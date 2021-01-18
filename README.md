# Source-code-to-IR mapper

This program maps bytecode/dex elements (such as types, methods,
fields, and invocations) to source elements. The input is the
executable code (JAR/APK format) plus the original sources. The output
is a [JSON mapping](https://github.com/clyze/metadata-model) of source
code elements that correspond to low-level entities. The low-level
entity ids follow the format of the [Doop static analysis
framework](https://bitbucket.org/yanniss/doop/).

This is work in progress, currently the following source languages are
(partially) supported: Java, Groovy, Kotlin.

## Build requirements

Install the fork of the ANTLRv4 grammars to the local Maven repository:

```
git clone https://github.com/gfour/grammars-v4.git
cd grammars-v4
mvn install
```

Then, install this program:

```
./gradlew installDist
```

## Basic use

To generate the JSON mappings for application with code in `app.jar`
and sources in `app-sources.jar`, run the following command:

```
build/install/source-ir-fitter/bin/source-ir-fitter --ir path/to/app.jar --source path/to/app-sources.jar --out app-out --json
```

The output .json files can be found in directory `app-out`.

## Use with Doop

This program can be used to map results of Doop static analyses
(originally on bytecode) to sources. The results can be viewed in a
tool with SARIF support, such as Visual Studio Code with the SARIF
viewer extension.

Assume a Doop analysis with SARIF output enabled:

```
cd $DOOP_HOME
./doop -i path/to/app.jar -a context-insensitive --sarif --id app --stats none --gen-opt-directives --no-standard-exports
```

Then, run the code processor:

```
build/install/source-ir-fitter/bin/source-ir-fitter --ir path/to/app.jar --source path/to/app/src --out app-out --database ${DOOP_HOME}/out/context-insensitive/app/database
```

Finally, run Visual Studio Code on the results:

```
code path/to/app/src app-out/doop.sarif
```

## Troubleshooting

*Problem:* Some elements in Groovy sources (such as method calls)
cannot be mapped to the generated bytecode.

Solution: This is an inherent limitation of Groovy's dynamic
features. Using `@CompileStatic` in the Groovy sources helps.

*Problem:* In the generated metadata, there is a call to
StringBuilder.append() that does not appear in the sources, yet it is
mapped to a source line.

Solution: Bytecode elements that have source line information are
still mapped to the sources. For example, the compiler may have
generated StringBuilder.append() calls to implement string
concatenation and the metadata may map these calls to the
corresponding source lines.
