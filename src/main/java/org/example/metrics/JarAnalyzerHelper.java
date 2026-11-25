package org.example.metrics;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarAnalyzerHelper {

    private JarAnalyzerHelper() { }

    public static ProjectMetrics analyzeJar(Path jar) throws IOException {
        return analyze(jar.getFileName().toString(), readClassFiles(jar));
    }

    static ProjectMetrics analyze(String source, Collection<byte[]> classFiles) {
        JarAnalyzer analyzer = new JarAnalyzer();
        for (byte[] b : classFiles) {
            ClassNode node = new ClassNode();
            try {
                new ClassReader(b).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (RuntimeException ex) {
                System.err.println("warning: skipping malformed class file: " + ex);
                continue;
            }
            if (node.name.endsWith("module-info") || node.name.endsWith("package-info")) {
                continue;
            }
            analyzer.putClass(node.name, node);
        }

        var result = analyzer.getClasses().stream()
                .map(analyzer::metrics)
                .sorted(Comparator.comparing(ClassMetrics::name))
                .toList();

        return new ProjectMetrics(source, result);
    }

    private static List<byte[]> readClassFiles(Path jar) throws IOException {
        List<byte[]> bytes = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                String n = entry.getName();
                // multi-release variants are not supported: only the base version is analyzed
                if (
                        entry.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/")
                                || n.endsWith("module-info.class") || n.endsWith("package-info.class")
                ) {
                    continue;
                }
                try (InputStream in = jf.getInputStream(entry)) {
                    bytes.add(in.readAllBytes());
                }
            }
        }

        return bytes;
    }
}
