package org.example.metrics;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.*;
import java.util.ArrayList;
import java.util.List;

final class ClassFileParser {

    private ClassFileParser() { }

    static ClassInfo parse(byte[] bytes) {
        ClassModel classModel = ClassFile.of(ClassFile.DebugElementsOption.DROP_DEBUG).parse(bytes);
        String owner = classModel.thisClass().asInternalName();

        List<ClassInfo.MethodInfo> methods = new ArrayList<>();
        for (MethodModel method : classModel.methods()) {
            String name = method.methodName().stringValue();
            int access = method.flags().flagsMask();

            boolean bridge = (access & ClassFile.ACC_BRIDGE) != 0;
            int assignments = 0, branches = 0, conditions = 0;
            String bridgeTarget = null;

            if (method.code().isPresent()) {
                for (CodeElement element : method.code().get()) {
                    switch (element) {
                        case StoreInstruction _ -> assignments++;
                        case InvokeInstruction invoke -> {
                            branches++;
                            if (bridge && bridgeTarget == null && invoke.name().equalsString(name)
                                    && invoke.owner().asInternalName().equals(owner)) {
                                bridgeTarget = invoke.type().stringValue();
                            }
                        }
                        case InvokeDynamicInstruction _ -> branches++;
                        case BranchInstruction branch
                                when branch.opcode() != Opcode.GOTO && branch.opcode() != Opcode.GOTO_W -> conditions++;
                        case TableSwitchInstruction _, LookupSwitchInstruction _ -> conditions++;
                        default -> { }
                    }
                }
            }
            methods.add(new ClassInfo.MethodInfo(
                    name,
                    method.methodType().stringValue(),
                    access,
                    assignments,
                    branches,
                    conditions,
                    bridgeTarget
            ));
        }
        return new ClassInfo(
                owner,
                classModel.superclass().map(ClassEntry::asInternalName).orElse(null),
                classModel.interfaces().stream().map(ClassEntry::asInternalName).toList(),
                classModel.flags().flagsMask(),
                classModel.fields().size(),
                methods
        );
    }
}
