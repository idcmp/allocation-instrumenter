package com.google.monitoring.runtime.instrumentation;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class RunnableInstrumenter implements ClassFileTransformer {
    // Implementation details: uses the java.lang.instrument API to
    // insert an INVOKESTATIC call to a specified method directly prior to
    // constructor return for the given class.

    private static final Logger logger =
            Logger.getLogger(ConstructorInstrumenter.class.getName());
    private static ConcurrentHashMap<Class<?>, List<ConstructorCallback<?>>>
            samplerMap =
            new ConcurrentHashMap<Class<?>, List<ConstructorCallback<?>>>();

    /**
     * We have a read-modify-write operation when doing a put in samplerMap
     * (above) and retransforming the class.  This lock protects multiple threads
     * from performing that operation concurrently.
     */
    private static final Object samplerPutAtomicityLock = new Object();

    /**
     * Whether to call the samplers when subclasses of the given class are
     * constructed.
     */
    static boolean subclassesAlso;

    // Only for package access (specifically, AllocationInstrumenter)
    RunnableInstrumenter() { }

    /**
     * Ensures that the given sampler will be invoked every time a constructor
     * for class c is invoked.
     *
     * @param c The class to be tracked
     * @param sampler the code to be invoked when an instance of c is constructed
     * @throws UnmodifiableClassException if c cannot be modified.
     */
    public static void instrumentClass(Class<?> c, ConstructorCallback<?> sampler)
            throws UnmodifiableClassException {
        // IMPORTANT: Don't forget that other threads may be accessing this
        // class while this code is running.  Specifically, the class may be
        // executed directly after the retransformClasses is called.  Thus, we need
        // to be careful about what happens after the retransformClasses call.
        synchronized (samplerPutAtomicityLock) {
            List<ConstructorCallback<?>> list = samplerMap.get(c);
            if (list == null) {
                CopyOnWriteArrayList<ConstructorCallback<?>> samplerList =
                        new CopyOnWriteArrayList<ConstructorCallback<?>>();
                samplerList.add(sampler);
                samplerMap.put(c, samplerList);
                Instrumentation inst = AllocationRecorder.getInstrumentation();
                Class<?>[] cs = new Class<?>[1];
                cs[0] = c;
                inst.retransformClasses(c);
            } else {
                list.add(sampler);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override public byte[] transform(
            ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer)  {
        System.out.println("className = " + className);
        if ((classBeingRedefined == null) ||
                (!samplerMap.containsKey(classBeingRedefined))) {
            return null;
        }
        if (!AllocationInstrumenter.canRewriteClass(className, loader)) {
            throw new RuntimeException(
                    new UnmodifiableClassException("cannot instrument " + className));
        }
        return instrument(classfileBuffer, classBeingRedefined);
    }

    /**
     * Given the bytes representing a class, add invocations of the
     * ConstructorCallback method to the constructor.
     *
     * @param originalBytes the original <code>byte[]</code> code.
     * @param classBeingRedefined the class being redefined.
     * @return the instrumented <code>byte[]</code> code.
     */
    public static byte[] instrument(
            byte[] originalBytes, Class<?> classBeingRedefined) {
        try {
            ClassReader cr = new ClassReader(originalBytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            VerifyingClassAdapter vcw =
                    new VerifyingClassAdapter(cw, originalBytes, cr.getClassName());
            ClassVisitor adapter =
                    new ConstructorClassAdapter(vcw, classBeingRedefined);

            cr.accept(adapter, ClassReader.SKIP_FRAMES);

            return vcw.toByteArray();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Failed to instrument class.", e);
            throw e;
        } catch (Error e) {
            logger.log(Level.WARNING, "Failed to instrument class.", e);
            throw e;
        }
    }

    /**
     * The per-method transformations to make.  Really only affects the
     * <init> methods.
     */
    static class ConstructorMethodAdapter extends MethodVisitor {
        /**
         * The LocalVariablesSorter used in this adapter.  Lame that it's public but
         * the ASM architecture requires setting it from the outside after this
         * AllocationMethodAdapter is fully constructed and the LocalVariablesSorter
         * constructor requires a reference to this adapter.  The only setter of
         * this should be AllocationClassAdapter.visitMethod().
         */
        public LocalVariablesSorter lvs = null;
        Class<?> cl;
        ConstructorMethodAdapter(MethodVisitor mv, Class<?> cl) {
            super(Opcodes.ASM5, mv);
            this.cl = cl;
        }

        /**
         * Inserts the appropriate INVOKESTATIC call
         */
        @Override public void visitInsn(int opcode) {
            if ((opcode == Opcodes.ARETURN) ||
                    (opcode == Opcodes.IRETURN) ||
                    (opcode == Opcodes.LRETURN) ||
                    (opcode == Opcodes.FRETURN) ||
                    (opcode == Opcodes.DRETURN)) {
                throw new RuntimeException(new UnmodifiableClassException(
                        "Constructors are supposed to return void"));
            }
            if (opcode == Opcodes.RETURN) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/google/monitoring/runtime/instrumentation/ConstructorInstrumenter",
                        "invokeSamplers",
                        "(Ljava/lang/Object;)V",
                        false);
            }
            super.visitInsn(opcode);
        }
    }

    /**
     * Bytecode is rewritten to invoke this method; it calls the sampler for
     * the given class.  Note that, unless the javaagent command line argument
     * "subclassesAlso" is specified, it won't do anything if o is a subclass of
     * the class that was supposed to be tracked.
     * @param o the object passed to the samplers.
     */
    @SuppressWarnings("unchecked")
    public static void invokeSamplers(Object o) {
        Class<?> currentClass = o.getClass();
        while (currentClass != null) {
            List<ConstructorCallback<?>> samplers = samplerMap.get(currentClass);
            if (samplers != null) {
                for (ConstructorCallback sampler : samplers) {
                    sampler.sample(o);
                }
                // Return once the first list of registered samplers are found and invoked.
                return;
            } else {
                // When subclassesAlso is not specified (default), return if no
                // samplers are registered with the type of the currently-constructed
                // object.  Otherwise, traverse upward the class hierarchy.
                if (!subclassesAlso) {
                    return;
                }
                currentClass = currentClass.getSuperclass();
            }
        }
    }

    /**
     * The class that deals with per-class transformations.  Basically, invokes
     * the per-method transformer above if the method is an {@code <init>} method.
     */
    static class ConstructorClassAdapter extends ClassVisitor {
        Class<?> cl;
        public ConstructorClassAdapter(ClassVisitor cv, Class<?> cl) {
            super(Opcodes.ASM5, cv);
            this.cl = cl;
        }

        /**
         * For each method in the class being instrumented,
         * <code>visitMethod</code> is called and the returned
         * MethodVisitor is used to visit the method.  Note that a new
         * MethodVisitor is constructed for each method.
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            MethodVisitor mv =
                    cv.visitMethod(access, name, desc, signature, exceptions);

            if ((mv != null) && "<init>".equals(name)){
                ConstructorMethodAdapter aimv = new ConstructorMethodAdapter(mv, cl);
                LocalVariablesSorter lvs = new LocalVariablesSorter(access, desc, aimv);
                aimv.lvs = lvs;
                mv = lvs;
            }
            return mv;
        }
    }

}
