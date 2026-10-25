/*
 * cubiomes helpers for the double monument kernel.
 *
 * All symbols are prefixed with `dm_` / `Dm` because this translation unit is
 * linked together with the multimonument and dripstonecave kernels, which
 * also export monument_* / MonumentGenerator names.
 */

#ifndef AIO_DOUBLE_MONUMENT_CORE_H
#define AIO_DOUBLE_MONUMENT_CORE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Opaque cubiomes Generator wrapper (defined only in .c). */
typedef struct DmMonumentGenerator DmMonumentGenerator;

DmMonumentGenerator *dm_monument_generator_alloc(void);
void dm_monument_generator_free(DmMonumentGenerator *g);

void dm_monument_init_generator(DmMonumentGenerator *g, int mc, uint64_t seed);

/** @return 1 if monument exists at region and passes deep-ocean viability */
int dm_monument_try_region(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** Uses an already-initialized generator (no setupGenerator per call). */
int dm_monument_try_region_g(
    DmMonumentGenerator *g,
    int mc,
    uint64_t seed,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ);

/** @return 1 if a monument exists near block (targetX,targetZ) within tolerance */
int dm_monument_exists_at_block(
    uint64_t seed,
    int mc,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ);

int dm_monument_exists_at_block_g(
    DmMonumentGenerator *g,
    int mc,
    uint64_t seed,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ);

/** Ocean type at pair center: 0=others, 1=cold, 2=frozen */
int dm_monument_classify_ocean_type(
    int mc,
    uint64_t seed,
    int centerX,
    int centerZ);

int dm_monument_classify_ocean_type_g(
    DmMonumentGenerator *g,
    int mc,
    int centerX,
    int centerZ);

#ifdef __cplusplus
}
#endif

#endif
