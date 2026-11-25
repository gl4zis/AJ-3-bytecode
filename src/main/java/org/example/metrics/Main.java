package org.example.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private static final String USAGE = "Usage: analyzer <file.jar> [-o <report.json>]";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        ProgramArgs programArgs;
        try {
            programArgs = parseArgs(args);
        } catch (InputException e) {
            return e.print();
        }

        ProjectMetrics metrics;
        try {
            metrics = JarAnalyzerHelper.analyzeJar(programArgs.jar);
        } catch (IOException | SecurityException e) {
            System.err.println("error: cannot read jar " + programArgs.jar + ": " + e.getMessage());
            return 2;
        }

        System.out.print(ReportFormatter.text(metrics));
        return writeJsonReport(metrics, programArgs.out);
    }

    private static ProgramArgs parseArgs(String[] args) throws InputException {
        Path jar = null, out = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") || args[i].equals("--output")) {
                if (++i >= args.length) {
                    throw new InputException("missing value for -o", 1);
                }
                out = Path.of(args[i]);
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

        return new ProgramArgs(jar, out);
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

    private record ProgramArgs(Path jar, Path out) { }

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
