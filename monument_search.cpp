#include "monument_search_core.h"

#include "Thread.h"
#include "monument_search.h"

#include <algorithm>
#include <chrono>
#include <climits>
#include <cstdlib>
#include <thread>
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

// No global monument list: grouping is decided inside the chunk window only; global hash tables caused OOM/stalls on huge searches.
struct SearchContext {
    MonumentSearchConfig cfg;
    Progress *progress = nullptr;
    ThreadSafeResults<PairHit> *out = nullptr;

    int centerRegX = 0;
    int centerRegZ = 0;
};

int defaultHwThreads()
{
    unsigned hw = std::thread::hardware_concurrency();
    return hw > 0 ? (int) hw : 1;
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

// Box-sharing centre distance bound: 2*128 + 58*sqrt(2) ~338; largest valid 16-aligned pair ~334 (dx=272,dz=192), so 336 is safe and tight.
constexpr int GROUP_LINK_DIST_SQ = 336 * 336;
constexpr int MONUMENT_BOX = 58;

// ==================== geometry-first chunk scan ====================

// Chunk size in regions. RNG generates chunk+halo positions cheaply; expensive sampling runs only on pre-screened candidates. Dedup: group members are pairwise <= 336 < 512, so the group lies in the window of the chunk holding its lexicographically smallest in-range member; emit once there, border groups covered by halo.
constexpr int CHUNK = 64;

inline bool lexLessPos(int ax, int az, int bx, int bz)
{
    return ax != bx ? ax < bx : az < bz;
}

// Group adjacency: centre distance <= 336 (RNG positions are always 16-aligned)
inline bool groupLinked(const MonumentBlockPos &a, const MonumentBlockPos &b)
{
    long long adx = a.x > b.x ? (long long) a.x - b.x : (long long) b.x - a.x;
    long long adz = a.z > b.z ? (long long) a.z - b.z : (long long) b.z - a.z;
    return adx * adx + adz * adz <= GROUP_LINK_DIST_SQ;
}

void emitGroup(SearchContext &ctx, const MonumentBlockPos *pts, int nn)
{
    // Skip expensive work once the result queue is full (geometry-only searches produce astronomically many candidates)
    if (ctx.out->full())
        return;

    // 3/4-combo counting: +1 candidate on pre-screen; every exit path marks it processed
    const bool counted = nn >= 3 && ctx.progress != nullptr;
    if (counted)
        ctx.progress->combosTotal.fetch_add(1, std::memory_order_relaxed);

    // Early reject: group coverage <= sum_i min_{j!=i} U2(dist_ij); bound <= threshold means even the best AFK point falls short, skip exact computation.
    long long bound = 0;
    for (int i = 0; i < nn; i++) {
        int m = INT_MAX;
        for (int j = 0; j < nn; j++) {
            if (j == i)
                continue;
            int dx = std::abs(pts[i].x - pts[j].x);
            int dz = std::abs(pts[i].z - pts[j].z);
            int ub = monument_pair_upper_bound(dx, dz);
            if (ub < m)
                m = ub;
        }
        bound += m;
    }
    if (bound <= ctx.cfg.minGroupArea) {
        if (counted)
            ctx.progress->combosDone.fetch_add(1, std::memory_order_relaxed);
        return;
    }

    int afkX = 0;
    int afkZ = 0;
    int groupArea = monument_afk_coverage(pts, nn, MONUMENT_BOX, MONUMENT_BOX, &afkX, &afkZ);

    // Area must strictly exceed the threshold (== best-pair area = no gain, i.e. noise)
    if (groupArea <= ctx.cfg.minGroupArea) {
        if (counted)
            ctx.progress->combosDone.fetch_add(1, std::memory_order_relaxed);
        return;
    }

    // Contribution filter: removing any member must strictly shrink the area, otherwise the group is effectively smaller (pseudo 3/4-group)
    bool keep = true;
    for (int skip = 0; skip < nn; skip++) {
        MonumentBlockPos sub[3];
        int m = 0;
        for (int i = 0; i < nn; i++) {
            if (i != skip)
                sub[m++] = pts[i];
        }
        if (monument_afk_coverage(sub, nn - 1, MONUMENT_BOX, MONUMENT_BOX, nullptr,
                                  nullptr) >= groupArea) {
            keep = false;
            break;
        }
    }
    if (!keep) {
        if (counted)
            ctx.progress->combosDone.fetch_add(1, std::memory_order_relaxed);
        return;
    }

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
    if (counted)
        ctx.progress->combosDone.fetch_add(1, std::memory_order_relaxed);
}

void processChunk(
    SearchContext &ctx,
    MonumentGenerator *g,
    int bx,
    int bz,
    int bw,
    int bh,
    long *cellsOut)
{
    const int W = CHUNK + 2;
    struct RegionPos {
        bool ok = false;
        int x = 0;
        int z = 0;
    };
    std::vector<RegionPos> win((size_t) W * W);
    // Viability memo: 0=unsampled 1=viable 2=not (always viable without biome check)
    std::vector<uint8_t> viable((size_t) W * W, 0);

    auto viableAt = [&](int lx, int lz) -> bool {
        uint8_t &v = viable[(size_t) lx * W + lz];
        if (v == 0) {
            const RegionPos &p = win[(size_t) lx * W + lz];
            if (!p.ok) {
                v = 2;
            } else if (!ctx.cfg.biomeCheck) {
                v = 1;
            } else {
                int mx = 0;
                int mz = 0;
                v = monument_try_region_g(g, ctx.cfg.mc, ctx.cfg.seed, bx - 1 + lx,
                                          bz - 1 + lz, &mx, &mz)
                        ? 1
                        : 2;
            }
        }
        return v == 1;
    };

    auto inRange = [&](int lx, int lz) -> bool {
        int regX = bx - 1 + lx;
        int regZ = bz - 1 + lz;
        return regX >= ctx.cfg.minX && regX < ctx.cfg.maxX && regZ >= ctx.cfg.minZ &&
               regZ < ctx.cfg.maxZ;
    };

    // Structure positions for chunk + halo (RNG only); halo may cross the search border so border-straddling groups are seen whole
    for (int lx = 0; lx < W; lx++) {
        for (int lz = 0; lz < W; lz++) {
            RegionPos &dst = win[(size_t) lx * W + lz];
            int px = 0;
            int pz = 0;
            if (monument_region_pos(ctx.cfg.seed, ctx.cfg.mc, bx - 1 + lx, bz - 1 + lz,
                                    &px, &pz)) {
                dst.ok = true;
                dst.x = px;
                dst.z = pz;
            }
        }
    }

    const int level = ctx.cfg.pairLevel;

    for (int lx = 1; lx <= bw; lx++) {
        for (int lz = 1; lz <= bh; lz++) {
            (*cellsOut)++;
            const RegionPos &p = win[(size_t) lx * W + lz];
            if (!p.ok)
                continue;

            if (level == 2) {
                // pair: pair only with out-of-range or lexicographically greater in-range neighbours, so each pair is emitted exactly once
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0)
                            continue;
                        int nx = lx + dx;
                        int nz = lz + dz;
                        const RegionPos &q = win[(size_t) nx * W + nz];
                        if (!q.ok)
                            continue;
                        if (inRange(nx, nz) && !lexLessPos(p.x, p.z, q.x, q.z))
                            continue;
                        PairCheckResult check =
                            checkCanFormPair(MonumentPos{p.x, p.z}, MonumentPos{q.x, q.z});
                        if (!check.canFormPair)
                            continue;
                        if (!viableAt(lx, lz) || !viableAt(nx, nz))
                            continue;
                        if (ctx.out->full())
                            return;

                        MonumentBlockPos pair[2] = {{p.x, p.z}, {q.x, q.z}};
                        PairHit hit;
                        hit.x = (p.x + q.x) / 2;
                        hit.z = (p.z + q.z) / 2;
                        hit.dx = std::abs(q.x - p.x);
                        hit.dz = std::abs(q.z - p.z);
                        hit.count = 2;
                        hit.area = monument_afk_coverage(pair, 2, MONUMENT_BOX,
                                                         MONUMENT_BOX, nullptr, nullptr);
                        ctx.out->addResult(hit);

                        if (ctx.cfg.pauseOnPairFound && !check.isLowEfficiency &&
                            ctx.progress) {
                            ctx.progress->try_pause = true;
                        }
                    }
                }
                continue;
            }

            // 3/4: c must be the lexicographically smallest in-range member
            struct Member {
                MonumentBlockPos pos;
                int lx;
                int lz;
            };
            Member nbrs[8];
            int nn = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0)
                        continue;
                    int nx = lx + dx;
                    int nz = lz + dz;
                    const RegionPos &q = win[(size_t) nx * W + nz];
                    if (!q.ok)
                        continue;
                    if (inRange(nx, nz) && !lexLessPos(p.x, p.z, q.x, q.z))
                        continue;
                    if (!groupLinked(MonumentBlockPos{p.x, p.z}, MonumentBlockPos{q.x, q.z}))
                        continue;
                    nbrs[nn].pos = MonumentBlockPos{q.x, q.z};
                    nbrs[nn].lx = nx;
                    nbrs[nn].lz = nz;
                    nn++;
                }
            }
            if (nn < level - 1)
                continue;

            MonumentBlockPos pts[4];
            pts[0] = MonumentBlockPos{p.x, p.z};

            if (level == 3) {
                for (int i = 0; i < nn; i++) {
                    for (int j = i + 1; j < nn; j++) {
                        if (!groupLinked(nbrs[i].pos, nbrs[j].pos))
                            continue;
                        if (!viableAt(lx, lz) || !viableAt(nbrs[i].lx, nbrs[i].lz) ||
                            !viableAt(nbrs[j].lx, nbrs[j].lz))
                            continue;
                        pts[1] = nbrs[i].pos;
                        pts[2] = nbrs[j].pos;
                        emitGroup(ctx, pts, 3);
                    }
                }
            } else { // level == 4
                for (int i = 0; i < nn; i++) {
                    for (int j = i + 1; j < nn; j++) {
                        if (!groupLinked(nbrs[i].pos, nbrs[j].pos))
                            continue;
                        for (int k = j + 1; k < nn; k++) {
                            if (!groupLinked(nbrs[i].pos, nbrs[k].pos) ||
                                !groupLinked(nbrs[j].pos, nbrs[k].pos))
                                continue;
                            if (!viableAt(lx, lz) || !viableAt(nbrs[i].lx, nbrs[i].lz) ||
                                !viableAt(nbrs[j].lx, nbrs[j].lz) ||
                                !viableAt(nbrs[k].lx, nbrs[k].lz))
                                continue;
                            pts[1] = nbrs[i].pos;
                            pts[2] = nbrs[j].pos;
                            pts[3] = nbrs[k].pos;
                            emitGroup(ctx, pts, 4);
                        }
                    }
                }
            }
        }
    }
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

    // Pairwise joint-coverage upper bound table (built once per process, ~100ms)
    if (cfg.pairLevel >= 3)
        monument_ensure_pair_upper_bounds();

    // Regions are never materialized: process 64x64 chunks sorted by distance to the search centre
    const long width = std::max(0, cfg.maxX - cfg.minX);
    const long height = std::max(0, cfg.maxZ - cfg.minZ);
    const long totalTasks = width * height;

    const int chunksW = (int) ((width + CHUNK - 1) / CHUNK);
    const int chunksH = (int) ((height + CHUNK - 1) / CHUNK);
    std::vector<std::pair<int, int>> chunks;
    chunks.reserve((size_t) chunksW * chunksH);
    for (int cx = 0; cx < chunksW; cx++) {
        for (int cz = 0; cz < chunksH; cz++) {
            chunks.emplace_back(cfg.minX + cx * CHUNK, cfg.minZ + cz * CHUNK);
        }
    }
    std::sort(chunks.begin(), chunks.end(), [&](const auto &a, const auto &b) {
        int dxa = a.first + CHUNK / 2 - ctx.centerRegX;
        int dza = a.second + CHUNK / 2 - ctx.centerRegZ;
        int dxb = b.first + CHUNK / 2 - ctx.centerRegX;
        int dzb = b.second + CHUNK / 2 - ctx.centerRegZ;
        return (dxa * dxa + dza * dza) < (dxb * dxb + dzb * dzb);
    });

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
                    if (idx >= (long) chunks.size())
                        break;

                    const auto &base = chunks[(size_t) idx];
                    int bw = (int) std::min((long) CHUNK, (long) cfg.maxX - base.first);
                    int bh = (int) std::min((long) CHUNK, (long) cfg.maxZ - base.second);
                    long cells = 0;
                    processChunk(ctx, g, base.first, base.second, bw, bh, &cells);

                    if (progress) {
                        long done = completed.fetch_add(cells) + cells;
                        // saturate at INT_MAX to avoid overflow to negative on huge ranges
                        progress->current = (int) std::min(done, (long) INT_MAX);
                    }
                }
                monument_generator_free(g);
            });
        }
    }

    if (progress) {
        progress->current = (int) totalTasks;
        progress->status = 2;
    }
}
