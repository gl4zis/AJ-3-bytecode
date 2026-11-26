package org.example.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CoverageAgent {
    private static final String SELF = CoverageAgent.class.getPackageName().replace('.', '/') + "/";

    private CoverageAgent() {}

    public static void premain(String args, Instrumentation inst) {
        var programArgs = parseProgramArgs(args);
        CoverageRuntime.installReportCallback(programArgs.output);
        inst.addTransformer(new CoverageClassTransformer(programArgs.includes));
    }

    private static ProgramArgs parseProgramArgs(String args) {
        List<String> includes = new ArrayList<>();
        Path output = Path.of("coverage-report.json");

        if (args != null && !args.isBlank()) {
            for (String opt : args.split(",")) {
                String[] keyVal = opt.split("=", 2);
                if (keyVal.length != 2) {
                    throw new IllegalArgumentException("coverage agent: bad option '" + opt + "'");
                }
                switch (keyVal[0].trim()) {
                    case "include" -> {
                        for (String pkg : keyVal[1].split(":")) {
                            if (!pkg.isBlank()) {
                                includes.add(pkg.trim().replace('.', '/') + "/");
                            }
                        }
                    }
                    case "output" -> output = Path.of(keyVal[1].trim());
                    default -> throw new IllegalArgumentException("coverage agent: unknown option '" + keyVal[0] + "'");
                }
            }
        }
        return new ProgramArgs(output, includes);
    }

    static boolean isAgentClass(String className) {
        return className.startsWith(SELF) && className.indexOf('/', SELF.length()) < 0;
    }

    private record ProgramArgs(Path output, List<String> includes) { }
}
