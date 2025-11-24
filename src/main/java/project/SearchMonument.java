package project;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.Monument;
import nl.kallestruik.noisesampler.minecraft.NoiseParameterKey;
import nl.kallestruik.noisesampler.minecraft.Xoroshiro128PlusPlusRandom;
import nl.kallestruik.noisesampler.minecraft.noise.LazyDoublePerlinNoiseSampler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SearchMonument {
    
    private final Monument monument;
    private final MCVersion mcVersion;
    private ExecutorService executor;
    private Thread progressThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private final List<String> results = new ArrayList<>();
    
    // 保存当前搜索状态，用于动态调整线程数
    private long currentSeed;
    private int currentMinX, currentMaxX, currentMinZ, currentMaxZ;
    private AtomicLong currentProcessedCount;
    private Consumer<String> currentResultCallback;
    private int currentThreadCount;
    private boolean pauseOnPairFound;
    
    // 存储所有找到的海底神殿位置
    private final List<MonumentPosition> monumentPositions = new ArrayList<>();
    
    // 存储搜索范围，用于按需生成坐标
    private int searchCenterX, searchCenterZ;
    
    // 存储搜索范围边界，用于判断位置是否在范围内
    private int searchMinX, searchMaxX, searchMinZ, searchMaxZ;

    public static class ProgressInfo {
        public final long processed;
        public final long total;
        public final double percentage;
        public final long elapsedMs;
        public final long remainingMs;

        public ProgressInfo(long processed, long total, double percentage, long elapsedMs, long remainingMs) {
            this.processed = processed;
            this.total = total;
            this.percentage = percentage;
            this.elapsedMs = elapsedMs;
            this.remainingMs = remainingMs;
        }
    }
    
    // 海底神殿位置类
    public static class MonumentPosition {
        public final int x;
        public final int z;
        
        public MonumentPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MonumentPosition that = (MonumentPosition) o;
            return x == that.x && z == that.z;
        }
        
        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }
    
    // 二联结果类
    public static class PairResult {
        public final MonumentPosition pos1;
        public final MonumentPosition pos2;
        public final int dx;
        public final int dz;
        public final double centerDistance;
        public final boolean isLowEfficiency;
        
        public PairResult(MonumentPosition pos1, MonumentPosition pos2, boolean isLowEfficiency) {
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.dx = Math.abs(pos2.x - pos1.x);
            this.dz = Math.abs(pos2.z - pos1.z);
            // 计算两个神殿中心点之间的距离
            this.centerDistance = Math.sqrt((double) dx * dx + (double) dz * dz);
            this.isLowEfficiency = isLowEfficiency;
        }
        
        // 计算挂机点坐标（两个神殿的中心点）
        public int getHangX() {
            return (pos1.x + pos2.x) / 2;
        }
        
        public int getHangZ() {
            return (pos1.z + pos2.z) / 2;
        }
        
        @Override
        public String toString() {
            String base = String.format("/tp %d 50 %d (差值: dx=%d, dz=%d)", 
                    getHangX(), getHangZ(), dx, dz);
            if (isLowEfficiency) {
                return base + " - 效率会略微损失";
            }
            return base;
        }
    }

    public SearchMonument(MCVersion mcVersion) {
        this.mcVersion = mcVersion;
        this.monument = new Monument(mcVersion);
    }

    public void startSearch(long seed, int threadCount, int minX, int maxX, int minZ, int maxZ,
                          Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback, boolean pauseOnPairFound) {
        this.pauseOnPairFound = pauseOnPairFound;
        // 如果正在运行且处于暂停状态，且线程数变化，则调整线程数
        if (isRunning && isPaused && threadCount != currentThreadCount) {
            adjustThreadCount(threadCount, resultCallback);
            return;
        }
        
        if (isRunning) {
            return;
        }
        isRunning = true;
        results.clear();
        monumentPositions.clear();


        // 保存当前搜索状态
        currentSeed = seed;
        currentMinX = minX;
        currentMaxX = maxX;
        currentMinZ = minZ;
        currentMaxZ = maxZ;
        currentThreadCount = threadCount;
        currentResultCallback = resultCallback;
        this.pauseOnPairFound = pauseOnPairFound;

        // 计算中心点
        searchCenterX = (minX + maxX) / 2;
        searchCenterZ = (minZ + maxZ) / 2;
        
        // 保存搜索范围边界（region坐标转换为真实坐标）
        // region坐标范围是每512 blocks一个region，但getInRegion返回的是chunk坐标
        // 为了判断位置是否在范围内，我们需要将region坐标转换为block坐标
        // 但实际判断时，我们使用monumentX和monumentZ（已经是block坐标）
        // 所以这里保存的是region坐标对应的block坐标范围
        searchMinX = minX * 512;
        searchMaxX = maxX * 512;
        searchMinZ = minZ * 512;
        searchMaxZ = maxZ * 512;
        
        // 计算总任务数（不预先生成所有坐标，避免内存溢出）
        long totalTasks = (long) (maxX - minX) * (maxZ - minZ);

        executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong processedCount = new AtomicLong(0);
        currentProcessedCount = processedCount;

        // 启动进度监控线程
        long startTime = System.currentTimeMillis();
        AtomicLong pausedTime = new AtomicLong(0);
        AtomicReference<Long> pauseStartTime = new AtomicReference<>(0L);
        progressThread = new Thread(() -> {
            while (isRunning && !executor.isTerminated()) {
                try {
                    Thread.sleep(100);
                    long processed = processedCount.get();
                    double percentage = (double) processed / totalTasks * 100.0;
                    
                    if (isPaused) {
                        pauseStartTime.updateAndGet(start -> start == 0 ? System.currentTimeMillis() : start);
                    } else {
                        Long pauseStart = pauseStartTime.getAndSet(0L);
                        if (pauseStart > 0) {
                            pausedTime.addAndGet(System.currentTimeMillis() - pauseStart);
                        }
                    }
                    
                    long elapsed = System.currentTimeMillis() - startTime - pausedTime.get();
                    long remaining = processed > 0 ? (elapsed * (totalTasks - processed) / processed) : 0;

                    if (progressCallback != null) {
                        progressCallback.accept(new ProgressInfo(processed, totalTasks, percentage, elapsed, remaining));
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            long processed = processedCount.get();
            double percentage = (double) processed / totalTasks * 100.0;
            long elapsed = System.currentTimeMillis() - startTime - pausedTime.get();
            if (progressCallback != null) {
                progressCallback.accept(new ProgressInfo(processed, totalTasks, percentage, elapsed, 0));
            }
        });
        progressThread.setDaemon(true);
        progressThread.start();

        // 将搜索范围分配给各个线程（按X轴分割）
        int totalX = maxX - minX;
        int chunkSize = Math.max(1, totalX / threadCount);
        for (int i = 0; i < threadCount; i++) {
            int startX = minX + i * chunkSize;
            int endX = (i == threadCount - 1) ? maxX : startX + chunkSize;
            executor.execute(new RegionChecker(seed, startX, endX, minZ, maxZ, processedCount, resultCallback));
        }
        executor.shutdown();

        // 等待完成
        new Thread(() -> {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isRunning = false;
                // 在所有位置找到后，检查二联（增量检查已经实时完成，这里不需要额外检查）
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        isPaused = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        if (progressThread != null) {
            progressThread.interrupt();
        }
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }
    
    private void adjustThreadCount(int newThreadCount, Consumer<String> resultCallback) {
        if (newThreadCount < 1) {
            return;
        }
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        
        currentThreadCount = newThreadCount;
        currentResultCallback = resultCallback;
        
        executor = Executors.newFixedThreadPool(newThreadCount);
        
        // 将搜索范围分配给各个线程（按X轴分割）
        int totalX = currentMaxX - currentMinX;
        int chunkSize = Math.max(1, totalX / newThreadCount);
        for (int i = 0; i < newThreadCount; i++) {
            int startX = currentMinX + i * chunkSize;
            int endX = (i == newThreadCount - 1) ? currentMaxX : startX + chunkSize;
            executor.execute(new RegionChecker(currentSeed, startX, endX, currentMinZ, currentMaxZ, 
                    currentProcessedCount, currentResultCallback));
        }
        executor.shutdown();
        
        isPaused = false;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public List<String> getResults() {
        return new ArrayList<>(results);
    }
    
    public MCVersion getMCVersion() {
        return mcVersion;
    }
    
    // 检查两个神殿是否可以构成二联，返回是否匹配以及是否为特殊低效情况
    private PairCheckResult checkCanFormPair(MonumentPosition pos1, MonumentPosition pos2) {
        int dx = Math.abs(pos2.x - pos1.x);
        int dz = Math.abs(pos2.z - pos1.z);
        
        // x和y都可以被16整除
        if (dx % 16 != 0 || dz % 16 != 0) {
            return new PairCheckResult(false, false);
        }
        
        // 如果两个坐标差值都小于等于112，直接判定为有效二联
        if (dx <= 112 && dz <= 112) {
            return new PairCheckResult(true, false);
        }
        
        int maxDiff = Math.max(dx, dz);
        int minDiff = Math.min(dx, dz);

        // 这里我们按照条件列表来判断，允许128的情况
        if (maxDiff < 128) {
            return new PairCheckResult(false, false);
        }
        
        // 检查特殊低效情况：(176, 48), (192, 0), (160, 80)，距离在128-130之间
        if ((maxDiff == 192 && minDiff == 0) ||
            (maxDiff == 176 && minDiff == 48) ||
            (maxDiff == 160 && minDiff == 80)) {
            return new PairCheckResult(true, true); // 匹配，但是低效情况
        }
        
        // 检查是否符合正常条件
        if (maxDiff == 128) {
            return new PairCheckResult(minDiff <= 112, false);
        } else if (maxDiff == 144) {
            return new PairCheckResult(minDiff <= 96, false);
        } else if (maxDiff == 160) {
            return new PairCheckResult(minDiff <= 64, false);
        } else if (maxDiff == 176) {
            return new PairCheckResult(minDiff <= 32, false);
        }
        
        // 如果较大值大于176，不符合条件
        return new PairCheckResult(false, false);
    }
    
    // 检查结果类
    private static class PairCheckResult {
        final boolean canFormPair;
        final boolean isLowEfficiency;
        
        PairCheckResult(boolean canFormPair, boolean isLowEfficiency) {
            this.canFormPair = canFormPair;
            this.isLowEfficiency = isLowEfficiency;
        }
    }
    
    // 检查二联（只检查新添加的位置与其他位置的配对，包括范围外的位置）
    private void checkPairsIncremental(Consumer<String> resultCallback, NoiseCache cache) {
        synchronized (monumentPositions) {
            Set<String> addedPairs = new HashSet<>();
            int size = monumentPositions.size();
            
            // 只检查最后一个位置与其他位置的配对（增量检查）
            if (size > 0) {
                MonumentPosition newPos = monumentPositions.get(size - 1);
                
                // 检查与已找到的位置配对
                for (int i = 0; i < size - 1; i++) {
                    MonumentPosition pos = monumentPositions.get(i);
                    
                    PairCheckResult checkResult = checkCanFormPair(newPos, pos);
                    if (checkResult.canFormPair) {
                        addPairResult(newPos, pos, checkResult.isLowEfficiency, addedPairs, resultCallback);
                    }
                }
                
                // 检查与范围外可能的位置配对
                // 如果当前位置在范围内，检查所有可能形成二联的范围外位置
                if (isInSearchRange(newPos)) {
                    checkPairsWithOutsidePositions(newPos, cache, addedPairs, resultCallback);
                }
            }
        }
    }
    
    // 判断位置是否在搜索范围内
    private boolean isInSearchRange(MonumentPosition pos) {
        return pos.x >= searchMinX && pos.x < searchMaxX && 
               pos.z >= searchMinZ && pos.z < searchMaxZ;
    }
    
    // 检查与范围外可能位置的配对
    private void checkPairsWithOutsidePositions(MonumentPosition inRangePos, NoiseCache cache, 
                                                Set<String> addedPairs, Consumer<String> resultCallback) {
        // 生成所有可能形成二联的偏移量
        // 根据二联条件：dx和dz都能被16整除，且满足特定条件
        // 最大偏移量：maxDiff最大为192，所以偏移量范围是-192到192（步长为16）
        for (int dx = -192; dx <= 192; dx += 16) {
            for (int dz = -192; dz <= 192; dz += 16) {
                if (dx == 0 && dz == 0) continue;
                
                int otherX = inRangePos.x + dx;
                int otherZ = inRangePos.z + dz;
                
                // 如果另一个位置也在范围内，跳过（已经在之前的检查中处理过）
                if (otherX >= searchMinX && otherX < searchMaxX && 
                    otherZ >= searchMinZ && otherZ < searchMaxZ) {
                    continue;
                }
                
                // 检查这两个位置是否能形成二联
                MonumentPosition otherPos = new MonumentPosition(otherX, otherZ);
                PairCheckResult checkResult = checkCanFormPair(inRangePos, otherPos);
                if (checkResult.canFormPair) {
                    // 检查范围外的位置是否真的存在神殿
                    if (checkMonumentExistsAt(otherX, otherZ, cache)) {
                        addPairResult(inRangePos, otherPos, checkResult.isLowEfficiency, addedPairs, resultCallback);
                    }
                }
            }
        }
    }
    
    // 检查指定位置是否存在海底神殿
    private boolean checkMonumentExistsAt(int x, int z, NoiseCache cache) {
        // 计算该位置对应的region坐标
        int regionX = x / 512;
        int regionZ = z / 512;
        
        // 获取该region的神殿位置
        ChunkRand rand = new ChunkRand();
        CPos pos = monument.getInRegion(currentSeed, regionX, regionZ, rand);
        if (pos == null) {
            return false;
        }
        
        int monumentX = 16 * pos.getX();
        int monumentZ = 16 * pos.getZ();
        
        // 检查是否与目标位置匹配（允许一定误差，因为神殿位置可能不完全精确）
        // 海底神殿中心位置可能略有偏差，我们检查是否在合理范围内
        int tolerance = 16; // 允许16格误差
        if (Math.abs(monumentX - x) <= tolerance && Math.abs(monumentZ - z) <= tolerance) {
            // 检查生成条件
            return checkMonumentConditions(cache, monumentX, monumentZ);
        }
        
        return false;
    }
    
    // 添加配对结果
    private void addPairResult(MonumentPosition pos1, MonumentPosition pos2, boolean isLowEfficiency,
                               Set<String> addedPairs, Consumer<String> resultCallback) {
        PairResult pair = new PairResult(pos1, pos2, isLowEfficiency);
        String pairKey = Math.min(pos1.x, pos2.x) + "," + Math.min(pos1.z, pos2.z) + 
                         "-" + Math.max(pos1.x, pos2.x) + "," + Math.max(pos1.z, pos2.z);
        
        if (!addedPairs.contains(pairKey)) {
            addedPairs.add(pairKey);
            String resultStr = pair.toString();
            synchronized (results) {
                results.add(resultStr);
            }
            if (resultCallback != null) {
                resultCallback.accept(resultStr);
            }
            
            // 如果启用了找到二联时暂停，且不是低效情况，立即暂停
            if (pauseOnPairFound && isRunning && !isPaused && !isLowEfficiency) {
                pause();
            }
        }
    }

    class RegionChecker implements Runnable {
        private final long seed;
        private final int startX;
        private final int endX;
        private final int minZ;
        private final int maxZ;
        private final ChunkRand rand;
        private final AtomicLong processedCount;
        private final Consumer<String> resultCallback;

        public RegionChecker(long seed, int startX, int endX, int minZ, int maxZ,
                           AtomicLong processedCount, Consumer<String> resultCallback) {
            this.seed = seed;
            this.startX = startX;
            this.endX = endX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.rand = new ChunkRand();
            this.processedCount = processedCount;
            this.resultCallback = resultCallback;
        }

        @Override
        public void run() {
            // 创建噪声缓存（每个线程复用，提高性能）
            NoiseCache cache = new NoiseCache(seed);
            
            // 生成该线程负责范围内的所有坐标，并按距离排序
            List<int[]> localCoords = new ArrayList<>();
            for (int x = startX; x < endX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    int dx = x - searchCenterX;
                    int dz = z - searchCenterZ;
                    int distanceSq = dx * dx + dz * dz;
                    localCoords.add(new int[]{x, z, distanceSq});
                }
            }
            // 按距离排序（从中心往外）
            localCoords.sort((a, b) -> Integer.compare(a[2], b[2]));
            
            for (int[] coord : localCoords) {
                // 暂停时等待
                while (isPaused && isRunning) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!isRunning) {
                    break;
                }
                
                int regionX = coord[0];
                int regionZ = coord[1];
                
                // 先获取海底神殿在区域中的位置（用于计算真实坐标）
                CPos pos = monument.getInRegion(seed, regionX, regionZ, rand);
                if (pos == null) {
                    processedCount.incrementAndGet();
                    continue;
                }
                
                int monumentX = 16 * pos.getX();
                int monumentZ = 16 * pos.getZ();
                
                // 检查生成条件（使用真实坐标检查生物群系噪声）
                if (checkMonumentConditions(cache, monumentX, monumentZ)) {
                    MonumentPosition monumentPos = new MonumentPosition(monumentX, monumentZ);
                    boolean shouldCheckPairs = false;
                    synchronized (monumentPositions) {
                        // 避免重复添加
                        if (!monumentPositions.contains(monumentPos)) {
                            monumentPositions.add(monumentPos);
                            shouldCheckPairs = true;
                        }
                    }
                    
                    // 如果找到新的神殿位置，立即检查二联（无论是否启用暂停选项，都需要检查以输出结果）
                    if (shouldCheckPairs) {
                        checkPairsIncremental(resultCallback, cache);
                    }
                }
                
                processedCount.incrementAndGet();
            }
        }
    }
    
    // 检查海底神殿的生成条件（使用复用的噪声缓存）
    private boolean checkMonumentConditions(NoiseCache cache, int x, int z) {
        // 检查 CONTINENTALNESS: -0.455 到 -1.05 之间
        double continentalness = cache.continentalness.sample((double) x / 4, 0, (double) z / 4);
        if (continentalness < -1.05 || continentalness > -0.455) {
            return false;
        }
        
        // 检查 TEMPERATURE: 小于 0.55
        double temperature = cache.temperature.sample((double) x / 4, 0, (double) z / 4);
        if (temperature >= 0.55) {
            return false;
        }
        
        return true;
    }
    
    // 噪声缓存类
    private static class NoiseCache {
        final LazyDoublePerlinNoiseSampler continentalness;
        final LazyDoublePerlinNoiseSampler temperature;

        NoiseCache(long worldseed) {
            Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(worldseed);
            var deriver = random.createRandomDeriver();
            continentalness = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.CONTINENTALNESS);
            temperature = LazyDoublePerlinNoiseSampler.createNoiseSampler(deriver, NoiseParameterKey.TEMPERATURE);
        }
    }
}

