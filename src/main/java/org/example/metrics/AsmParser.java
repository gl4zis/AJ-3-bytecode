package org.example.metrics;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

final class AsmParser {

    private AsmParser() { }

    static ClassInfo parse(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<ClassInfo.MethodInfo> methods = new ArrayList<>();
        for (MethodNode methodNode : classNode.methods) {
            int assignments = 0, branches = 0, conditions = 0;
            String bridgeTarget = null;
            boolean bridge = (methodNode.access & Opcodes.ACC_BRIDGE) != 0;
            for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                int op = insn.getOpcode();
                if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
                    assignments++;
                } else if (op >= Opcodes.INVOKEVIRTUAL && op <= Opcodes.INVOKEDYNAMIC) {
                    branches++;
                } else if ((op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE) || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL
                        || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) {
                    conditions++;
                }
                if (bridge && bridgeTarget == null && insn instanceof MethodInsnNode mi
                        && mi.name.equals(methodNode.name) && mi.owner.equals(classNode.name)) {
                    bridgeTarget = mi.desc;
                }
            }
            methods.add(new ClassInfo.MethodInfo(
                    methodNode.name,
                    methodNode.desc,
                    methodNode.access,
                    assignments,
                    branches,
                    conditions,
                    bridgeTarget
            ));
        }
        return new ClassInfo(
                classNode.name,
                classNode.superName,
                List.copyOf(classNode.interfaces),
                classNode.access,
                classNode.fields.size(),
                methods
        );
    }
}
