package org.example.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Main {
    private static final String USAGE = """
            Usage: analyzer <file.jar> [-o <report.json>] [--api asm|classfile] [--compare]
              -o, --output   JSON report path (default: <jar-name>-report.json)
              --api          bytecode reader: asm (default) or classfile (java.lang.classfile, JDK 24+)
              --compare      run both readers, check their metrics are identical and print timings""";

    private static final int COMPARE_RUNS = 5;

    static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        ProgramArgs programArgs;
        try {
            programArgs = parseArgs(args);
        } catch (InputException e) {
            return e.print();
        }

        List<byte[]> classFiles;
        try {
            classFiles = JarAnalyzerHelper.readClassFiles(programArgs.jar);
        } catch (IOException | SecurityException e) {
            System.err.println("error: cannot read jar " + programArgs.jar + ": " + e.getMessage());
            return 2;
        }

        String source = programArgs.jar.getFileName().toString();
        if (programArgs.compare) {
            return compareApis(source, classFiles);
        }

        ProjectMetrics metrics = JarAnalyzerHelper.analyze(source, classFiles, programArgs.api);
        System.out.print(ReportFormatter.text(metrics));
        return writeJsonReport(metrics, programArgs.out);
    }

    private static ProgramArgs parseArgs(String[] args) throws InputException {
        Path jar = null, out = null;
        JarAnalyzerHelper.Api api = JarAnalyzerHelper.Api.ASM;
        boolean compare = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") || args[i].equals("--output")) {
                if (++i >= args.length) {
                    throw new InputException("missing value for -o", 1);
                }
                out = Path.of(args[i]);
            } else if (args[i].equals("--api")) {
                if (++i >= args.length) {
                    throw new InputException("missing value for --api", 1);
                }
                try {
                    api = JarAnalyzerHelper.Api.valueOf(args[i].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new InputException("unknown api: " + args[i], 1);
                }
            } else if (args[i].equals("--compare")) {
                compare = true;
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                throw new InputException("", 0);
            } else if (jar == null) {
                jar = Path.of(args[i]);
            } else {
                throw new InputException("unexpected argument: " + args[i], 1);
            }
        }
        if (jar == null) {
            throw new InputException("no jar file given", 1);
        }
        if (!Files.isRegularFile(jar)) {
            throw new InputException("file not found: " + jar, 1);
        }
        if (out == null) {
            out = Path.of(jar.getFileName().toString().replaceFirst("\\.jar$", "") + "-report.json");
        }

        return new ProgramArgs(jar, out, api, compare);
    }

    private static int compareApis(String source, List<byte[]> classFiles) {
        System.out.printf("%s: %d class files, best of %d runs after warm-up\n", source, classFiles.size(), COMPARE_RUNS);
        List<ClassMetrics> reference = null;
        boolean same = true;
        for (JarAnalyzerHelper.Api api : JarAnalyzerHelper.Api.values()) {
            List<ClassMetrics> metrics = JarAnalyzerHelper.analyze(source, classFiles, api).classes(); // warm-up
            long best = Long.MAX_VALUE;
            for (int i = 0; i < COMPARE_RUNS; i++) {
                long start = System.nanoTime();
                JarAnalyzerHelper.analyze(source, classFiles, api);
                best = Math.min(best, System.nanoTime() - start);
            }
            System.out.printf(Locale.ROOT, "  %-9s %8.1f ms%n", api.name().toLowerCase(Locale.ROOT), best / 1e6);
            if (reference == null) {
                reference = metrics;
            } else {
                same &= reference.equals(metrics);
            }
        }
        System.out.println(same ? "Metrics are identical." : "Metrics DIFFER.");
        return same ? 0 : 4;
    }

    private static int writeJsonReport(ProjectMetrics metrics, Path out) {
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, ReportFormatter.json(metrics));
        } catch (IOException e) {
            System.err.println("error: cannot write report " + out + ": " + e.getMessage());
            return 3;
        }
        System.out.println("\nJSON report written to " + out);
        return 0;
    }

    private record ProgramArgs(Path jar, Path out, JarAnalyzerHelper.Api api, boolean compare) { }

    private static class InputException extends Exception {
        private final int code;

        private InputException(String message, int code) {
            super(message);
            this.code = code;
        }

        private int print() {
            System.err.println(getMessage() + "\n" + USAGE);
            return code;
        }
    }
}
