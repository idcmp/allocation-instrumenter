import com.google.monitoring.runtime.instrumentation.ConstructorCallback;
import com.google.monitoring.runtime.instrumentation.ConstructorInstrumenter;

import java.lang.instrument.UnmodifiableClassException;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class HelloWorld {

    static class Tada implements Runnable {

        private ExecutorService es;

        public Tada(ExecutorService es) {
            this.es = es;
        }

        public void run() {
            es.submit(new Tada2());
        }
    }

    static class Tada2 implements Runnable {

        public void run() {
            System.out.println("It's now: " + new Date());
        }
    }

    public static void main(String[] args) throws InterruptedException {

        try {
            ConstructorInstrumenter.instrumentClass(ThreadPoolExecutor.class, new ConstructorCallback<Executor>() {
                public void sample(Executor newObj) {
                    System.out.println("newObj = " + newObj);
                }
            });
        } catch (UnmodifiableClassException e) {
            e.printStackTrace();
        }
        System.out.println("Hello World!");

        ExecutorService pool1 = Executors.newFixedThreadPool(20);
        ExecutorService pool2 = Executors.newFixedThreadPool(20);

        pool1.submit(new Tada(pool2));

        Thread.sleep(5000);
        pool2.shutdown();
        pool1.shutdown();
    }
}
