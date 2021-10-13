package org.clyze.source.irfitter.test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.clyze.source.irfitter.Main;
import org.clyze.source.irfitter.RunResult;
import org.clyze.source.irfitter.source.model.IdMapper;
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
        assert (rr.idMapper.allTypes >= 38);
        assert (rr.idMapper.matchedTypes >= 38);
        assert (rr.idMapper.allMethods >= 267);
        assert (rr.idMapper.matchedMethods >= 205);
        assert (rr.idMapper.allFields >= 92);
        assert (rr.idMapper.matchedFields >= 92);
        assert (rr.idMapper.allInvos >= 1700);
        assert (rr.idMapper.matchedInvos >= 1631);
        assert (rr.idMapper.allAllocs >= 638);
        assert (rr.idMapper.matchedAllocs >= 637);
        assert (rr.idMapper.allMethodRefs >= 1);
        assert (rr.idMapper.matchedMethodRefs >= 1);
        assert (rr.idMapper.allFieldAccesses >= 50);
        assert (rr.idMapper.matchedFieldAccesses >= 18);
        assert (rr.idMapper.allUses >= 323);
        assert (rr.idMapper.matchedUses >= 323);
        assert (rr.idMapper.allVariables >= 372);
        assert (rr.idMapper.matchedVariables >= 370);
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
        assert (rr.idMapper.allTypes >= 15);
        assert (rr.idMapper.matchedTypes >= 15);
        assert (rr.idMapper.allMethods >= 53);
        assert (rr.idMapper.matchedMethods >= 26);
        assert (rr.idMapper.allFields >= 46);
        assert (rr.idMapper.matchedFields >= 22);
        assert (rr.idMapper.allInvos >= 275);
        assert (rr.idMapper.matchedInvos >= 268);
        assert (rr.idMapper.allAllocs >= 29);
        assert (rr.idMapper.matchedAllocs >= 28);
        assert (rr.idMapper.allMethodRefs >= 0);
        assert (rr.idMapper.matchedMethodRefs >= 0);
        assert (rr.idMapper.allFieldAccesses >= 2);
        assert (rr.idMapper.matchedFieldAccesses >= 0);
        assert (rr.idMapper.allUses >= 1);
        assert (rr.idMapper.matchedUses >= 1);
        assert (rr.idMapper.allVariables >= 31);
        assert (rr.idMapper.matchedVariables >= 29);
    }

    // Helper method to use when updating test statistics.
    void inspect(IdMapper idMapper) {
        try {
            for (Field field : idMapper.getClass().getFields()) {
                System.out.println(field.getName() + " -> " + field.get(idMapper));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    RunResult generateJson(String jarRes, String sourcesJarRes, String outDir) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String jar = Objects.requireNonNull(classLoader.getResource(jarRes)).getFile();
        System.out.println("jar: " + jar);
        String sourcesJar = Objects.requireNonNull(classLoader.getResource(sourcesJarRes)).getFile();
        System.out.println("sourcesJar: " + sourcesJar);
        FileUtils.deleteDirectory(new File(outDir));
        String[] args = new String[]{"--ir", jar, "--source", sourcesJar, "--out", outDir, "--json", "--stats"};
        return Main.run(args);
    }
}
