package org.example.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarAnalyzerTest {
    private static final String P = "org/example/metrics/fixtures/Fixtures";
    private static final String[] NAMES = {"", "$Shape", "$Base", "$Mid", "$Leaf", "$Abc"};

    private static byte[] bytes(String internal) throws IOException {
        try (InputStream in = JarAnalyzerTest.class.getResourceAsStream("/" + internal + ".class")) {
            return in.readAllBytes();
        }
    }

    private static ProjectMetrics analyze() throws IOException {
        List<byte[]> list = new ArrayList<>();
        for (String n : NAMES) list.add(bytes(P + n));
        return JarAnalyzerHelper.analyze("test", list);
    }

    private static ClassMetrics get(ProjectMetrics p, String suffix) {
        String name = (P + suffix).replace('/', '.');
        return p.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void inheritanceDepth() throws IOException {
        ProjectMetrics p = analyze();
        assertEquals(0, get(p, "$Base").inheritanceDepth());
        assertEquals(1, get(p, "$Mid").inheritanceDepth());
        assertEquals(2, get(p, "$Leaf").inheritanceDepth());
        assertEquals(2, p.maxInheritanceDepth());
        // classes: Fixtures(0), Base(0), Mid(1), Leaf(2), Abc(0); interface excluded
        assertEquals(5, p.regularClasses().size());
        assertEquals(3.0 / 5, p.avgInheritanceDepth(), 1e-9);
    }

    @Test
    void missingParentMakesRoot() throws IOException {
        ProjectMetrics p = JarAnalyzerHelper.analyze("t", List.of(bytes(P + "$Leaf")));
        assertEquals(0, get(p, "$Leaf").inheritanceDepth());
        assertEquals(0, get(p, "$Leaf").overriddenMethods());
    }

    @Test
    void abc() throws IOException {
        ClassMetrics c = get(analyze(), "$Abc");
        // <init>: invokespecial; f: 2x istore, ifle, invokestatic
        assertEquals(2, c.assignments());
        assertEquals(2, c.branches());
        assertEquals(1, c.conditions());
        assertEquals(3.0, c.abc(), 1e-9);
    }

    @Test
    void overriddenMethods() throws IOException {
        ProjectMetrics p = analyze();
        assertEquals(2, get(p, "$Base").overriddenMethods()); // area, self from Shape
        assertEquals(1, get(p, "$Mid").overriddenMethods());  // area only
        assertEquals(2, get(p, "$Leaf").overriddenMethods()); // self (covariant), pkg
    }

    @Test
    void fields() throws IOException {
        ProjectMetrics p = analyze();
        assertEquals(2, get(p, "$Base").fields());
        assertEquals(3, get(p, "$Leaf").fields());
        assertEquals(0, get(p, "$Mid").fields());
    }

    @Test
    void endToEndJarAndCli(@TempDir Path tmp) throws IOException {
        Path jar = tmp.resolve("t.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String n : NAMES) {
                out.putNextEntry(new JarEntry(P + n + ".class"));
                out.write(bytes(P + n));
            }
            out.putNextEntry(new JarEntry("module-info.class"));
            out.write(new byte[]{1, 2, 3}); // must be ignored, not parsed
        }
        Path report = tmp.resolve("out/r.json");
        assertEquals(0, Main.run(new String[]{jar.toString(), "-o", report.toString()}));
        String json = Files.readString(report);
        assertTrue(json.contains("\"maxInheritanceDepth\": 2"));
        assertTrue(json.contains("Fixtures$Leaf"));
    }

    @Test
    void cliErrors(@TempDir Path tmp) throws IOException {
        assertEquals(1, Main.run(new String[]{}));
        assertEquals(1, Main.run(new String[]{tmp.resolve("missing.jar").toString()}));
        Path notJar = Files.writeString(tmp.resolve("bad.jar"), "not a zip");
        assertEquals(2, Main.run(new String[]{notJar.toString(), "-o", tmp.resolve("r.json").toString()}));
    }
}
