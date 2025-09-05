package pr1;

import java.util.*;
import java.util.concurrent.*;

public class FileProcessingSystem {
    enum FileType { XML, JSON, XLS }

    static final class FileTask {
        final int id; final FileType type; final int size;
        FileTask(int id, FileType type, int size) { this.id=id; this.type=type; this.size=size; }
        boolean poison() { return size < 0; }
        static FileTask poison(FileType t) { return new FileTask(-1, t, -1); }
        static FileTask globalPoison() { return new FileTask(-1, FileType.XML, -1); }
        @Override public String toString() { return "id=" + id + " " + type + " size=" + size; }
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<FileTask> incoming = new ArrayBlockingQueue<>(5);
        Map<FileType, BlockingQueue<FileTask>> perType = new EnumMap<>(FileType.class);
        for (FileType t : FileType.values()) perType.put(t, new LinkedBlockingQueue<>());

        int total = 30;
        Thread generator = new Thread(() -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 1; i <= total; i++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(rnd.nextInt(100, 1001));
                    FileType t = FileType.values()[rnd.nextInt(FileType.values().length)];
                    int size = rnd.nextInt(10, 101);
                    FileTask f = new FileTask(i, t, size);
                    incoming.put(f);
                    System.out.println("Сгенерирован: " + f);
                } catch (InterruptedException ignored) { break; }
            }
            try { incoming.put(FileTask.globalPoison()); } catch (InterruptedException ignored) {}
        });

        Thread dispatcher = new Thread(() -> {
            try {
                while (true) {
                    FileTask f = incoming.take();
                    if (f.poison()) break;
                    perType.get(f.type).put(f);
                    System.out.println("Направлен: " + f);
                }
            } catch (InterruptedException ignored) {}
            finally {
                for (FileType t : FileType.values()) {
                    try { perType.get(t).put(FileTask.poison(t)); } catch (InterruptedException ignored) {}
                }
            }
        });

        List<Thread> processors = new ArrayList<>();
        for (FileType t : FileType.values()) {
            BlockingQueue<FileTask> q = perType.get(t);
            Thread p = new Thread(() -> {
                try {
                    while (true) {
                        FileTask f = q.take();
                        if (f.poison()) break;
                        TimeUnit.MILLISECONDS.sleep((long) f.size * 7);
                        System.out.println("Обработан " + t + ": id=" + f.id + " время=" + (f.size * 7) + "мс");
                    }
                } catch (InterruptedException ignored) {}
            });
            processors.add(p);
        }

        generator.start();
        dispatcher.start();
        for (Thread p : processors) p.start();

        generator.join();
        dispatcher.join();
        for (Thread p : processors) p.join();
        System.out.println("Готово");
    }
}
