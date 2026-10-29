#ifndef MONUMENT_SEARCH_CORE_H
#define MONUMENT_SEARCH_CORE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** cubiomes Pos redeclared to avoid pulling cubiomes headers into every consumer. */
typedef struct MonumentBlockPos {
    int x;
    int z;
} MonumentBlockPos;

/** Opaque cubiomes Generator wrapper (defined only in .c). */
typedef struct MonumentGenerator MonumentGenerator;

MonumentGenerator *monument_generator_alloc(void);
void monument_generator_free(MonumentGenerator *g);

void monument_init_generator(MonumentGenerator *g, int mc, uint64_t seed);

/** Deep-ocean viable monument at region; @return 1 and outputs block position. */
int monument_try_region(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Same as monument_try_region but with an already-initialized generator. */
int monument_try_region_g(
    MonumentGenerator *g,
    int mc,
    uint64_t seed,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Structure-region RNG only (no biome sampling) - cheap geometric prefilter. */
int monument_region_pos(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Monument near block (targetX,targetZ) within tolerance; @return 1 and outputs position. */
int monument_exists_at_block(
    uint64_t seed,
    int mc,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ);

int monument_exists_at_block_g(
    MonumentGenerator *g,
    int mc,
    uint64_t seed,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ);

/** Ocean type at pair center: 0=others, 1=cold, 2=frozen */
int monument_classify_ocean_type(
    int mc,
    uint64_t seed,
    int centerX,
    int centerZ);

int monument_classify_ocean_type_g(
    MonumentGenerator *g,
    int mc,
    int centerX,
    int centerZ);

/** Best AFK coverage for n (1..4) monuments: 128-block spawning circle, 58x58 box anchored at [pos-29, pos+28] per monument (in-game verified; same coverage model as cubiomes getOptimalAfk, ties averaged like quadbase.c); outputs the optimal AFK point if requested. @return covered spawning blocks. */
int monument_afk_coverage(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX,
    int *outZ);

/** Optimal TWO AFK points (dual dummies) for n (2..4) monuments: a spawning block counts when
 *  within 128 blocks of either point (multiset area semantics as above), subject to the
 *  farm-footprint constraint that the counted area fits max-min <= 256 per axis (a single
 *  128-disk spans exactly 256, so only dual pairs sprawled wider than one disk are excluded).
 *  Alternating exact maximization: each half-step picks the globally best span-valid partner
 *  via the union identity union(a,b) = cov(a) + |disk(b)\disk(a)| (field argmax over the
 *  provably span-safe rectangle plus exactly validated cells outside it), started from the
 *  single-point optimum and from every non-trivial bipartition (duplicate starts skipped).
 *  Also outputs the optimal SINGLE-dummy area and point (a single disk is always span-valid).
 *  Heuristic: each half-step is exact, the joint optimum is not proven globally.
 *  @return area of the returned pair; never below the single-dummy optimum (when no better pair
 *          exists the single optimum is returned with both points coincident). */
int monument_afk_coverage_dual(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX1,
    int *outZ1,
    int *outX2,
    int *outZ2,
    int *outSingleArea,
    int *outSingleX,
    int *outSingleZ);

/** Same optimum as monument_afk_coverage_dual but stops as soon as an area >= target is reached
 *  (used by the group contribution filter, which only needs the comparison).
 *  @return 1 when the optimum is >= target (possibly an early-exit lower bound, never below the
 *          single-dummy optimum), 0 otherwise. */
int monument_afk_coverage_dual_reaches(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int target);

/** Area ceiling for the group when the number of AFK dummies is unbounded but the same farm
 *  footprint constraint applies (counted blocks must fit max-min <= 256 per axis, i.e. inside a
 *  257x257 block window). Returns the largest number of box blocks any such window contains with
 *  multiset semantics, so area_single <= area_dual <= monument_group_max_area <= n*ax*az.
 *  Also outputs the centre of mass of the blocks counted in the winning window (the overworld
 *  point a single gate/portal for that footprint would link to). Reference value only, not
 *  reachable with one/two dummies. Returns 0 on invalid input or allocation failure. */
int monument_group_max_area(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX,
    int *outZ);

/** Build the pairwise joint-coverage upper-bound table once (call before 3/4 searches). */
void monument_ensure_pair_upper_bounds(void);

/** Exact max joint coverage of two spawning boxes at (dx,dz) block distance; dx,dz must be multiples of 16, <= 336. Safe upper bound 2*58*58 until the table is built. */
int monument_pair_upper_bound(int dxBlocks, int dzBlocks);

#ifdef __cplusplus
}
#endif

#endif
