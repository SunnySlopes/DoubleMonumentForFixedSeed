/*
 * Double ocean monument search core — All-In-One Finder kernel branch.
 *
 * This header is the public C++ interface used by All-In-One Finder's
 * native/double_monument_host.cpp.  The implementation is the original
 * DoubleMonumentForFixedSeed algorithm; only the exported names carry the
 * Double / dm_ prefix so that the library can be linked into the same
 * module as the dripstonecave / multimonument / fortress kernels.
 */

#ifndef AIO_DOUBLE_MONUMENT_SEARCH_H
#define AIO_DOUBLE_MONUMENT_SEARCH_H

#include "Thread.h"
#include "monument_search_core.h"

#include <atomic>
#include <cstdint>

/** Search progress. `try_pause` / `try_stop` are polled by the workers. */
struct DoubleProgress {
    std::atomic_int current{0};
    std::atomic_int total{0};
    std::atomic_int status{0};
    std::atomic_bool try_pause{false};
    std::atomic_bool try_stop{false};
};

/** One valid double-monument pair. `hangX/hangZ` is the midpoint of the pair. */
struct DoublePairHit {
    int hangX = 0;
    int hangZ = 0;
    int dx = 0;
    int dz = 0;
    /** 1 = the pair works but with slightly reduced efficiency. */
    int lowEfficiency = 0;

    bool operator<(const DoublePairHit &o) const noexcept;
};

inline bool DoublePairHit::operator<(const DoublePairHit &o) const noexcept
{
    if (hangX != o.hangX)
        return hangX < o.hangX;
    return hangZ < o.hangZ;
}

/** Search configuration. Region coordinates are in 512-block units. */
struct DoubleMonumentConfig {
    uint64_t seed = 0;
    int minX = 0;   // region min X, inclusive
    int maxX = 0;   // region max X, exclusive
    int minZ = 0;   // region min Z, inclusive
    int maxZ = 0;   // region max Z, exclusive
    int mc = 25;    // cubiomes MC version; AIO passes MC_26_3
    bool pauseOnPairFound = false;
};

/**
 * Runs the search synchronously on the calling thread (workers are spawned
 * internally).  On return all hits have been written to `out`.
 *
 * The result container has no built-in limit: All-In-One Finder collects all
 * hits and applies its own sort/truncate step.
 */
void runDoubleMonumentSearch(
    const DoubleMonumentConfig &cfg,
    DoubleProgress *progress,
    DoubleResults<DoublePairHit> &out,
    int numThreads);

#endif
