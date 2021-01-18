package org.clyze.source.irfitter.source.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.clyze.source.irfitter.SourceProcessor;
import org.clyze.source.irfitter.source.model.SourceFile;

/** This class handles Java source processing. */
public class JavaProcessor implements SourceProcessor {
    @Override
    public SourceFile process(File topDir, File srcFile, boolean debug, boolean synthesizeTypes) {
        JavaParser jp = new JavaParser();
        try {
            Optional<CompilationUnit> optCu = jp.parse(srcFile).getResult();
            if (optCu.isPresent()) {
                SourceFile sf = new SourceFile(topDir, srcFile, debug, synthesizeTypes);
                optCu.ifPresent((CompilationUnit cu) -> cu.accept(new JavaVisitor(), sf));
                return sf;
            } else
                System.out.println("No parsing result for " + srcFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
