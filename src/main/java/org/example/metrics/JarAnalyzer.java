package org.example.metrics;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public final class JarAnalyzer {

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Map<String, Integer> depthCache = new HashMap<>();

    public void putClass(String name, ClassNode node) {
        classes.put(name, node);
    }

    public Collection<ClassNode> getClasses() {
        return List.copyOf(classes.values());
    }

    ClassMetrics metrics(ClassNode cNode) {
        int assignments = 0, branches = 0, conditions = 0;
        for (MethodNode methodNode : cNode.methods) {
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                int op = insn.getOpcode();
                if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
                    assignments++;
                }
                else if (op >= Opcodes.INVOKEVIRTUAL && op <= Opcodes.INVOKEDYNAMIC) {
                    branches++;
                }
                else if ((op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE) || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL
                        || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) {
                    conditions++;
                }
            }
        }
        return new ClassMetrics(
                cNode.name.replace('/', '.'),
                isInterface(cNode),
                depth(cNode.name),
                assignments,
                branches,
                conditions,
                overriddenCount(cNode),
                cNode.fields.size()
        );
    }

    private static boolean isInterface(ClassNode c) {
        return (c.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private int depth(String name) {
        Integer cached = depthCache.get(name);
        if (cached != null) {
            return cached;
        }

        ClassNode classNode = classes.get(name);
        int d = 0;
        if (classNode != null && classNode.superName != null && classes.containsKey(classNode.superName)) {
            depthCache.put(name, 0); // cycle guard for corrupted input
            d = depth(classNode.superName) + 1;
        }
        depthCache.put(name, d);
        return d;
    }

    private int overriddenCount(ClassNode classNode) {
        Map<String, List<String>> bridgedDescs = new HashMap<>();
        for (MethodNode methodNode : classNode.methods) {
            if ((methodNode.access & Opcodes.ACC_BRIDGE) == 0) {
                continue;
            }

            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode mi && mi.name.equals(methodNode.name) && mi.owner.equals(classNode.name)) {
                    bridgedDescs.computeIfAbsent(mi.desc, k -> new ArrayList<>()).add(methodNode.desc);
                    break;
                }
            }
        }

        List<ClassNode> ancestors = ancestors(classNode);
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.name.startsWith("<") || (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE
                    | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                continue;
            }

            List<String> descs = new ArrayList<>(bridgedDescs.getOrDefault(method.desc, List.of()));
            descs.add(method.desc);

            boolean overridden = ancestors.stream().anyMatch(ancestor ->
                    descs.stream().anyMatch(desc -> overridable(ancestor, classNode, method.name, desc)));
            if (overridden) {
                count++;
            }
        }
        return count;
    }

    private static boolean overridable(ClassNode ancestor, ClassNode classNode, String name, String desc) {
        for (MethodNode methodNode : ancestor.methods) {
            if (!methodNode.name.equals(name) || !methodNode.desc.equals(desc)) {
                continue;
            }
            if ((methodNode.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0) {
                return false;
            }
            boolean packagePrivate = (methodNode.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0;
            return !packagePrivate || pkg(ancestor.name).equals(pkg(classNode.name));
        }
        return false;
    }

    private static String pkg(String internalName) {
        int i = internalName.lastIndexOf('/');
        return i < 0 ? "" : internalName.substring(0, i);
    }

    private List<ClassNode> ancestors(ClassNode classNode) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (classNode.superName != null) {
            queue.add(classNode.superName);
        }
        queue.addAll(classNode.interfaces);

        while (!queue.isEmpty()) {
            String name = queue.poll();
            ClassNode node = classes.get(name);

            if (node == null || !seen.add(name)) {
                continue;
            }
            if (node.superName != null) {
                queue.add(node.superName);
            }
            queue.addAll(node.interfaces);
        }
        return seen.stream().map(classes::get).toList();
    }

}
