package org.linuxstuff.ebb0;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class EbbAgent {


    // We can rewrite classes loaded by the bootstrap class loader
    // iff the agent is loaded by the bootstrap class loader.  It is
    // always *supposed* to be loaded by the bootstrap class loader, but
    // this relies on the Boot-Class-Path attribute in the JAR file always being
    // set to the name of the JAR file that contains this agent, which we cannot
    // guarantee programmatically.
    private static volatile boolean canRewriteBootstrap;

    static boolean canRewriteClass(String className, ClassLoader loader) {
        // There are two conditions under which we don't rewrite:
        //  1. If className was loaded by the bootstrap class loader and
        //  the agent wasn't (in which case the class being rewritten
        //  won't be able to call agent methods).
        //  2. If it is java.lang.ThreadLocal, which can't be rewritten because the
        //  JVM depends on its structure.
        if (((loader == null) && !canRewriteBootstrap) ||
                className.startsWith("java/lang/ThreadLocal")) {
            return false;
        }
        // third_party/java/webwork/*/ognl.jar contains bad class files.  Ugh.
        if (className.startsWith("ognl/")) {
            return false;
        }

        return true;
    }



    public static void premain(String agentArgs, Instrumentation inst) {
//        RunnableInstrumenter.setInstrumentation(inst);

        // Force eager class loading here; we need these classes in order to do
        // instrumentation, so if we don't do the eager class loading, we
        // get a ClassCircularityError when trying to load and instrument
        // this class.
        try {
            Class.forName("sun.security.provider.PolicyFile");
            Class.forName("java.util.ResourceBundle");
            Class.forName("java.util.Date");
            Class.forName(RunnableInstrumenter.class.getCanonicalName());
        } catch (Throwable t) {
            System.out.println("t = " + t);
        }

        if (!inst.isRetransformClassesSupported()) {
            System.err.println("Some JDK classes are already loaded and " +
                    "will not be instrumented.");
        }

        // Don't try to rewrite classes loaded by the bootstrap class
        // loader if this class wasn't loaded by the bootstrap class
        // loader.
//        if (EbbAgent.class.getClassLoader() != null) {
//            canRewriteBootstrap = false;
//            // The loggers aren't installed yet, so we use println.
//            System.err.println("Class loading breakage: " +
//                    "Will not be able to instrument JDK classes");
//            return;
//        }

        canRewriteBootstrap = true;
        List<String> args = Arrays.asList(
                agentArgs == null ? new String[0] : agentArgs.split(","));

        // When "subclassesAlso" is specified, samplers are also invoked when
        // SubclassOfA.<init> is called while only class A is specified to be
        // instrumented.
        RunnableInstrumenter.subclassesAlso = args.contains("subclassesAlso");
        inst.addTransformer(new RunnableInstrumenter(),
                inst.isRetransformClassesSupported());
    }

}
