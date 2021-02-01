package org.clyze.source.irfitter.test;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.clyze.source.irfitter.Main;
import org.junit.jupiter.api.Test;

//import static org.junit.jupiter.api.Assertions.*;

public class TestMapper {

    /**
     * Test that a command-line invocation will not crash on
     * Java/Groovy sources and the output directory is not empty.
     */
    @Test
    void testJavaAndGroovy() throws IOException {
        String outDir = "build/test-out-java-groovy";
        generateJson("clue-common-3.24.1.jar", "clue-common-3.24.1-sources.jar", outDir);
        assert (new File(outDir).listFiles() != null);
    }

    /**
     * Test that a command-line invocation will not crash on
     * Kotlin sources and the output directory is not empty.
     */
    @Test
    void testKotlin() throws IOException {
        String outDir = "build/test-out-kotlin";
        generateJson("noarg-compiler-plugin.jar", "noarg-compiler-plugin-sources.zip", outDir);
        assert (new File(outDir).listFiles() != null);
    }

    void generateJson(String jarRes, String sourcesJarRes, String outDir) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String jar = classLoader.getResource(jarRes).getFile();
        System.out.println("jar: " + jar);
        String sourcesJar = classLoader.getResource(sourcesJarRes).getFile();
        System.out.println("sourcesJar: " + sourcesJar);
        FileUtils.deleteDirectory(new File(outDir));
        String[] args = new String[]{"--ir", jar, "--source", sourcesJar, "--out", outDir, "--json"};
        Main.main(args);
    }
}