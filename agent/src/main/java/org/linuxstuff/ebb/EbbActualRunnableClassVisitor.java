package org.linuxstuff.ebb;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 *
 */
public class EbbActualRunnableClassVisitor extends ClassVisitor {

    public EbbActualRunnableClassVisitor(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        MethodVisitor mv =
                cv.visitMethod(access, name, desc, signature, exceptions);

        if ((mv != null) && "<init>".equals(name)) {

            GeneratorAdapter generatorAdapter =
                    new GeneratorAdapter(mv, access, name, desc);
            generatorAdapter.loadThis();
            generatorAdapter.invokeStatic(Type.getType(EbbInjected.class),
                    Method.getMethod("void doRun(Runnable)"));
            return generatorAdapter;
        }
        return mv;
    }

}
