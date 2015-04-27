package org.linuxstuff.ebb;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.linuxstuff.ebb.YesNoMaybe.*;

/**
 * We consider a class Runnable if:
 * <ul>
 * <li>(NO) it's not abstract: we're going to instrument the constructor of a Runnable</li>
 * <li>(MAYBE) it has an explicit parent class (because maybe it extends something Runnable)</li>
 * <li>(YES) it implements Runnable</li>
 * <li>(MAYBE) it has a method named run()</li>
 * <li>(YES) if any of the already loaded interfaces extend Runnable</li>
 * <li>(YES) if the parent class is already loaded and extends Runnable</li>
 * <li>(MAYBE) if class has interfaces or parent class not already loaded.</li>
 * </ul>
 * <p>
 * This class will yield a YES, NO, or MAYBE.
 */
public class EbbIsRunnableClassVisitor extends ClassVisitor {

    private final Class<?> clazz;

    private String[] interfaces;

    private String className;

    private YesNoMaybe runnable = MAYBE;
    private String superName;

    public EbbIsRunnableClassVisitor(ClassVisitor cv, Class<?> clazz) {
        super(Opcodes.ASM5, cv);
        this.clazz = clazz;
    }

    public YesNoMaybe isRunnable() {
        return runnable;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        this.superName = superName;
        this.interfaces = interfaces;

        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            runnable = NO;
            return;
        }

        for (String anInterface : interfaces) {
            if ("java/lang/Runnable".equals(anInterface)) {
                runnable = YES;
                return;
            }
        }

        if (runnable != YES && superName != null) {
            runnable = MAYBE;
        }

    }

    private void lookup(String superName, String[] interfaces) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        if (interfaces != null) {
            for (String anInterface : interfaces) {
                try {
                    Class<?> aClass = loader.loadClass(anInterface.replace('/', '.'));
                    if (Runnable.class.isAssignableFrom(aClass)) {
                        runnable = YES;
                        return;
                    }
                } catch (ClassNotFoundException e) {
                    if (runnable != YES) {
                        runnable = MAYBE;
                    }
                }
            }
        }

        if (superName != null) {
            try {
                Class<?> aClass = loader.loadClass(superName.replace('/', '.'));
                if (Runnable.class.isAssignableFrom(aClass)) {
                    runnable = YES;
                    return;
                }
            } catch (ClassNotFoundException e) {
                if (runnable != YES) {
                    runnable = MAYBE;
                }
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);


        if (signature == null && exceptions == null && "run".equals(name) && "()V".equals(desc) && runnable != YES) {
            runnable = MAYBE;
        }

        return methodVisitor;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        if (runnable == MAYBE) {
            lookup(superName, interfaces);
        }
    }
}
