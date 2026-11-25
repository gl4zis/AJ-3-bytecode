package org.example.metrics;

import java.util.List;

public record ProjectMetrics(
        String source,
        List<ClassMetrics> classes
) {

    public List<ClassMetrics> regularClasses() {
        return classes.stream().filter(c -> !c.isInterface()).toList();
    }

    public int maxInheritanceDepth() {
        return regularClasses().stream().mapToInt(ClassMetrics::inheritanceDepth).max().orElse(0);
    }

    public double avgInheritanceDepth() {
        return regularClasses().stream().mapToInt(ClassMetrics::inheritanceDepth).average().orElse(0);
    }

    public double avgOverriddenMethods() {
        return regularClasses().stream().mapToInt(ClassMetrics::overriddenMethods).average().orElse(0);
    }

    public double avgFields() {
        return regularClasses().stream().mapToInt(ClassMetrics::fields).average().orElse(0);
    }

    public int assignments() {
        return classes.stream().mapToInt(ClassMetrics::assignments).sum();
    }

    public int branches() {
        return classes.stream().mapToInt(ClassMetrics::branches).sum();
    }

    public int conditions() {
        return classes.stream().mapToInt(ClassMetrics::conditions).sum();
    }

    public double abc() {
        double a = assignments(), b = branches(), c = conditions();
        return Math.sqrt(a * a + b * b + c * c);
    }
}
