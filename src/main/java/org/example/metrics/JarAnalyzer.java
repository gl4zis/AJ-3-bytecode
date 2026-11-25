package org.example.metrics;

import java.util.*;

import static java.lang.classfile.ClassFile.*;

public final class JarAnalyzer {

    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, Integer> depthCache = new HashMap<>();

    public void putClass(ClassInfo info) {
        classes.put(info.name(), info);
    }

    public Collection<ClassInfo> getClasses() {
        return List.copyOf(classes.values());
    }

    ClassMetrics metrics(ClassInfo classInfo) {
        int assignments = 0, branches = 0, conditions = 0;
        for (ClassInfo.MethodInfo method : classInfo.methods()) {
            assignments += method.assignments();
            branches += method.branches();
            conditions += method.conditions();
        }
        return new ClassMetrics(
                classInfo.name().replace('/', '.'),
                isInterface(classInfo),
                depth(classInfo.name()),
                assignments,
                branches,
                conditions,
                overriddenCount(classInfo),
                classInfo.fieldCount()
        );
    }

    private static boolean isInterface(ClassInfo c) {
        return (c.access() & ACC_INTERFACE) != 0;
    }

    private int depth(String name) {
        Integer cached = depthCache.get(name);
        if (cached != null) {
            return cached;
        }

        ClassInfo classInfo = classes.get(name);
        int d = 0;
        if (classInfo != null && classInfo.superName() != null && classes.containsKey(classInfo.superName())) {
            depthCache.put(name, 0); // cycle guard for corrupted input
            d = depth(classInfo.superName()) + 1;
        }
        depthCache.put(name, d);
        return d;
    }

    private int overriddenCount(ClassInfo classInfo) {
        Map<String, List<String>> bridgedDescs = new HashMap<>();
        for (ClassInfo.MethodInfo method : classInfo.methods()) {
            if (method.bridgeTarget() != null) {
                bridgedDescs.computeIfAbsent(method.bridgeTarget(), k -> new ArrayList<>()).add(method.desc());
            }
        }

        List<ClassInfo> ancestors = ancestors(classInfo);
        int count = 0;
        for (ClassInfo.MethodInfo method : classInfo.methods()) {
            if (method.name().startsWith("<")
                    || (method.access() & (ACC_STATIC | ACC_PRIVATE | ACC_SYNTHETIC | ACC_BRIDGE)) != 0) {
                continue;
            }

            List<String> descs = new ArrayList<>(bridgedDescs.getOrDefault(method.desc(), List.of()));
            descs.add(method.desc());

            boolean overridden = ancestors.stream().anyMatch(ancestor ->
                    descs.stream().anyMatch(desc -> overridable(ancestor, classInfo, method.name(), desc)));
            if (overridden) {
                count++;
            }
        }
        return count;
    }

    private static boolean overridable(ClassInfo ancestor, ClassInfo classInfo, String name, String desc) {
        for (ClassInfo.MethodInfo method : ancestor.methods()) {
            if (!method.name().equals(name) || !method.desc().equals(desc)) {
                continue;
            }
            if ((method.access() & (ACC_STATIC | ACC_PRIVATE)) != 0) {
                return false;
            }
            boolean packagePrivate = (method.access() & (ACC_PUBLIC | ACC_PROTECTED)) == 0;
            return !packagePrivate || pkg(ancestor.name()).equals(pkg(classInfo.name()));
        }
        return false;
    }

    private static String pkg(String internalName) {
        int i = internalName.lastIndexOf('/');
        return i < 0 ? "" : internalName.substring(0, i);
    }

    private List<ClassInfo> ancestors(ClassInfo classInfo) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (classInfo.superName() != null) {
            queue.add(classInfo.superName());
        }
        queue.addAll(classInfo.interfaces());

        while (!queue.isEmpty()) {
            String name = queue.poll();
            ClassInfo info = classes.get(name);

            if (info == null || !seen.add(name)) {
                continue;
            }
            if (info.superName() != null) {
                queue.add(info.superName());
            }
            queue.addAll(info.interfaces());
        }
        return seen.stream().map(classes::get).toList();
    }

}
