#ifndef MONUMENT_SEARCH_H
#define MONUMENT_SEARCH_H

#include <atomic>
#include <cstdint>

struct Progress {
    // region scan progress; 64-bit: full-world searches exceed 2^31 regions (int would saturate at 100% and wrap on the final assignment)
    std::atomic<long long> current{0};
    std::atomic<long long> total{0};
    std::atomic_int status{0};
    std::atomic_bool try_pause{false};
    std::atomic_bool try_stop{false};
};

struct PairHit {
    // output point: pair midpoint for count=2, best AFK point for 3/4 (dual mode: first dummy)
    int x = 0;
    int z = 0;
    // dual mode: second dummy point (equals x/z otherwise)
    int x2 = 0;
    int z2 = 0;
    // optimal SINGLE-dummy point (always computed; == x/z in single mode)
    int singleX = 0;
    int singleZ = 0;
    // count=2: inter-monument |dx|/|dz|; count=3/4: group bounding box width/height
    int dx = 0;
    int dz = 0;
    // monuments in the group (2/3/4)
    int count = 2;
    // usable spawning area (blocks) from the output point(s): dual mode = two-point union
    // under the max-min<=256 span constraint; single mode = single-point optimum
    int area = 0;
    // optimal single-dummy usable area (max-min<=256 holds for one disk automatically)
    int singleArea = 0;
    // ceiling for this group with an unbounded number of dummies but the same max-min<=256
    // footprint: the most box blocks any 257x257 window holds (>= area, reference only)
    int areaMax = 0;
    // overworld centre of the winning 257x257 footprint (portal / gate location, world coords)
    int areaMaxX = 0;
    int areaMaxZ = 0;
    // true when x2/z2 carry a distinct second dummy point (dual mode)
    bool dual = false;

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
    // area threshold: the reported area must strictly exceed it; 0 = no area filter
    int minGroupArea = 2 * 58 * 58;
    // search modes for 3/4 groups, bitmask: which areas to compute (and therefore show):
    //   bit 0 (1) single dummy : single-point optimum
    //   bit 1 (2) dual dummy   : two-point union (max-min<=256 span constraint)
    //   bit 2 (4) single gate  : unlimited-dummy ceiling inside one 257x257 footprint
    int searchModes = 1;
    // area the threshold is compared against: 0 = single-dummy area, 1 = dual-dummy area,
    // 2 = single-gate ceiling. The other computed areas are still filled into the result row.
    int filterMode = 0;
};

template<typename T>
class ThreadSafeResults;

void runMonumentSearch(
    const MonumentSearchConfig &cfg,
    Progress *progress,
    ThreadSafeResults<PairHit> &out,
    int numThreads);

#endif
