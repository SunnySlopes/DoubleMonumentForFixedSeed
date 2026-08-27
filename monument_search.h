#ifndef MONUMENT_SEARCH_H
#define MONUMENT_SEARCH_H

#include <atomic>
#include <cstdint>

struct Progress {
    std::atomic_int current{0};
    std::atomic_int total{0};
    std::atomic_int status{0};
    // 3/4-combo progress: combosTotal = pre-screened candidates, combosDone = processed
    std::atomic_int combosDone{0};
    std::atomic_int combosTotal{0};
    std::atomic_bool try_pause{false};
    std::atomic_bool try_stop{false};
};

struct PairHit {
    // output point: pair midpoint for count=2, optimal AFK point for 3/4
    int x = 0;
    int z = 0;
    // count=2: inter-monument |dx|/|dz|; count=3/4: group bounding box width/height
    int dx = 0;
    int dz = 0;
    // monuments in the group (2/3/4)
    int count = 2;
    // usable spawning area (blocks) from the best AFK point, 128-block circle, 58x58 box per monument
    int area = 0;

    bool operator<(const PairHit &o) const noexcept;
};

inline bool PairHit::operator<(const PairHit &o) const noexcept
{
    if (x != o.x)
        return x < o.x;
    return z < o.z;
}

struct MonumentSearchConfig {
    uint64_t seed = 0;
    int minX = 0;
    int maxX = 0;
    int minZ = 0;
    int maxZ = 0;
    int mc = 25;
    bool pauseOnPairFound = false;
    // requested group size: 2 = pairs, 3 = triples, 4 = quadruples
    int pairLevel = 2;
    // false = RNG-only structure positions, skip biome viability sampling
    bool biomeCheck = true;
    // min group area threshold: area must strictly exceed it (default 2*58*58)
    int minGroupArea = 2 * 58 * 58;
};

template<typename T>
class ThreadSafeResults;

void runMonumentSearch(
    const MonumentSearchConfig &cfg,
    Progress *progress,
    ThreadSafeResults<PairHit> &out,
    int numThreads);

#endif
