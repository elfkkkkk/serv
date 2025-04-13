package org.example;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    // количество заполняемых символов
    private static final int SYMBOLS = 50;
    // количество потоков
    private static final int THREAD_COUNT = 12;
    // Время завершения.
    private static final Duration DURATION = Duration.ofSeconds(2);

    private static final CountDownLatch CDL = new CountDownLatch(THREAD_COUNT);

    private static final Map<String, ProgressInfo> progressData = new ConcurrentHashMap<>();

    private static class ProgressInfo {
        volatile int step;
        final long startTime;
        Long finalDuration;

        ProgressInfo(long startTime) {
            this.step = 0;
            this.startTime = startTime;
            this.finalDuration = null;
        }
    }

    private static synchronized void updateDisplay() {
        System.out.print("\033[" + (progressData.size()) + "A");

        List<String> names = new ArrayList<>(progressData.keySet());
        names.sort(Comparator.comparingInt(name -> Integer.parseInt(name.replaceAll("\\D", ""))));

        for (String name : names) {
            ProgressInfo info = progressData.get(name);
            int done = info.step;
            int remaining = Math.max(0, SYMBOLS - done);
            String bar = "[" + "=".repeat(done) + "-".repeat(remaining) + "]";

            String line;
            if (info.step >= SYMBOLS) {
                if (info.finalDuration == null) {
                    info.finalDuration = System.nanoTime() - info.startTime;
                }
                double seconds = info.finalDuration / 1_000_000_000.0;
                line = String.format("%-10s %s (%.3fs)", name, bar, seconds);
            } else {
                line = String.format("%-10s %s", name, bar);
            }
            System.out.println(line);
        }
    }

    private static class Worker implements Runnable {
        private final String threadName;
        private final long baseDelayMillis;
        private final Random rng = new Random();

        Worker(String threadName, Duration targetDuration) {
            this.threadName = threadName;
            this.baseDelayMillis = targetDuration.toMillis() / SYMBOLS;
        }

        @Override
        public void run() {
            long start = System.nanoTime();
            progressData.put(threadName, new ProgressInfo(start));

            for (int i = 0; i < SYMBOLS; i++) {
                try {
                    long jitter = rng.nextBoolean() ? rng.nextLong(baseDelayMillis / 2 + 1)
                            : -rng.nextLong(baseDelayMillis / 2 + 1);
                    long delay = Math.max(0, baseDelayMillis + jitter);
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                progressData.get(threadName).step = i + 1;
                updateDisplay();
            }
            CDL.countDown();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting progress race...\n" + "\n".repeat(THREAD_COUNT + 2));
        Thread.sleep(1000);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 1; i <= THREAD_COUNT; i++) {
            executor.submit(new Worker("Thread" + i, DURATION));
        }
        CDL.await();
        executor.shutdown();
        System.out.println("All threads completed!");
    }
}