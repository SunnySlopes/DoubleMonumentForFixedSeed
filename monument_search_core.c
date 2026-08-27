#include "monument_search_core.h"

#include "cubiomes/biomes.h"
#include "cubiomes/biomenoise.h"
#include "cubiomes/finders.h"
#include "cubiomes/generator.h"

#include <limits.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

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

struct MonumentGenerator {
    Generator gen;
};

MonumentGenerator *monument_generator_alloc(void)
{
    return (MonumentGenerator *) calloc(1, sizeof(MonumentGenerator));
}

void monument_generator_free(MonumentGenerator *g)
{
    free(g);
}

void monument_init_generator(MonumentGenerator *g, int mc, uint64_t seed)
{
    if (!g)
        return;
    setupGenerator(&g->gen, mc, 0);
    applySeed(&g->gen, DIM_OVERWORLD, seed);
}

static int check_viable(MonumentGenerator *g, int blockX, int blockZ)
{
    return isViableStructurePos(Monument, &g->gen, blockX, blockZ, 0);
}

int monument_try_region_g(
    MonumentGenerator *g,
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

int monument_try_region(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ)
{
    MonumentGenerator g;
    monument_init_generator(&g, mc, seed);
    return monument_try_region_g(&g, mc, seed, regX, regZ, outBlockX, outBlockZ);
}

int monument_region_pos(
    uint64_t seed,
    int mc,
    int regX,
    int regZ,
    int *outBlockX,
    int *outBlockZ)
{
    Pos pos;
    if (!getStructurePos(Monument, mc, seed, regX, regZ, &pos))
        return 0;
    if (outBlockX)
        *outBlockX = pos.x;
    if (outBlockZ)
        *outBlockZ = pos.z;
    return 1;
}

int monument_exists_at_block_g(
    MonumentGenerator *g,
    int mc,
    uint64_t seed,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ)
{
    int regionX = targetX / 512;
    int regionZ = targetZ / 512;
    int blockX = 0;
    int blockZ = 0;

    if (!monument_try_region_g(g, mc, seed, regionX, regionZ, &blockX, &blockZ))
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

int monument_exists_at_block(
    uint64_t seed,
    int mc,
    int targetX,
    int targetZ,
    int *outBlockX,
    int *outBlockZ)
{
    MonumentGenerator g;
    monument_init_generator(&g, mc, seed);
    return monument_exists_at_block_g(&g, mc, seed, targetX, targetZ, outBlockX, outBlockZ);
}

int monument_classify_ocean_type_g(
    MonumentGenerator *g,
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

int monument_classify_ocean_type(
    int mc,
    uint64_t seed,
    int centerX,
    int centerZ)
{
    MonumentGenerator g;
    monument_init_generator(&g, mc, seed);
    return monument_classify_ocean_type_g(&g, mc, centerX, centerZ);
}

/* ==================== AFK coverage (multi-monument groups) ==================== */

/* Horizontal spawning circle: the AFK position attracts (keeps alive) every
 * spawning block within euclidean distance 128 on the horizontal plane. */
static const double AFK_RSQ = 128.0 * 128.0;

/* Monument spawning box anchor. getStructurePos returns the structure-region
 * chunk corner; the in-game monument centre sits at corner + 8 and the 58x58
 * bounding box extends 29 blocks north-west and 28 blocks south-east of it:
 * [pos + 8 - 29, pos + 8 + 28] = [pos - 21, pos + 36]. (In-game verified.)
 * Area is invariant to any rigid anchor shift, but the reported AFK point is
 * only correct with this anchor. */
#define AFK_BOX_LO(ax) (8 - ((ax) + 1) / 2)        /* -21 for 58 */
#define AFK_BOX_HI(ax) (AFK_BOX_LO(ax) + (ax) - 1) /* +36 for 58 */

typedef struct {
    const MonumentBlockPos *p;
    int n;
    int *buf; /* flood-fill memo: -1 = unvisited, else covered count */
    int x0, z0, w, h;
    int ax, az;
    int best;
    int sumn;
    int64_t sumx, sumz;
} AfkMeta;

static int afk_blocks_in_range(
    const MonumentBlockPos *p,
    int n,
    int x,
    int z,
    int ax,
    int az)
{
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        double dx = (double)p[i].x + AFK_BOX_LO(ax) - x;
        double dz = (double)p[i].z + AFK_BOX_LO(az) - z;
        for (int px = 0; px < ax; px++) {
            for (int pz = 0; pz < az; pz++) {
                double ddx = px + dx;
                double ddz = pz + dz;
                cnt += (ddx * ddx + ddz * ddz <= AFK_RSQ);
            }
        }
    }
    return cnt;
}

/* Iterative flood fill from one seed point (recursion could get very deep on
 * wide 4-monument groups). Neighbours are only expanded while the current
 * block ties or beats the running best, as in cubiomes quadbase.c. */
static void afk_flood(AfkMeta *d, int sx, int sz)
{
    if (sx < 0 || sz < 0 || sx >= d->w || sz >= d->h)
        return;

    int cap = d->w * d->h;
    /* a cell can be pushed once per neighbour (plus the seed), so allow 4x */
    int *stack = (int *) malloc(sizeof(int) * (size_t) cap * 4 + sizeof(int) * 4);
    if (!stack)
        return;

    int top = 0;
    stack[top++] = sz * d->w + sx;

    while (top > 0) {
        int idx = stack[--top];
        if (d->buf[idx] >= 0)
            continue;

        int x = idx % d->w;
        int z = idx / d->w;
        int q = afk_blocks_in_range(d->p, d->n, x + d->x0, z + d->z0, d->ax, d->az);
        d->buf[idx] = q;

        if (q >= d->best) {
            if (q > d->best) {
                d->best = q;
                d->sumn = 1;
                d->sumx = d->x0 + x;
                d->sumz = d->z0 + z;
            } else {
                d->sumn += 1;
                d->sumx += d->x0 + x;
                d->sumz += d->z0 + z;
            }
            if (x > 0) stack[top++] = idx - 1;
            if (x < d->w - 1) stack[top++] = idx + 1;
            if (z > 0) stack[top++] = idx - d->w;
            if (z < d->h - 1) stack[top++] = idx + d->w;
        }
    }

    free(stack);
}

/* Exact full-window coverage scan for n >= 3 groups.
 *
 * The flood fill used for pairs only walks non-decreasing plateaus, so a
 * triangle/quad whose global optimum is separated from every midpoint or
 * centroid seed by a dip would be scored with a local maximum instead
 * (observed: 7895 reported where far more was achievable).  This path
 * evaluates every window cell exactly:
 *
 *   - window [x0, x0+w) x [z0, z0+h) provably contains the optimum: clamping
 *     an outside candidate towards the group bounding box never decreases any
 *     box-row interval intersection;
 *   - row decomposition keeps it cheap: a box block-row at z only reaches
 *     candidate rows pz with |pz - z| <= 128, and within one (box row,
 *     candidate row) pair the per-column contribution is the length of the
 *     intersection [max(bx, px-R), min(bx+ax-1, px+R)], with
 *     R = floor(sqrt(128^2 - dz^2)) precomputed per dz. */
static void afk_coverage_exact(
    const MonumentBlockPos *p,
    int n,
    int x0,
    int z0,
    int w,
    int h,
    int ax,
    int az,
    MonumentBlockPos *out,
    int *outCnt)
{
    const int RMAX = 128;
    int rt[2 * RMAX + 1];
    for (int dz = -RMAX; dz <= RMAX; dz++)
        rt[dz + RMAX] = (int) sqrt((double) RMAX * RMAX - (double) dz * dz);

    int *cov = (int *) malloc(sizeof(int) * (size_t) w * (size_t) h);
    int *stack = (int *) malloc(sizeof(int) * (size_t) w * (size_t) h);
    if (!cov || !stack) {
        free(cov);
        free(stack);
        return; /* keep the caller's single-box fallback */
    }
    memset(cov, 0, sizeof(int) * (size_t) w * (size_t) h);

    for (int i = 0; i < n; i++) {
        const int bx = p[i].x + AFK_BOX_LO(ax);
        const int bx1 = p[i].x + AFK_BOX_HI(ax);
        for (int j = 0; j < az; j++) { /* box block-rows */
            const int rowz = p[i].z + AFK_BOX_LO(az) + j;
            int pz = rowz - RMAX > z0 ? rowz - RMAX : z0;
            const int pzHi = rowz + RMAX < z0 + h - 1 ? rowz + RMAX : z0 + h - 1;
            for (; pz <= pzHi; pz++) {
                const int R = rt[(pz - rowz) + RMAX];
                int px = bx - R > x0 ? bx - R : x0;
                const int pxHi = bx1 + R < x0 + w - 1 ? bx1 + R : x0 + w - 1;
                int *crow = cov + (size_t) (pz - z0) * w;
                for (; px <= pxHi; px++) {
                    int lo = px - R > bx ? px - R : bx;
                    int hi = px + R < bx1 ? px + R : bx1;
                    crow[px - x0] += hi - lo + 1;
                }
            }
        }
    }

    int best = 0;
    int bIdx = -1;
    for (int k = 0; k < w * h; k++) {
        if (cov[k] > best) {
            best = cov[k];
            bIdx = k;
        }
    }

    if (bIdx < 0) {
        free(cov);
        free(stack);
        return;
    }

    /* average the plateau of globally optimal cells connected to bIdx
     * (disconnected maxima must not be averaged into the same point) */
    int64_t sumx = 0;
    int64_t sumz = 0;
    int sumn = 0;
    int top = 0;
    stack[top++] = bIdx;
    cov[bIdx] = -1;
    while (top > 0) {
        int idx = stack[--top];
        int x = idx % w;
        int z = idx / w;
        sumx += x0 + x;
        sumz += z0 + z;
        sumn++;
        if (x > 0 && cov[idx - 1] == best) {
            cov[idx - 1] = -1;
            stack[top++] = idx - 1;
        }
        if (x < w - 1 && cov[idx + 1] == best) {
            cov[idx + 1] = -1;
            stack[top++] = idx + 1;
        }
        if (z > 0 && cov[idx - w] == best) {
            cov[idx - w] = -1;
            stack[top++] = idx - w;
        }
        if (z < h - 1 && cov[idx + w] == best) {
            cov[idx + w] = -1;
            stack[top++] = idx + w;
        }
    }

    free(cov);
    free(stack);

    out->x = (int) lround((double) sumx / sumn);
    out->z = (int) lround((double) sumz / sumn);
    *outCnt = best;
}

int monument_afk_coverage(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX,
    int *outZ)
{
    if (!p || n < 1 || n > 4 || ax <= 0 || az <= 0)
        return 0;

    /* window over the box extents: [pos + 9 - ax, pos + 8] per axis */
    int minX = INT_MAX, minZ = INT_MAX, maxX = INT_MIN, maxZ = INT_MIN;
    for (int i = 0; i < n; i++) {
        if (p[i].x < minX) minX = p[i].x;
        if (p[i].z < minZ) minZ = p[i].z;
        if (p[i].x > maxX) maxX = p[i].x;
        if (p[i].z > maxZ) maxZ = p[i].z;
    }
    minX += AFK_BOX_LO(ax);
    minZ += AFK_BOX_LO(az);
    maxX += AFK_BOX_HI(ax);
    maxZ += AFK_BOX_HI(az);
    int w = maxX - minX + 1;
    int h = maxZ - minZ + 1;

    MonumentBlockPos afk = {p[0].x + AFK_BOX_LO(ax) + ax / 2,
                            p[0].z + AFK_BOX_LO(az) + az / 2};
    int cnt = ax * az;

    if (w < 1 || h < 1) {
        if (outX) *outX = afk.x;
        if (outZ) *outZ = afk.z;
        return cnt;
    }

    /* n >= 3: exact full-window scan. The plateau-limited flood fill below
     * cannot cross a dip, so for triangles/quads it can report a local
     * maximum instead of the global optimum. */
    if (n >= 3) {
        afk_coverage_exact(p, n, minX, minZ, w, h, ax, az, &afk, &cnt);
        if (outX) *outX = afk.x;
        if (outZ) *outZ = afk.z;
        return cnt;
    }

    AfkMeta d;
    d.p = p;
    d.n = n;
    d.buf = (int *) malloc(sizeof(int) * (size_t) w * (size_t) h);
    if (!d.buf) {
        if (outX) *outX = afk.x;
        if (outZ) *outZ = afk.z;
        return cnt;
    }
    for (size_t i = 0; i < (size_t) w * (size_t) h; i++)
        d.buf[i] = -1;
    d.x0 = minX;
    d.z0 = minZ;
    d.w = w;
    d.h = h;
    d.ax = ax;
    d.az = az;

    /* seed candidates for pairs: the pair midpoint. The optimum of two boxes
     * lies on the segment between the centers, which the plateau walk from
     * the midpoint covers completely. */
    MonumentBlockPos dsp[1];
    int ndsp = 0;
    if (n == 2) {
        dsp[0].x = (p[0].x + p[1].x) / 2;
        dsp[0].z = (p[0].z + p[1].z) / 2;
        ndsp = 1;
    }

    int v[1] = {0};
    for (int i = 0; i < ndsp; i++)
        v[i] = afk_blocks_in_range(p, n, dsp[i].x, dsp[i].z, ax, az);

    for (int round = 0; round < ndsp; round++) {
        int jmax = -1;
        int vmax = 0;
        for (int j = 0; j < ndsp; j++) {
            if (v[j] > vmax) {
                vmax = v[j];
                jmax = j;
            }
        }
        if (jmax < 0 || vmax <= ax * az)
            break;

        d.best = vmax;
        d.sumn = 0;
        d.sumx = 0;
        d.sumz = 0;
        afk_flood(&d, dsp[jmax].x - d.x0, dsp[jmax].z - d.z0);
        if (d.best > cnt && d.sumn > 0) {
            cnt = d.best;
            afk.x = (int) lround((double) d.sumx / d.sumn);
            afk.z = (int) lround((double) d.sumz / d.sumn);
            if (cnt >= (n - 1) * ax * az)
                break;
        }
        v[jmax] = 0;
    }

    free(d.buf);

    if (outX)
        *outX = afk.x;
    if (outZ)
        *outZ = afk.z;
    return cnt;
}
