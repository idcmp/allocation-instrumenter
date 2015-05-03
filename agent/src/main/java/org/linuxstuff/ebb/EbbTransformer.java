package org.linuxstuff.ebb;

import org.linuxstuff.ebb0.VerifyingClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.linuxstuff.ebb.YesNoMaybe.NO;

public class EbbTransformer implements ClassFileTransformer {

    private static final Logger logger = Logger.getLogger(EbbTransformer.class.getName());

    EbbTransformer() {
    }


    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain
            protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        try {
            {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                VerifyingClassAdapter vcw =
                        new VerifyingClassAdapter(cw, classfileBuffer, cr.getClassName());

                EbbIsRunnableClassVisitor adapter =
                        new EbbIsRunnableClassVisitor(vcw, classBeingRedefined);

                cr.accept(adapter, ClassReader.SKIP_FRAMES);

                if (adapter.isRunnable() == NO) {
                    return vcw.toByteArray();
                }
            }

            {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                VerifyingClassAdapter vcw =
                        new VerifyingClassAdapter(cw, classfileBuffer, cr.getClassName());

                EbbActualRunnableClassVisitor adapter2 =
                        new EbbActualRunnableClassVisitor(vcw);

                cr.accept(adapter2, ClassReader.SKIP_FRAMES);
                return vcw.toByteArray();
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Failed to instrument class " + className, e);
            throw e;
        } catch (Error e) {
            logger.log(Level.WARNING, "Failed to instrument class " + className, e);
            throw e;
        }
    }
}