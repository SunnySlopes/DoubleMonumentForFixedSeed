#ifndef MONUMENT_SEARCH_CORE_H
#define MONUMENT_SEARCH_CORE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** cubiomes structure position (Pos from finders.h) — redeclared to avoid
 *  pulling cubiomes headers into every consumer. */
typedef struct MonumentBlockPos {
    int x;
    int z;
} MonumentBlockPos;

/** Opaque cubiomes Generator wrapper (defined only in .c). */
typedef struct MonumentGenerator MonumentGenerator;

MonumentGenerator *monument_generator_alloc(void);
void monument_generator_free(MonumentGenerator *g);

void monument_init_generator(MonumentGenerator *g, int mc, uint64_t seed);

/** @return 1 if monument exists at region and passes deep-ocean viability */
int monument_try_region(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Uses an already-initialized generator (no setupGenerator per call). */
int monument_try_region_g(
    MonumentGenerator *g,
    int mc,
    uint64_t seed,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Structure-region RNG only (no biome sampling) — cheap geometric prefilter.
 *  @return 1 if the region yields a structure position */
int monument_region_pos(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** @return 1 if a monument exists near block (targetX,targetZ) within tolerance */
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

/**
 * Usable spawning area for a group of n (1..4) monuments, evaluated with the
 * horizontal 128-block spawning circle (no height dimension), mirroring
 * cubiomes-viewer's getOptimalAfk(qs, 58, 0, 58, ...) call.
 *
 * Each monument contributes a 58x58 spawning box anchored at its position.
 * Searches whole-block AFK positions for the maximum covered box blocks,
 * seeded from pair midpoints and flood-filled (iteratively), ties resolved
 * by averaging equally good positions (same as cubiomes quadbase.c).
 *
 * @p        : monument positions
 * @n        : number of positions (1..4)
 * @ax,az    : spawning box size (58, 58 for monuments)
 * @outX,outZ: optional output of the optimal AFK block position
 * @return   : number of spawning blocks covered from the best AFK point
 */
int monument_afk_coverage(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX,
    int *outZ);

#ifdef __cplusplus
}
#endif

#endif
