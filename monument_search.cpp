#include "monument_search_core.h"

#include "Thread.h"
#include "monument_search.h"

#include <algorithm>
#include <chrono>
#include <climits>
#include <cmath>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_set>
#include <vector>

namespace {

struct MonumentPos {
    int x = 0;
    int z = 0;
};

struct PairCheckResult {
    bool canFormPair = false;
    bool isLowEfficiency = false;
};

struct SearchContext {
    MonumentSearchConfig cfg;
    Progress *progress = nullptr;
    ThreadSafeResults<PairHit> *out = nullptr;

    int centerRegX = 0;
    int centerRegZ = 0;
    int searchMinX = 0;
    int searchMaxX = 0;
    int searchMinZ = 0;
    int searchMaxZ = 0;

    std::vector<std::pair<int, int>> regions;
    std::mutex monumentsMu;
    std::unordered_set<uint64_t> monumentKeys;
    std::vector<MonumentPos> monuments;
    /* monuments found outside the search range via pair probing (still real,
     * viable monuments — needed so triples/quads can span the search border) */
    std::mutex outerMu;
    std::unordered_set<uint64_t> outerKeys;
    std::vector<MonumentPos> outerMonuments;
    std::mutex pairsMu;
    std::unordered_set<uint64_t> addedPairKeys;
    /* group enumeration runs single-threaded after the region scan */
    std::unordered_set<std::string> addedGroupKeys;
};

int defaultHwThreads()
{
    unsigned hw = std::thread::hardware_concurrency();
    return hw > 0 ? (int) hw : 1;
}

uint64_t posKey(int x, int z)
{
    return ((uint64_t) (uint32_t) x << 32) | (uint32_t) z;
}

uint64_t pairKey(int x1, int z1, int x2, int z2)
{
    int minX = std::min(x1, x2);
    int minZ = std::min(z1, z2);
    int maxX = std::max(x1, x2);
    int maxZ = std::max(z1, z2);
    return (posKey(minX, minZ) << 1) ^ posKey(maxX, maxZ);
}

bool checkProgress(Progress *progress)
{
    if (!progress)
        return true;
    while (progress->try_pause.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (progress->try_stop.load())
            return false;
    }
    return !progress->try_stop.load();
}

PairCheckResult checkCanFormPair(const MonumentPos &pos1, const MonumentPos &pos2)
{
    int dx = std::abs(pos2.x - pos1.x);
    int dz = std::abs(pos2.z - pos1.z);

    if (dx % 16 != 0 || dz % 16 != 0)
        return {false, false};

    if (dx <= 112 && dz <= 112)
        return {true, false};

    int maxDiff = std::max(dx, dz);
    int minDiff = std::min(dx, dz);

    if (maxDiff < 128)
        return {false, false};

    if ((maxDiff == 192 && minDiff == 0) || (maxDiff == 176 && minDiff == 48) ||
        (maxDiff == 160 && minDiff == 80)) {
        return {true, true};
    }

    if (maxDiff == 128)
        return {minDiff <= 112, false};
    if (maxDiff == 144)
        return {minDiff <= 96, false};
    if (maxDiff == 160)
        return {minDiff <= 64, false};
    if (maxDiff == 176)
        return {minDiff <= 32, false};

    return {false, false};
}

bool isInSearchRange(const SearchContext &ctx, const MonumentPos &pos)
{
    return pos.x >= ctx.searchMinX && pos.x < ctx.searchMaxX && pos.z >= ctx.searchMinZ &&
           pos.z < ctx.searchMaxZ;
}

void addPairResult(SearchContext &ctx, const MonumentPos &pos1, const MonumentPos &pos2, bool lowEfficiency)
{
    uint64_t key = pairKey(pos1.x, pos1.z, pos2.x, pos2.z);
    {
        std::lock_guard<std::mutex> lock(ctx.pairsMu);
        if (!ctx.addedPairKeys.insert(key).second)
            return;
    }

    /* in group mode (pairLevel >= 3) pairs are only intermediate bookkeeping;
     * the actual results are the groups enumerated after the scan */
    if (ctx.cfg.pairLevel != 2)
        return;

    PairHit hit;
    hit.x = (pos1.x + pos2.x) / 2;
    hit.z = (pos1.z + pos2.z) / 2;
    hit.dx = std::abs(pos2.x - pos1.x);
    hit.dz = std::abs(pos2.z - pos1.z);
    hit.count = 2;
    MonumentBlockPos pair[2] = {{pos1.x, pos1.z}, {pos2.x, pos2.z}};
    hit.area = monument_afk_coverage(pair, 2, 58, 58, nullptr, nullptr);
    ctx.out->addResult(hit);

    if (ctx.cfg.pauseOnPairFound && !lowEfficiency && ctx.progress) {
        ctx.progress->try_pause = true;
    }
}

void checkPairsWithOutsidePositions(
    SearchContext &ctx,
    MonumentGenerator *g,
    const MonumentPos &inRangePos)
{
    for (int dx = -192; dx <= 192; dx += 16) {
        for (int dz = -192; dz <= 192; dz += 16) {
            if (dx == 0 && dz == 0)
                continue;

            int otherX = inRangePos.x + dx;
            int otherZ = inRangePos.z + dz;

            if (otherX >= ctx.searchMinX && otherX < ctx.searchMaxX && otherZ >= ctx.searchMinZ &&
                otherZ < ctx.searchMaxZ) {
                continue;
            }

            MonumentPos otherPos{otherX, otherZ};
            PairCheckResult check = checkCanFormPair(inRangePos, otherPos);
            if (!check.canFormPair)
                continue;

            int actualX = 0, actualZ = 0;
            if (!monument_exists_at_block_g(
                    g, ctx.cfg.mc, ctx.cfg.seed, otherX, otherZ, &actualX, &actualZ)) {
                continue;
            }

            MonumentPos actual{actualX, actualZ};
            {
                std::lock_guard<std::mutex> lock(ctx.outerMu);
                if (ctx.outerKeys.insert(posKey(actual.x, actual.z)).second)
                    ctx.outerMonuments.push_back(actual);
            }
            addPairResult(ctx, inRangePos, actual, check.isLowEfficiency);
        }
    }
}

void checkPairsIncremental(SearchContext &ctx, MonumentGenerator *g, const MonumentPos &newPos)
{
    if (ctx.cfg.pairLevel >= 3) {
        /* group mode: skip pair judging, only probe outside positions */
        if (isInSearchRange(ctx, newPos))
            checkPairsWithOutsidePositions(ctx, g, newPos);
        return;
    }

    std::vector<MonumentPos> snapshot;
    {
        std::lock_guard<std::mutex> lock(ctx.monumentsMu);
        snapshot.reserve(ctx.monuments.size());
        for (const MonumentPos &p : ctx.monuments) {
            if (p.x == newPos.x && p.z == newPos.z)
                continue;
            snapshot.push_back(p);
        }
    }

    for (const MonumentPos &p : snapshot) {
        PairCheckResult check = checkCanFormPair(newPos, p);
        if (check.canFormPair)
            addPairResult(ctx, newPos, p, check.isLowEfficiency);
    }

    if (isInSearchRange(ctx, newPos))
        checkPairsWithOutsidePositions(ctx, g, newPos);
}

void tryAddMonument(SearchContext &ctx, MonumentGenerator *g, int monumentX, int monumentZ)
{
    MonumentPos pos{monumentX, monumentZ};
    uint64_t key = posKey(monumentX, monumentZ);
    bool isNew = false;
    {
        std::lock_guard<std::mutex> lock(ctx.monumentsMu);
        if (ctx.monumentKeys.insert(key).second) {
            ctx.monuments.push_back(pos);
            isNew = true;
        }
    }
    if (isNew)
        checkPairsIncremental(ctx, g, pos);
}

void processRegion(SearchContext &ctx, MonumentGenerator *g, int regX, int regZ)
{
    if (!checkProgress(ctx.progress))
        return;

    int monumentX = 0;
    int monumentZ = 0;
    if (!monument_try_region_g(g, ctx.cfg.mc, ctx.cfg.seed, regX, regZ, &monumentX, &monumentZ))
        return;

    tryAddMonument(ctx, g, monumentX, monumentZ);
}

/* ==================== group enumeration (triples / quads) ==================== */

/* Two monuments can only share one spawning area if their boxes both reach a
 * common 128-circle, bounding the centre distance well below this limit
 * (2 * (128 spawn radius + 58 monument size), generous on purpose). */
constexpr int GROUP_LINK_DIST_SQ = 372 * 372;
constexpr int MONUMENT_BOX = 58;

void processGroup(
    SearchContext &ctx,
    const std::vector<MonumentPos> &all,
    const std::vector<int> &idx)
{
    std::string key;
    for (int i : idx) {
        key += std::to_string(all[i].x);
        key += ',';
        key += std::to_string(all[i].z);
        key += ';';
    }
    if (!ctx.addedGroupKeys.insert(key).second)
        return;

    const int nn = (int) idx.size();
    MonumentBlockPos pts[4];
    for (int i = 0; i < nn; i++) {
        pts[i].x = all[idx[i]].x;
        pts[i].z = all[idx[i]].z;
    }

    int afkX = 0;
    int afkZ = 0;
    int groupArea = monument_afk_coverage(pts, nn, MONUMENT_BOX, MONUMENT_BOX, &afkX, &afkZ);

    /* marginal-gain filter: a pseudo-group whose usable area is not at least
     * 110% of its best (n-1)-subset is not worth reporting */
    int bestSub = 0;
    for (int skip = 0; skip < nn; skip++) {
        MonumentBlockPos sub[3];
        int m = 0;
        for (int i = 0; i < nn; i++) {
            if (i != skip)
                sub[m++] = pts[i];
        }
        int a = monument_afk_coverage(sub, nn - 1, MONUMENT_BOX, MONUMENT_BOX, nullptr, nullptr);
        if (a > bestSub)
            bestSub = a;
    }
    if ((long long) groupArea * 10 <= (long long) bestSub * 11)
        return;

    PairHit hit;
    hit.x = afkX;
    hit.z = afkZ;
    int minX = pts[0].x, maxX = pts[0].x, minZ = pts[0].z, maxZ = pts[0].z;
    for (int i = 1; i < nn; i++) {
        minX = std::min(minX, pts[i].x);
        maxX = std::max(maxX, pts[i].x);
        minZ = std::min(minZ, pts[i].z);
        maxZ = std::max(maxZ, pts[i].z);
    }
    hit.dx = maxX - minX;
    hit.dz = maxZ - minZ;
    hit.count = nn;
    hit.area = groupArea;
    ctx.out->addResult(hit);
}

void enumerateGroups(SearchContext &ctx)
{
    const int n = ctx.cfg.pairLevel;
    if (n != 3 && n != 4)
        return;

    std::vector<MonumentPos> all;
    {
        std::lock_guard<std::mutex> lockA(ctx.monumentsMu);
        all = ctx.monuments;
        std::lock_guard<std::mutex> lockB(ctx.outerMu);
        all.insert(all.end(), ctx.outerMonuments.begin(), ctx.outerMonuments.end());
    }
    std::sort(all.begin(), all.end(), [](const MonumentPos &a, const MonumentPos &b) {
        return a.x != b.x ? a.x < b.x : a.z < b.z;
    });
    all.erase(std::unique(all.begin(), all.end(), [](const MonumentPos &a, const MonumentPos &b) {
                  return a.x == b.x && a.z == b.z;
              }),
              all.end());

    const int V = (int) all.size();
    if (V < n)
        return;

    /* loose adjacency: members of a shareable group must be close together */
    std::vector<std::vector<char>> linked(V, std::vector<char>(V, 0));
    for (int i = 0; i < V; i++) {
        for (int j = i + 1; j < V; j++) {
            int dx = std::abs(all[j].x - all[i].x);
            int dz = std::abs(all[j].z - all[i].z);
            if (dx % 16 != 0 || dz % 16 != 0)
                continue;
            if (dx * dx + dz * dz > GROUP_LINK_DIST_SQ)
                continue;
            linked[i][j] = 1;
            linked[j][i] = 1;
        }
    }

    std::vector<int> cur;
    cur.reserve(n);
    std::function<void(int)> extend = [&](int start) {
        if ((int) cur.size() == n) {
            processGroup(ctx, all, cur);
            return;
        }
        for (int i = start; i < V; i++) {
            bool ok = true;
            for (int m : cur) {
                if (!linked[m][i]) {
                    ok = false;
                    break;
                }
            }
            if (!ok)
                continue;
            cur.push_back(i);
            extend(i + 1);
            cur.pop_back();
        }
    };
    extend(0);
}

} // namespace

