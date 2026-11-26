package org.example.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.instruction.LineNumber;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CoverageAgentTest {

    @Test
    void reportsCoveredAndMissedLines(@TempDir Path tmp) throws Exception {
        Path report = tmp.resolve("cov.json");
        Path out = tmp.resolve("stdout.txt");
        String agent = System.getProperty("agent.jar");
        assertNotNull(agent, "agent.jar system property is set by Gradle");
        Process p = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-javaagent:" + agent + "=include=org.example.agent.demo,output=" + report,
                "-cp", System.getProperty("java.class.path"),
                "org.example.agent.demo.CoverageDemo")
                .redirectErrorStream(true).redirectOutput(out.toFile()).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String output = Files.readString(out);
        assertEquals(0, p.exitValue(), output);
        assertTrue(output.contains("odd"), "instrumented program still behaves correctly: " + output);
        assertFalse(output.contains("cannot instrument"), output);

        List<Integer> hit = new ArrayList<>(), miss = new ArrayList<>();
        List<String> src = Files.readAllLines(Path.of("src/test/java/org/example/agent/demo/CoverageDemo.java"));
        for (int i = 0; i < src.size(); i++) {
            if (src.get(i).contains("// hit")) hit.add(i + 1);
            if (src.get(i).contains("// miss")) miss.add(i + 1);
        }
        String json = Files.readString(report);
        assertTrue(json.contains("\"coveredLines\": " + hit), json);
        assertTrue(json.contains("\"missedLines\": " + miss), json);
    }

    @Test
    void unknownOptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CoverageAgent.premain("colour=red", null));
    }

    @Test
    void classWithoutLineNumbersIsLeftAlone() throws IOException {
        // compiled without -g:lines -> nothing to probe -> transformer returns null (class unchanged)
        byte[] bytes;
        try (var in = CoverageAgentTest.class.getResourceAsStream("/org/example/agent/demo/CoverageDemo.class")) {
            bytes = in.readAllBytes();
        }
        ClassFile cf = ClassFile.of();
        byte[] noLines = cf.transformClass(cf.parse(bytes),
                ClassTransform.transformingMethodBodies((CodeTransform) (cb, e) -> {
                    if (!(e instanceof LineNumber)) {
                        cb.with(e);
                    }
                }));
        assertNull(CoverageClassTransformer.instrument("org/example/agent/demo/CoverageDemo", getClass().getClassLoader(), noLines));
    }
}
