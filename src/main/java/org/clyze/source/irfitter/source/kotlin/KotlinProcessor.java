package org.clyze.source.irfitter.source.kotlin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.*;
import org.clyze.source.irfitter.SourceProcessor;
import org.clyze.source.irfitter.source.model.SourceFile;
import org.antlr.grammars.KotlinLexer;
import org.antlr.grammars.KotlinParser;

/** This class handles Kotlin source processing. */
public class KotlinProcessor implements SourceProcessor {
    @Override
    public SourceFile process(File topDir, File srcFile, boolean debug, boolean synthesizeTypes, boolean lossy) {
        SourceFile sf = new SourceFile(topDir, srcFile, debug, synthesizeTypes, lossy);
        try (InputStream inputStream = new FileInputStream(srcFile)) {
            Lexer lexer = new KotlinLexer(CharStreams.fromStream(inputStream));
            TokenStream tokenStream = new CommonTokenStream(lexer);
            KotlinParser parser = new KotlinParser(tokenStream);
            parser.kotlinFile().accept(new KotlinVisitor(sf));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return sf;
    }
}
