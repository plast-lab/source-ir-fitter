package org.clyze.source.irfitter.source.groovy;

import groovyjarjarantlr4.v4.runtime.CommonTokenStream;
import groovyjarjarantlr4.v4.runtime.CharStreams;
import java.io.*;
import org.apache.groovy.parser.antlr4.GroovyLangLexer;
import org.apache.groovy.parser.antlr4.GroovyLangParser;
import org.clyze.source.irfitter.SourceProcessor;
import org.clyze.source.irfitter.source.model.SourceFile;

/** This class handles Groovy source processing. */
public class GroovyProcessor implements SourceProcessor {
    @Override
    public SourceFile process(File topDir, File srcFile, boolean debug, boolean synthesizeTypes) {
        SourceFile sf = new SourceFile(topDir, srcFile, debug, synthesizeTypes);
        try {
            GroovyLangLexer gll = new GroovyLangLexer(CharStreams.fromFile(srcFile));
            CommonTokenStream tokens = new CommonTokenStream(gll);
            tokens.fill();
            GroovyLangParser glp = new GroovyLangParser(tokens);
            glp.compilationUnit().accept(new GroovyTreeVisitor(sf));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return sf;
    }
}
