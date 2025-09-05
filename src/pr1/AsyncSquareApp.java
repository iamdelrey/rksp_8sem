package pr1;

import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class AsyncSquareApp {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        CompletionService<JobResult> cs = new ExecutorCompletionService<>(pool);
        AtomicBoolean accepting = new AtomicBoolean(true);
        AtomicInteger pending = new AtomicInteger(0);
        AtomicInteger ids = new AtomicInteger(1);

        Thread printer = new Thread(() -> {
            try {
                while (accepting.get() || pending.get() > 0) {
                    Future<JobResult> f = cs.poll(250, TimeUnit.MILLISECONDS);
                    if (f != null) {
                        JobResult r = f.get();
                        System.out.printf("Готово: #%d -> %d^2 = %d (задержка %d с)%n", r.id, r.input, r.square, r.delaySec);
                        pending.decrementAndGet();
                    }
                }
            } catch (InterruptedException | ExecutionException ignored) {}
        });
        printer.start();

        System.out.println("Введите число и жмите Enter (exit для выхода)");
        Scanner sc = new Scanner(System.in);
        while (true) {
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) break;
            if (line.isEmpty()) continue;
            try {
                long value = Long.parseLong(line);
                int id = ids.getAndIncrement();
                pending.incrementAndGet();
                cs.submit(() -> {
                    int d = ThreadLocalRandom.current().nextInt(1, 6);
                    TimeUnit.SECONDS.sleep(d);
                    long sq = value * value;
                    return new JobResult(id, value, sq, d);
                });
                System.out.printf("Принято: #%d (%d)%n", id, value);
            } catch (NumberFormatException e) {
                System.out.println("Не число, попробуйте ещё");
            }
        }
        accepting.set(false);
        pool.shutdown();
        printer.join();
        System.out.println("Завершено");
    }

    record JobResult(int id, long input, long square, int delaySec) {}
}
