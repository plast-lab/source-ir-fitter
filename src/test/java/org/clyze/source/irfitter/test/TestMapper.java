package org.clyze.source.irfitter.test;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.clyze.source.irfitter.Main;
import org.junit.jupiter.api.Test;

//import static org.junit.jupiter.api.Assertions.*;

public class TestMapper {
    /**
     * Test that a command-line invocation will not crash and the output
     * directory is not empty.
     */
    @Test
    void test() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String clueCommonJar = classLoader.getResource("clue-common-3.24.1.jar").getFile();
        System.out.println("clueCommonJar: " + clueCommonJar);
        String clueCommonSourcesJar = classLoader.getResource("clue-common-3.24.1-sources.jar").getFile();
        System.out.println("clueCommonSourcesJar: " + clueCommonSourcesJar);
        String outDir = "build/test-out";
        FileUtils.deleteDirectory(new File(outDir));
        String[] args = new String[]{"--ir", clueCommonJar, "--source", clueCommonSourcesJar, "--out", outDir, "--json"};
        Main.main(args);

        assert (new File(outDir).listFiles() != null);
    }
}
