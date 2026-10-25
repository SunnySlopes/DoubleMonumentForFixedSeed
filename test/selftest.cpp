/*
 * Standalone selftest for the double monument kernel.
 *
 * Usage:
 *   double_monument_selftest <seed> <minX> <maxX> <minZ> <maxZ> [threads] [mc]
 *
 * Region coordinates are in 512-block units (same convention as
 * DoubleMonumentConfig).  The output is stable and sorted, which makes it
 * usable for a diff against a pre-refactor build.
 */

#include "monument_search.h"
#include "monument_search_core.h"
#include "Thread.h"

#include "cubiomes/biomes.h"

#include <algorithm>
#include <cinttypes>
#include <cstdio>
#include <cstdlib>
#include <vector>

namespace {

bool hitLess(const DoublePairHit &a, const DoublePairHit &b)
{
    if (a.hangX != b.hangX) return a.hangX < b.hangX;
    if (a.hangZ != b.hangZ) return a.hangZ < b.hangZ;
    if (a.dx != b.dx) return a.dx < b.dx;
    if (a.dz != b.dz) return a.dz < b.dz;
    return a.lowEfficiency < b.lowEfficiency;
}

} // namespace

int main(int argc, char **argv)
{
    if (argc < 6) {
        std::fprintf(stderr,
                     "usage: %s <seed> <minX> <maxX> <minZ> <maxZ> [threads] [mc]\n",
                     argv[0]);
        return 2;
    }

    DoubleMonumentConfig cfg;
    cfg.seed = std::strtoull(argv[1], nullptr, 10);
    cfg.minX = std::atoi(argv[2]);
    cfg.maxX = std::atoi(argv[3]);
    cfg.minZ = std::atoi(argv[4]);
    cfg.maxZ = std::atoi(argv[5]);
    const int threads = argc > 6 ? std::atoi(argv[6]) : 4;
    cfg.mc = argc > 7 ? std::atoi(argv[7]) : MC_26_3;
    cfg.pauseOnPairFound = false;

    DoubleProgress progress;
    DoubleResults<DoublePairHit> results;
    runDoubleMonumentSearch(cfg, &progress, results, threads);

    std::vector<DoublePairHit> hits = results.getAllResults();
    std::sort(hits.begin(), hits.end(), hitLess);

    std::printf("seed=%" PRIu64 " mc=%d region=[%d,%d)x[%d,%d) threads=%d "
                "hits=%zu current=%d total=%d status=%d\n",
                cfg.seed, cfg.mc, cfg.minX, cfg.maxX, cfg.minZ, cfg.maxZ, threads,
                hits.size(), progress.current.load(), progress.total.load(),
                progress.status.load());

    DmMonumentGenerator *gen = dm_monument_generator_alloc();
    if (gen)
        dm_monument_init_generator(gen, cfg.mc, cfg.seed);
    for (const DoublePairHit &hit : hits) {
        const int type = gen ? dm_monument_classify_ocean_type_g(gen, cfg.mc, hit.hangX, hit.hangZ) : -1;
        std::printf("hit %d %d dx=%d dz=%d low=%d type=%d\n",
                    hit.hangX, hit.hangZ, hit.dx, hit.dz,
                    hit.lowEfficiency, type);
    }
    if (gen)
        dm_monument_generator_free(gen);

    return 0;
}
