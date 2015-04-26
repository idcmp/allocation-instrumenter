package org.linuxstuff.ebb;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;

public class EbbClassVisitor extends ClassVisitor {

    private final Class<?> clazz;

    private String[] interfaces;

    private String className;

    public EbbClassVisitor(ClassVisitor cv, Class<?> clazz) {
        super(Opcodes.ASM5, cv);
        this.clazz = clazz;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        this.interfaces = interfaces;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        System.out.println("done class: " + className + " interfaces: " + Arrays.deepToString(interfaces));
    }
}
