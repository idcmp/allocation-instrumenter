import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        System.out.println("Hello World!");

        ExecutorService pool1 = Executors.newFixedThreadPool(20);
        ExecutorService pool2 = Executors.newFixedThreadPool(20);

        pool1.submit(new Tada(pool2));

        Thread.sleep(5000);
        pool2.shutdown();
        pool1.shutdown();
    }
}
