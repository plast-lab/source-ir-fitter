package org.clyze.source.irfitter.test;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.clyze.source.irfitter.Main;
import org.clyze.source.irfitter.RunResult;
import org.junit.jupiter.api.Test;

//import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("MagicNumber")
public class TestMapper {

    /**
     * Test that a command-line invocation will not crash on
     * Java/Groovy sources and the output directory is not empty.
     */
    @Test
    void testJavaAndGroovy() throws IOException {
        String outDir = "build/test-out-java-groovy";
        RunResult rr = generateJson("clue-common-3.24.1.jar", "clue-common-3.24.1-sources.jar", outDir);
        assert (new File(outDir).listFiles() != null);
        assert (rr.unmatched == 32);
    }

    /**
     * Test that a command-line invocation will not crash on
     * Kotlin sources and the output directory is not empty.
     */
    @Test
    void testKotlin() throws IOException {
        String outDir = "build/test-out-kotlin";
        RunResult rr = generateJson("noarg-compiler-plugin.jar", "noarg-compiler-plugin-sources.zip", outDir);
        assert (new File(outDir).listFiles() != null);
        assert (rr.unmatched == 27);
    }

    RunResult generateJson(String jarRes, String sourcesJarRes, String outDir) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String jar = Objects.requireNonNull(classLoader.getResource(jarRes)).getFile();
        System.out.println("jar: " + jar);
        String sourcesJar = Objects.requireNonNull(classLoader.getResource(sourcesJarRes)).getFile();
        System.out.println("sourcesJar: " + sourcesJar);
        FileUtils.deleteDirectory(new File(outDir));
        String[] args = new String[]{"--ir", jar, "--source", sourcesJar, "--out", outDir, "--json"};
        return Main.run(args);
    }
}
