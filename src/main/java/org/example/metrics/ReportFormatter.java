package org.example.metrics;

import java.util.Locale;

public final class ReportFormatter {
    private ReportFormatter() {}

    public static String text(ProjectMetrics p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(p.source()).append('\n');
        sb.append("Analysis API: ").append(p.api()).append('\n');
        sb.append(String.format(Locale.ROOT, "Classes analyzed: %d (interfaces: %d)%n",
                p.regularClasses().size(), p.classes().size() - p.regularClasses().size()));
        sb.append(String.format(Locale.ROOT, "Max inheritance depth: %d%n", p.maxInheritanceDepth()));
        sb.append(String.format(Locale.ROOT, "Avg inheritance depth: %.3f%n", p.avgInheritanceDepth()));
        sb.append(String.format(Locale.ROOT, "ABC: A=%d B=%d C=%d |ABC|=%.3f%n",
                p.assignments(), p.branches(), p.conditions(), p.abc()));
        sb.append(String.format(Locale.ROOT, "Avg overridden methods: %.3f%n", p.avgOverriddenMethods()));
        sb.append(String.format(Locale.ROOT, "Avg fields per class: %.3f%n%n", p.avgFields()));
        sb.append(String.format(Locale.ROOT, "%-60s %5s %6s %6s %6s %9s %5s %6s%n",
                "Class", "Depth", "A", "B", "C", "ABC", "Ovr", "Fields"));
        for (ClassMetrics c : p.regularClasses()) {
            sb.append(String.format(Locale.ROOT, "%-60s %5d %6d %6d %6d %9.2f %5d %6d%n", c.name(), c.inheritanceDepth(),
                    c.assignments(), c.branches(), c.conditions(), c.abc(), c.overriddenMethods(), c.fields()));
        }
        return sb.toString();
    }

    public static String json(ProjectMetrics p) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"source\": ").append(str(p.source())).append(",\n");
        sb.append("  \"api\": ").append(str(p.api())).append(",\n");
        sb.append("  \"project\": {\n");
        sb.append("    \"classCount\": ").append(p.regularClasses().size()).append(",\n");
        sb.append("    \"interfaceCount\": ").append(p.classes().size() - p.regularClasses().size()).append(",\n");
        sb.append("    \"maxInheritanceDepth\": ").append(p.maxInheritanceDepth()).append(",\n");
        sb.append("    \"avgInheritanceDepth\": ").append(num(p.avgInheritanceDepth())).append(",\n");
        sb.append("    \"abc\": ").append(abc(p.assignments(), p.branches(), p.conditions(), p.abc())).append(",\n");
        sb.append("    \"avgOverriddenMethods\": ").append(num(p.avgOverriddenMethods())).append(",\n");
        sb.append("    \"avgFields\": ").append(num(p.avgFields())).append('\n');
        sb.append("  },\n  \"classes\": [");
        boolean first = true;
        for (ClassMetrics c : p.classes()) {
            sb.append(first ? "\n" : ",\n");
            first = false;
            sb.append("    {\"name\": ").append(str(c.name()))
              .append(", \"interface\": ").append(c.isInterface())
              .append(", \"inheritanceDepth\": ").append(c.inheritanceDepth())
              .append(", \"abc\": ").append(abc(c.assignments(), c.branches(), c.conditions(), c.abc()))
              .append(", \"overriddenMethods\": ").append(c.overriddenMethods())
              .append(", \"fields\": ").append(c.fields()).append('}');
        }
        return sb.append("\n  ]\n}\n").toString();
    }

    private static String abc(int a, int b, int c, double v) {
        return "{\"a\": " + a + ", \"b\": " + b + ", \"c\": " + c + ", \"value\": " + num(v) + "}";
    }

    private static String num(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }
}
