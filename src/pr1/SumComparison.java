package pr1;

import java.util.*;
import java.util.concurrent.*;

public class SumComparison {
    private static final int size = 10000;
    private static final int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int delayMs = 1;

    private static final int[] array = new int[size];
    private static final ForkJoinPool fjp = new ForkJoinPool(threads);
    private static final int fjThreshold;

    static {
        Random r = new Random(42);
        for (int i = 0; i < size; i++) array[i] = r.nextInt(1000000);
        fjThreshold = (array.length + threads - 1) / threads;
        fjp.invoke(new RecursiveAction() { @Override protected void compute() {} });
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Размер массива: " + size);
        System.out.println("Потоков в пуле: " + threads);
        System.out.println("Задержка: " + delayMs + " мс на каждое сложение");
        System.out.println("FJ threshold: " + fjThreshold + " (≈ " +
                (int) Math.ceil((double) size / fjThreshold) + " листовых задач)");
        System.out.println("FJ ManagedBlocker: OFF\n");

        Result<Long> r1 = measure(() -> seqSum(array));
        System.out.printf("Последовательно: сумма=%d, время=%d мс, память=%d KB%n", r1.value, r1.ms, r1.kb);

        Result<Long> r2 = measure(() -> parSum(array, threads));
        System.out.printf("Future/Executor: сумма=%d, время=%d мс, память=%d KB%n", r2.value, r2.ms, r2.kb);

        Result<Long> r3 = measure(() -> {
            SumAction root = new SumAction(array, 0, array.length, fjThreshold);
            fjp.invoke(root);
            return root.result;
        });
        System.out.printf("ForkJoin: сумма=%d, время=%d мс, память=%d KB%n", r3.value, r3.ms, r3.kb);
    }

    private static long seqSum(int[] a) throws InterruptedException {
        long s = 0;
        for (int x : a) { Thread.sleep(delayMs); s += x; }
        return s;
    }

    private static long parSum(int[] a, int nThreads) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(nThreads);
        try {
            List<Future<Long>> fs = new ArrayList<>();
            int chunk = (a.length + nThreads - 1) / nThreads;
            for (int i = 0; i < nThreads; i++) {
                int s = i * chunk, e = Math.min(a.length, s + chunk);
                if (s >= e) break;
                final int from = s, to = e;
                fs.add(ex.submit(() -> {
                    long sum = 0;
                    for (int j = from; j < to; j++) { Thread.sleep(delayMs); sum += a[j]; }
                    return sum;
                }));
            }
            long total = 0;
            for (Future<Long> f : fs) total += f.get();
            return total;
        } finally {
            ex.shutdown();
        }
    }

    static class SumAction extends RecursiveAction {
        private final int[] a;
        private final int l, r;
        private final int threshold;
        volatile long result;

        SumAction(int[] a, int l, int r, int threshold) {
            this.a = a; this.l = l; this.r = r; this.threshold = threshold;
        }

        @Override protected void compute() {
            int len = r - l;
            if (len <= threshold) {
                long s = 0;
                for (int i = l; i < r; i++) {
                    pause();
                    s += a[i];
                }
                result = s;
                return;
            }
            int m = l + (len >>> 1);
            SumAction left = new SumAction(a, l, m, threshold);
            SumAction right = new SumAction(a, m, r, threshold);
            right.fork();
            left.compute();
            right.join();
            result = left.result + right.result;
        }

        private static void pause() {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static <T> Result<T> measure(Callable<T> job) throws Exception {
        System.gc();
        long memBefore = used();
        long t0 = System.nanoTime();
        T v = job.call();
        long ms = (System.nanoTime() - t0) / 1000000;
        System.gc();
        long memAfter = used();
        long kb = Math.max(0, (memAfter - memBefore) / 1024);
        return new Result<>(v, ms, kb);
    }

    private static long used() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static class Result<T> {
        final T value; final long ms; final long kb;
        Result(T value, long ms, long kb) { this.value = value; this.ms = ms; this.kb = kb; }
    }
}
