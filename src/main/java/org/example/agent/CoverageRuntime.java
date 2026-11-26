package org.example.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CoverageRuntime {
    private static final int LINES_PER_PAGE = 4096;
    private static final int MAX_PAGES = 32_768;
    private static final boolean[][] EXECUTED = new boolean[MAX_PAGES][];
    private static final Map<Line, Integer> IDS = new HashMap<>();
    private static final List<Line> LINES = new ArrayList<>();

    private CoverageRuntime() {}

    // Don't remove!! Calls via transformer
    public static void hit(int id) {
        EXECUTED[pageOf(id)][slotOf(id)] = true;
    }

    static synchronized int probe(String className, String source, int lineNumber) {
        return IDS.computeIfAbsent(new Line(className, source, lineNumber), line -> {
            int id = LINES.size();
            if (EXECUTED[pageOf(id)] == null) {
                EXECUTED[pageOf(id)] = new boolean[LINES_PER_PAGE];
            }
            LINES.add(line);
            return id;
        });
    }

    static void installReportCallback(Path output) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String json = report();
            try {
                Path parent = output.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(output, json);
                System.out.println("[coverage] report written to " + output.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[coverage] cannot write report " + output + ": " + e.getMessage());
            }
        }, "coverage-report"));
    }

    private static synchronized String report() {
        Map<String, TreeMap<Integer, Boolean>> byClass = new TreeMap<>();
        Map<String, String> sources = new HashMap<>();

        for (int id = 0; id < LINES.size(); id++) {
            Line line = LINES.get(id);
            boolean hit = EXECUTED[pageOf(id)][slotOf(id)];

            byClass.computeIfAbsent(line.className(), _ -> new TreeMap<>())
                    .merge(line.lineNumber(), hit, Boolean::logicalOr);

            if (line.source() != null) {
                sources.put(line.className(), line.source());
            }
        }

        int total = 0, covered = 0;
        StringBuilder classes = new StringBuilder();
        StringBuilder text = new StringBuilder();

        for (var classEntry : byClass.entrySet()) {
            List<Integer> hitLines = new ArrayList<>(), notHitLines = new ArrayList<>();
            classEntry.getValue()
                    .forEach((line, hit) -> (hit ? hitLines : notHitLines)
                    .add(line));

            total += classEntry.getValue().size();
            covered += hitLines.size();

            if (!classes.isEmpty()) {
                classes.append(",\n");
            }
            String name = classEntry.getKey().replace('/', '.');
            classes.append("    {\"name\": \"").append(name).append('"')
                    .append(", \"source\": ").append(sources.containsKey(classEntry.getKey()) ? "\"" + sources.get(classEntry.getKey()) + "\"" : "null")
                    .append(", \"lines\": ").append(classEntry.getValue().size())
                    .append(", \"covered\": ").append(hitLines.size())
                    .append(", \"percent\": ").append(percentage(hitLines.size(), classEntry.getValue().size()))
                    .append(", \"coveredLines\": ").append(hitLines)
                    .append(", \"missedLines\": ").append(notHitLines).append('}');
            text.append(
                    String.format(Locale.ROOT, "  %-60s %4d/%-4d %6s%%\n",
                    name,
                    hitLines.size(),
                    classEntry.getValue().size(),
                    percentage(hitLines.size(), classEntry.getValue().size()))
            );
        }
        System.out.printf(
                Locale.ROOT, "[coverage] %d/%d lines (%s%%) in %d classes%n%s",
                covered,
                total,
                percentage(covered, total),
                byClass.size(),
                text
        );
        return """
                {
                  "lines": %d,
                  "covered": %d,
                  "percent": %s,
                  "classes": [
                %s
                  ]
                }
                """.formatted(total, covered, percentage(covered, total), classes);
    }

    private static String percentage(int a, int b) {
        return String.format(Locale.ROOT, "%.2f", b == 0 ? 0.0 : 100.0 * a / b);
    }

    private static int pageOf(int id) {
        return id / LINES_PER_PAGE;
    }

    private static int slotOf(int id) {
        return id % LINES_PER_PAGE;
    }

    private record Line(String className, String source, int lineNumber) { }
}
