#include "monument_search_core.h"

#include "cubiomes/biomes.h"
#include "cubiomes/biomenoise.h"
#include "cubiomes/finders.h"
#include "cubiomes/generator.h"

#include <stdlib.h>

enum {
    OCEAN_TYPE_OTHERS = 0,
    OCEAN_TYPE_COLD = 1,
    OCEAN_TYPE_FROZEN = 2,
};

/** Minecraft climate band boundaries (temperature / continentalness). */
static const double TEMP_FROZEN_MAX = -0.45;
static const double TEMP_COLD_MAX = -0.15;
static const double CONT_OCEAN_MAX = -0.11;

static int classify_ocean_point(const BiomeNoise *bn, int blockX, int blockZ)
{
    /* sampleBiomeNoise expects 1:4 horizontal coords (see cubiomes mapApproxHeight). */
    int sampleX = blockX / 4;
    int sampleZ = blockZ / 4;
    int64_t np[NP_MAX] = {0};
    sampleBiomeNoise(bn, np, sampleX, 0, sampleZ, NULL, SAMPLE_NO_DEPTH | SAMPLE_NO_BIOME);

    double cont = np[NP_CONTINENTALNESS] / 10000.0;
    if (cont >= CONT_OCEAN_MAX)
        return -1;

    double temp = np[NP_TEMPERATURE] / 10000.0;
    if (temp < TEMP_FROZEN_MAX)
        return OCEAN_TYPE_FROZEN;
    if (temp < TEMP_COLD_MAX)
        return OCEAN_TYPE_COLD;
    return OCEAN_TYPE_OTHERS;
}

/** Floor-division mapping from block coordinate to the containing 512-block region. */
static int block_to_region(int block)
{
    if (block >= 0)
        return block / 512;
    return (int) -((-(int64_t) block + 511) / 512);
}

struct DmMonumentGenerator {
    Generator gen;
};

DmMonumentGenerator *dm_monument_generator_alloc(void)
{
    return (DmMonumentGenerator *) calloc(1, sizeof(DmMonumentGenerator));
}

void dm_monument_generator_free(DmMonumentGenerator *g)
{
    free(g);
}

void dm_monument_init_generator(DmMonumentGenerator *g, int mc, uint64_t seed)
{
    if (!g)
        return;
    setupGenerator(&g->gen, mc, 0);
    applySeed(&g->gen, DIM_OVERWORLD, seed);
}

static int check_viable(DmMonumentGenerator *g, int blockX, int blockZ)
{
    return isViableStructurePos(Monument, &g->gen, blockX, blockZ, 0);
}

int dm_monument_try_region_g(
    DmMonumentGenerator *g,
    int mc,
    uint64_t seed,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ)
{
    Pos pos;
    if (!g)
        return 0;
    if (!getStructurePos(Monument, mc, seed, regX, regZ, &pos))
        return 0;

    int blockX = pos.x;
    int blockZ = pos.z;

    if (!check_viable(g, blockX, blockZ))
        return 0;

    if (outBlockX)
        *outBlockX = blockX;
    if (outBlockZ)
        *outBlockZ = blockZ;
    return 1;
}

int dm_monument_try_region(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ)
{
    DmMonumentGenerator g;
    dm_monument_init_generator(&g, mc, seed);
    return dm_monument_try_region_g(&g, mc, seed, regX, regZ, outBlockX, outBlockZ);
}

int dm_monument_exists_at_block_g(
    DmMonumentGenerator *g,
    int mc,
    uint64_t seed,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ)
{
    /* Must floor, not truncate: C division rounds negative coordinates toward zero,
       which would map e.g. -65552 into region -128 instead of region -129. */
    int regionX = block_to_region(targetX);
    int regionZ = block_to_region(targetZ);
    int blockX = 0;
    int blockZ = 0;

    if (!dm_monument_try_region_g(g, mc, seed, regionX, regionZ, &blockX, &blockZ))
        return 0;

    const int tolerance = 16;
    if (abs(blockX - targetX) > tolerance || abs(blockZ - targetZ) > tolerance)
        return 0;

    if (outBlockX)
        *outBlockX = blockX;
    if (outBlockZ)
        *outBlockZ = blockZ;
    return 1;
}

int dm_monument_exists_at_block(
    uint64_t seed,
    int mc,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ)
{
    DmMonumentGenerator g;
    dm_monument_init_generator(&g, mc, seed);
    return dm_monument_exists_at_block_g(&g, mc, seed, targetX, targetZ, outBlockX, outBlockZ);
}

int dm_monument_classify_ocean_type_g(
    DmMonumentGenerator *g,
    int mc,
    int centerX,
    int centerZ)
{
    static const int offsets[4][2] = {{-64, -64}, {64, -64}, {-64, 64}, {64, 64}};
    int hasCold = 0;
    int hasFrozen = 0;

    if (!g || mc < MC_1_18)
        return OCEAN_TYPE_OTHERS;

    const BiomeNoise *bn = &g->gen.bn;
    for (int i = 0; i < 4; i++) {
        int x = centerX + offsets[i][0];
        int z = centerZ + offsets[i][1];
        int pt = classify_ocean_point(bn, x, z);
        if (pt == OCEAN_TYPE_COLD)
            hasCold = 1;
        else if (pt == OCEAN_TYPE_FROZEN)
            hasFrozen = 1;
    }

    if (hasCold)
        return OCEAN_TYPE_COLD;
    if (hasFrozen)
        return OCEAN_TYPE_FROZEN;
    return OCEAN_TYPE_OTHERS;
}

int dm_monument_classify_ocean_type(
    int mc,
    uint64_t seed,
    int centerX,
    int centerZ)
{
    DmMonumentGenerator g;
    dm_monument_init_generator(&g, mc, seed);
    return dm_monument_classify_ocean_type_g(&g, mc, centerX, centerZ);
}
