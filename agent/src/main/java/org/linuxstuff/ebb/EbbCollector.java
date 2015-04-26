package org.linuxstuff.ebb;

import java.util.HashSet;
import java.util.Set;

public class EbbCollector {
    private static final Set<String> runnables = new HashSet<String>();

    private static final Set<String> executors = new HashSet<String>();


    public static void collect(String className, String[] interfaces) {
        if ("java/lang/Runnable".equals(className)) {
            runnables.add(className);
            return;
        }

        if ("java/util/concurrent/Executor".equals(className)) {
            executors.add(className);
        }

    }

}
