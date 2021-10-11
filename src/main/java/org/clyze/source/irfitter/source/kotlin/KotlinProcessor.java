package org.clyze.source.irfitter.source.kotlin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.antlr.v4.runtime.*;
import org.clyze.source.irfitter.SourceProcessor;
import org.clyze.source.irfitter.matcher.Aliaser;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.antlr.grammars.KotlinLexer;
import org.antlr.grammars.KotlinParser;

/** This class handles Kotlin source processing. */
public class KotlinProcessor implements SourceProcessor {
    @Override
    public SourceFile process(File topDir, File srcFile, boolean debug,
                              boolean synthesizeTypes,Set<String> vaIrMethods) {
        SourceFile sf = new SourceFile(topDir, srcFile, debug, synthesizeTypes);
        try (InputStream inputStream = new FileInputStream(srcFile)) {
            Lexer lexer = new KotlinLexer(CharStreams.fromStream(inputStream));
            TokenStream tokenStream = new CommonTokenStream(lexer);
            KotlinParser parser = new KotlinParser(tokenStream);
            parser.kotlinFile().accept(new KotlinVisitor(sf, debug));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return sf;
    }
}
