package org.example.metrics;

public record ClassMetrics(
        String name,
        boolean isInterface,
        int inheritanceDepth,
        int assignments,
        int branches,
        int conditions,
        int overriddenMethods,
        int fields
) {

    public double abc() {
        return Math.sqrt(
                (double) assignments * assignments +
                (double) branches * branches +
                (double) conditions * conditions
        );
    }
}
