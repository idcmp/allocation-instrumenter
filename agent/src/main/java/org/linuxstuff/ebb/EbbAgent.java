package org.linuxstuff.ebb;

import java.lang.instrument.Instrumentation;

public class EbbAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        // Force eager class loading here; we need these classes in order to do
        // instrumentation, so if we don't do the eager class loading, we
        // get a ClassCircularityError when trying to load and instrument
        // this class.
        try {
            Class.forName("sun.security.provider.PolicyFile");
            Class.forName("java.util.Date");
            Class.forName("java.util.ResourceBundle");
        } catch (Throwable t) {
            // Nope.
        }

        inst.addTransformer(new EbbTransformer(), inst.isRetransformClassesSupported());

    }
}