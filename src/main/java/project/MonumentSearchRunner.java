package project;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Monument group search via JNI ({@link MonumentFinderBridge}).
 */
public class MonumentSearchRunner {

    private static final long PROGRESS_POLL_MS = 100L;

    public record SearchParams(
            long seed,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int mc,
            int threadCount,
            boolean pauseOnPairFound) {
    }

    public record ProgressInfo(long processed, long total, double percentage, long elapsedMs, long remainingMs) {
    }

    private final int mc;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile long runStartTimeMs = 0;
    private final AtomicLong totalPausedMs = new AtomicLong();
    private volatile long pauseStartMs = 0;
    private final List<String> results = new ArrayList<>();
    private final Set<String> emittedResults = new HashSet<>();
    private volatile Thread searchThread;

    public MonumentSearchRunner(int mc) {
        this.mc = mc;
    }

    public int getMc() {
        return mc;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public List<String> getResults() {
        synchronized (results) {
            return new ArrayList<>(results);
        }
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartMs = System.currentTimeMillis();
            try {
                MonumentFinderBridge.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    public void resume() {
        if (isPaused) {
            if (pauseStartMs > 0) {
                totalPausedMs.addAndGet(System.currentTimeMillis() - pauseStartMs);
                pauseStartMs = 0;
            }
            isPaused = false;
            try {
                MonumentFinderBridge.resume();
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        isRunning = false;
        isPaused = false;
        try {
            MonumentFinderBridge.stop();
        } catch (Throwable ignored) {
        }
    }

    private long getElapsedMs() {
        long base = System.currentTimeMillis() - runStartTimeMs;
        long paused = totalPausedMs.get();
        if (pauseStartMs > 0) {
            paused += System.currentTimeMillis() - pauseStartMs;
        }
        return Math.max(0, base - paused);
    }

    public void startSearch(
            long seed,
            int threadCount,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback,
            boolean pauseOnPairFound,
            boolean biomeCheck,
            int pairLevel,
            int minGroupArea,
            int searchModes,
            int filterMode,
            PairResultFormatter resultFormatter,
            String nativeErrorMessage) {
        if (isRunning && isPaused && threadCount > 0) {
            restartWithNewThreadCount(seed, threadCount, minX, maxX, minZ, maxZ, progressCallback,
                    resultCallback, pauseOnPairFound, biomeCheck, pairLevel, minGroupArea, searchModes, filterMode,
                    resultFormatter, nativeErrorMessage);
            return;
        }
        if (isRunning) {
            return;
        }

        Thread t = new Thread(
                () -> runSearch(seed, threadCount, minX, maxX, minZ, maxZ, progressCallback, resultCallback,
                        pauseOnPairFound, biomeCheck, pairLevel, minGroupArea, searchModes, filterMode, resultFormatter, nativeErrorMessage),
                "monument-search");
        t.setDaemon(true);
        searchThread = t;
        t.start();
    }

    /**
     * Runs the search on the calling thread (for batch/list search workers).
     */
    public void runSearchBlocking(
            long seed,
            int threadCount,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback,
            boolean pauseOnPairFound,
            boolean biomeCheck,
            int pairLevel,
            int minGroupArea,
            int searchModes,
            int filterMode,
            PairResultFormatter resultFormatter,
            String nativeErrorMessage) {
        if (isRunning) {
            return;
        }
        runSearch(seed, threadCount, minX, maxX, minZ, maxZ, progressCallback, resultCallback,
                pauseOnPairFound, biomeCheck, pairLevel, minGroupArea, searchModes, filterMode, resultFormatter, nativeErrorMessage);
    }

    /**
     * Blocks until the async search thread from {@link #startSearch} finishes.
     */
    public void awaitSearchFinished() throws InterruptedException {
        Thread t = searchThread;
        if (t != null) {
            t.join();
        }
    }

    private void restartWithNewThreadCount(
            long seed,
            int threadCount,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback,
            boolean pauseOnPairFound,
            boolean biomeCheck,
            int pairLevel,
            int minGroupArea,
            int searchModes,
            int filterMode,
            PairResultFormatter resultFormatter,
            String nativeErrorMessage) {
        stop();
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        isRunning = false;
        isPaused = false;
        startSearch(seed, threadCount, minX, maxX, minZ, maxZ, progressCallback, resultCallback,
                pauseOnPairFound, biomeCheck, pairLevel, minGroupArea, searchModes, filterMode, resultFormatter, nativeErrorMessage);
    }

    private void runSearch(
            long seed,
            int threadCount,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Consumer<ProgressInfo> progressCallback,
            Consumer<String> resultCallback,
            boolean pauseOnPairFound,
            boolean biomeCheck,
            int pairLevel,
            int minGroupArea,
            int searchModes,
            int filterMode,
            PairResultFormatter resultFormatter,
            String nativeErrorMessage) {
        isRunning = true;
        isPaused = false;
        totalPausedMs.set(0);
        pauseStartMs = 0;
        runStartTimeMs = System.currentTimeMillis();

        synchronized (results) {
            results.clear();
        }
        synchronized (emittedResults) {
            emittedResults.clear();
        }

        try {
            MonumentFinderBridge.resetSearchState();
        } catch (Throwable ignored) {
        }

        int threads = Math.max(1, Math.min(threadCount, Runtime.getRuntime().availableProcessors()));
        long totalTasks = (long) (maxX - minX) * (maxZ - minZ);
        AtomicLong lastProcessed = new AtomicLong(0);
        AtomicLong lastTotal = new AtomicLong(Math.max(1, totalTasks));

        try (CloseableScheduler progressScheduler = new CloseableScheduler(
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread pt = new Thread(task, "monument-progress");
                    pt.setDaemon(true);
                    return pt;
                }))) {

            if (progressCallback != null && totalTasks > 0) {
                progressCallback.accept(new ProgressInfo(0, totalTasks, 0, getElapsedMs(), 0));
            }

            progressScheduler.executor.scheduleAtFixedRate(() -> {
                if (!isRunning) {
                    return;
                }
                try {
                    pollIncrementalResults(resultCallback, resultFormatter);

                    if (progressCallback != null) {
                        long[] arr = MonumentFinderBridge.getSearchProgress();
                        long processed;
                        long total;
                        if (arr != null && arr.length >= 2) {
                            processed = arr[0];
                            total = Math.max(arr[1], 1L);
                            lastProcessed.set(processed);
                            lastTotal.set(total);
                        } else {
                            processed = lastProcessed.get();
                            total = lastTotal.get();
                        }
                        if (total > 0 && processed >= total) {
                            processed = total - 1;
                        }
                        long elapsed = getElapsedMs();
                        long remaining = 0;
                        if (!isPaused && total > processed && processed > 0) {
                            remaining = elapsed * (total - processed) / processed;
                        }
                        double pct = total > 0 ? processed * 100.0 / total : 0;
                        progressCallback.accept(new ProgressInfo(processed, total, pct, elapsed, remaining));
                    }
                } catch (Throwable ignored) {
                }
            }, 0, PROGRESS_POLL_MS, TimeUnit.MILLISECONDS);

            try {
                int[] raw = MonumentFinderBridge.monumentSearch(
                        seed, minX, maxX, minZ, maxZ, mc, threads, pauseOnPairFound, pairLevel,
                        biomeCheck, minGroupArea, searchModes, filterMode);
                pollIncrementalResults(resultCallback, resultFormatter);
                if (raw != null) {
                    emitRawResults(raw, resultCallback, resultFormatter);
                }
            } catch (UnsatisfiedLinkError e) {
                String msg = (nativeErrorMessage != null ? nativeErrorMessage : "Native library not loaded")
                        + " " + e.getMessage();
                if (resultCallback != null) {
                    resultCallback.accept(msg);
                }
                synchronized (results) {
                    results.add(msg);
                }
            }
        } finally {
            isRunning = false;
            if (pauseStartMs > 0) {
                totalPausedMs.addAndGet(System.currentTimeMillis() - pauseStartMs);
                pauseStartMs = 0;
            }
            isPaused = false;
            searchThread = null;
            if (progressCallback != null) {
                long p = lastProcessed.get();
                long t = lastTotal.get();
                if (p < t) {
                    p = t;
                }
                progressCallback.accept(new ProgressInfo(p, t, 100.0, getElapsedMs(), 0));
            }
        }
    }

    private void pollIncrementalResults(Consumer<String> resultCallback, PairResultFormatter resultFormatter) {
        try {
            int[] raw = MonumentFinderBridge.getNowResult();
            if (raw != null) {
                emitRawResults(raw, resultCallback, resultFormatter);
            }
        } catch (Throwable ignored) {
        }
    }

    private void emitRawResults(int[] raw, Consumer<String> resultCallback, PairResultFormatter resultFormatter) {
        if (resultFormatter == null) {
            return;
        }
        for (int i = 0; i + MonumentFinderBridge.PAIR_STRIDE <= raw.length; i += MonumentFinderBridge.PAIR_STRIDE) {
            int x = raw[i];
            int z = raw[i + 1];
            int x2 = raw[i + 2];
            int z2 = raw[i + 3];
            int sx = raw[i + 4];
            int sz = raw[i + 5];
            int dx = raw[i + 6];
            int dz = raw[i + 7];
            int count = raw[i + 8];
            int area = raw[i + 9];
            int singleArea = raw[i + 10];
            int areaMax = raw[i + 11];
            int areaMaxX = raw[i + 12];
            int areaMaxZ = raw[i + 13];
            boolean dual = raw[i + 14] != 0;
            String line = resultFormatter.format(x, z, x2, z2, sx, sz, count, area, singleArea,
                    areaMax, areaMaxX, areaMaxZ, dx, dz, dual);
            synchronized (emittedResults) {
                if (!emittedResults.add(line)) {
                    continue;
                }
            }
            synchronized (results) {
                results.add(line);
            }
            if (resultCallback != null) {
                resultCallback.accept(line);
            }
        }
    }

    private record CloseableScheduler(ScheduledExecutorService executor) implements AutoCloseable {

        @Override
            public void close() {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
}
