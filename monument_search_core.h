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

/** Best AFK coverage for n (1..4) monuments: 128-block spawning circle, 58x58 box each (getOptimalAfk model, ties averaged like quadbase.c); outputs the optimal AFK point if requested. @return covered spawning blocks. */
int monument_afk_coverage(
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
