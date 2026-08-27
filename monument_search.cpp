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

/* 搜索上下文不保存任何全局神殿列表：配对/成组判定全部在 64x64 分块窗口内
 * 局部完成。全局哈希表在大范围搜索（数亿区域）时会膨胀到上亿条目，是
 * OOM 与进度卡顿的根源，故一律去掉 */
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

/* Two monuments can only share one spawning area if their boxes both reach a
 * common 128-circle, bounding the centre distance well below this limit
 * (2 * (128 spawn radius + 58 monument size), generous on purpose). */
constexpr int GROUP_LINK_DIST_SQ = 372 * 372;
constexpr int MONUMENT_BOX = 58;

/* 过滤三/四连的最小可用面积：一个二连最多覆盖 2 个完整刷怪箱 */
constexpr int MIN_GROUP_AREA = 2 * MONUMENT_BOX * MONUMENT_BOX;

/* ==================== geometry-first chunk scan ==================== */

/* 分块大小（区域数）。块内 + 1 圈 halo 的结构位置只靠 RNG 生成（廉价），
 * 只有几何预筛通过的候选才做群系采样/挂机面积计算。
 * 无全局去重的正确性：任何有效组的成员两两中心距 <= 372 < 512（一个区域），
 * 因此整组必然落在"组内字典序最小的搜索范围内成员"所在分块的窗口里；只在
 * 该成员为块内区域时输出一次，既不重复也不遗漏，跨搜索边界的组由 halo
 * 自然覆盖（界外成员最多 372 < 512，必在边界块的 halo 内）。 */
constexpr int CHUNK = 64;

inline bool lexLessPos(int ax, int az, int bx, int bz)
{
    return ax != bx ? ax < bx : az < bz;
}

/* 组邻接：中心距 <= 372（RNG 结构位置恒为 16 对齐，无需再查对齐） */
inline bool groupLinked(const MonumentBlockPos &a, const MonumentBlockPos &b)
{
    long long adx = a.x > b.x ? (long long) a.x - b.x : (long long) b.x - a.x;
    long long adz = a.z > b.z ? (long long) a.z - b.z : (long long) b.z - a.z;
    return adx * adx + adz * adz <= GROUP_LINK_DIST_SQ;
}

void emitGroup(SearchContext &ctx, const MonumentBlockPos *pts, int nn)
{
    /* 结果队列已满（几何-only 大范围搜索会产出天文数字的候选）时跳过
     * 所有昂贵计算 */
    if (ctx.out->full())
        return;

    int afkX = 0;
    int afkZ = 0;
    int groupArea = monument_afk_coverage(pts, nn, MONUMENT_BOX, MONUMENT_BOX, &afkX, &afkZ);

    /* 三/四连面积过滤 */
    if (groupArea <= MIN_GROUP_AREA)
        return;

    /* 贡献过滤：去掉任一成员都必须使面积严格变小，否则该成员对最佳挂机点
     * 贡献为 0，整个组实质上是更小的组（伪三/四连），不输出 */
    for (int skip = 0; skip < nn; skip++) {
        MonumentBlockPos sub[3];
        int m = 0;
        for (int i = 0; i < nn; i++) {
            if (i != skip)
                sub[m++] = pts[i];
        }
        if (monument_afk_coverage(sub, nn - 1, MONUMENT_BOX, MONUMENT_BOX, nullptr,
                                  nullptr) >= groupArea)
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
    /* 可行性备忘：0=未采样 1=可行 2=不可行（biomeCheck=false 时恒可行） */
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

    /* 块内 + halo 的结构位置（RNG only，无群系采样）；halo 允许越出搜索
     * 边界，使跨搜索边缘的组也能被整组看到 */
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
                /* ---- 对：c 为块内区域，只与"搜索范围外的邻居"或"字典序
                 * 大于 c 的范围内邻居"配对时输出，保证全局恰好一次 ---- */
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

            /* ---- 三/四连：c 必须是组内字典序最小的搜索范围内成员 ---- */
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
            } else { /* level == 4 */
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

    /* 区域不物化成列表：按 64x64 分块，块间按距搜索中心排序（近似就近出结果） */
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
                        progress->current = (int) std::min(done, totalTasks);
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
