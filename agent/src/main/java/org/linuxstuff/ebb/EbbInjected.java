package org.linuxstuff.ebb;

import javax.validation.constraints.NotNull;

public class EbbInjected {

    public static void doRun(@NotNull Runnable runnable) {
        try {
            Object boom;
            boom = runnable.getClass().getDeclaredField("jamesIsAwe$ome").get(runnable);
            System.out.println("boom = " + boom);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

    }
}