void runMonumentSearch(
    const MonumentSearchConfig &cfg,
    Progress *progress,
    ThreadSafeResults<PairHit> &out,
    int numThreads) {
    SearchContext ctx;
    ctx.cfg = cfg;
    ctx.progress = progress;
    ctx.out = &out;

    ctx.centerRegX = (cfg.minX + cfg.maxX) / 2;
    ctx.centerRegZ = (cfg.minZ + cfg.maxZ) / 2;
    ctx.searchMinX = cfg.minX * 512;
    ctx.searchMaxX = cfg.maxX * 512;
    ctx.searchMinZ = cfg.minZ * 512;
    ctx.searchMaxZ = cfg.maxZ * 512;

    ctx.regions.reserve((size_t) std::max(0, cfg.maxX - cfg.minX) * std::max(0, cfg.maxZ - cfg.minZ));
    for (int x = cfg.minX; x < cfg.maxX; x++) {
        for (int z = cfg.minZ; z < cfg.maxZ; z++) {
            ctx.regions.emplace_back(x, z);
        }
    }

    std::sort(ctx.regions.begin(), ctx.regions.end(), [&](const auto &a, const auto &b) {
        int dxa = a.first - ctx.centerRegX;
        int dza = a.second - ctx.centerRegZ;
        int dxb = b.first - ctx.centerRegX;
        int dzb = b.second - ctx.centerRegZ;
        return (dxa * dxa + dza * dza) < (dxb * dxb + dzb * dzb);
    });

    const long totalTasks = (long) ctx.regions.size();
    if (totalTasks <= 0) {
        if (progress) {
            progress->current = 0;
            progress->total = 0;
            progress->status = 2;
        }
        return;
    }

    int threads = numThreads > 0 ? numThreads : defaultHwThreads();
    if (progress) {
        progress->current = 0;
        progress->total = (int) std::min(totalTasks, (long) INT_MAX);
        progress->status = 1;
        progress->try_pause = false;
        progress->try_stop = false;
    }

    std::atomic<long> nextIndex{0};
    std::atomic<long> completed{0};

    {
        ThreadPool pool((size_t) threads);
        for (int t = 0; t < threads; t++) {
            pool.enqueue([&]() {
                MonumentGenerator *g = monument_generator_alloc();
                if (!g)
                    return;
                monument_init_generator(g, ctx.cfg.mc, ctx.cfg.seed);

                while (true) {
                    if (!checkProgress(progress))
                        break;

                    long idx = nextIndex.fetch_add(1);
                    if (idx >= totalTasks)
                        break;

                    const auto &reg = ctx.regions[(size_t) idx];
                    processRegion(ctx, g, reg.first, reg.second);

                    if (progress) {
                        long done = completed.fetch_add(1) + 1;
                        progress->current = (int) std::min(done, totalTasks);
                    }
                }
                monument_generator_free(g);
            });
        }
    }

    /* group mode: enumerate triples/quads from all monuments found (runs
     * single-threaded; skipped when the search was stopped) */
    if (ctx.cfg.pairLevel >= 3 && (!progress || !progress->try_stop.load())) {
        enumerateGroups(ctx);
    }

    if (progress) {
        progress->current = (int) totalTasks;
        progress->status = 2;
    }
}
