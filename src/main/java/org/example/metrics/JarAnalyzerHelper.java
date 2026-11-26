package org.example.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarAnalyzerHelper {

    public enum Api {
        ASM(AsmParser::parse),
        CLASSFILE(ClassFileParser::parse);

        private final Function<byte[], ClassInfo> parser;

        Api(Function<byte[], ClassInfo> parser) {
            this.parser = parser;
        }
    }

    private JarAnalyzerHelper() { }

    static ProjectMetrics analyze(String source, Collection<byte[]> classFiles, Api api) {
        JarAnalyzer analyzer = new JarAnalyzer();
        for (byte[] classFile : classFiles) {
            ClassInfo info;
            try {
                info = api.parser.apply(classFile);
            } catch (RuntimeException ex) {
                System.err.println("warning: skipping malformed class file: " + ex);
                continue;
            }
            if (info.name().endsWith("module-info") || info.name().endsWith("package-info")) {
                continue;
            }
            analyzer.putClass(info);
        }

        var result = analyzer.getClasses().stream()
                .map(analyzer::metrics)
                .sorted(Comparator.comparing(ClassMetrics::name))
                .toList();

        return new ProjectMetrics(source, api.name(), result);
    }

    static List<byte[]> readClassFiles(Path jar) throws IOException {
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
