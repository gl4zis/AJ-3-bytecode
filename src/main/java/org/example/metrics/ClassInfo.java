package org.example.metrics;

import java.util.List;

public record ClassInfo(
        String name,
        String superName,
        List<String> interfaces,
        int access,
        int fieldCount,
        List<MethodInfo> methods
) {

    public record MethodInfo(
            String name,
            String desc,
            int access,
            int assignments,
            int branches,
            int conditions,
            String bridgeTarget
    ) { }
}
