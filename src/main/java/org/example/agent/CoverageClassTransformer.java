package org.example.agent;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoverageClassTransformer implements ClassFileTransformer {
    private static final ClassDesc RUNTIME = ClassDesc.of(CoverageRuntime.class.getName());
    private static final MethodTypeDesc HIT = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);

    private final List<String> includes;

    CoverageClassTransformer(List<String> includes) {
        this.includes = includes;
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> redefined,
            ProtectionDomain pd,
            byte[] bytes
    ) {
        if (loader == null || className == null || module.isNamed() || CoverageAgent.isAgentClass(className)) {
            return null;
        }
        if (!includes.isEmpty() && includes.stream().noneMatch(className::startsWith)) {
            return null;
        }
        try {
            return instrument(className, loader, bytes);
        } catch (Throwable t) {
            System.err.println("[coverage] cannot instrument " + className + ": " + t);
            return null;
        }
    }

    static byte[] instrument(String className, ClassLoader loader, byte[] bytes) {
        ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing(loader).cached()
        ));
        ClassModel classModel = classFile.parse(bytes);
        String source = classModel.findAttribute(Attributes.sourceFile())
                .map(SourceFileAttribute::sourceFile)
                .map(Utf8Entry::stringValue)
                .orElse(null);

        AtomicBoolean changed = new AtomicBoolean(false);
        byte[] result = classFile.transformClass(
                classModel,
                ClassTransform.transformingMethodBodies(CodeTransform.ofStateful(() -> new CodeTransform() {
                    int pendingLine = -1;

                    @Override
                    public void accept(CodeBuilder codeBuilder, CodeElement codeElement) {
                        if (pendingLine >= 0 && codeElement instanceof Instruction) {
                            codeBuilder.loadConstant(CoverageRuntime.probe(className, source, pendingLine));
                            codeBuilder.invokestatic(RUNTIME, "hit", HIT);
                            changed.set(true);
                            pendingLine = -1;
                        }
                        if (codeElement instanceof LineNumber ln) {
                            pendingLine = ln.line();
                        }
                        codeBuilder.with(codeElement);
                    }
                }))
        );
        return changed.get() ? result : null;
    }
}
