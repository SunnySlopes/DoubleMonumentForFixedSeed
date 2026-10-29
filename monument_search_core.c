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

// Minecraft climate band boundaries (temperature / continentalness)
static const double TEMP_FROZEN_MAX = -0.45;
static const double TEMP_COLD_MAX = -0.15;
static const double TEMP_NORMAL_MAX = 0.2;
static const double TEMP_LUKEWARM_MAX = 0.55;
static const double CONT_OCEAN_MAX = -0.11;

static int classify_ocean_point(const BiomeNoise *bn, int blockX, int blockZ)
{
    // sampleBiomeNoise expects 1:4 horizontal coords (see cubiomes mapApproxHeight)
    int sampleX = blockX / 4;
    int sampleZ = blockZ / 4;
    int64_t np[NP_MAX] = {0};
    sampleBiomeNoise(bn, np, sampleX, 0, sampleZ, NULL, SAMPLE_NO_DEPTH | SAMPLE_NO_BIOME);

    double cont = np[NP_CONTINENTALNESS] / 10000.0;
    if (cont >= CONT_OCEAN_MAX)
        return 0;

    double temp = np[NP_TEMPERATURE] / 10000.0;
    if (temp < TEMP_FROZEN_MAX)
        return OCEAN_MASK_FROZEN;
    if (temp < TEMP_COLD_MAX)
        return OCEAN_MASK_COLD;
    if (temp < TEMP_NORMAL_MAX)
        return OCEAN_MASK_NORMAL;
    if (temp < TEMP_LUKEWARM_MAX)
        return OCEAN_MASK_LUKEWARM;
    return OCEAN_MASK_WARM;
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

int monument_classify_ocean_types_g(
    MonumentGenerator *g,
    int mc,
    int centerX,
    int centerZ)
{
    static const int offsets[4][2] = {{-64, -64}, {64, -64}, {-64, 64}, {64, 64}};
    int mask = 0;

    if (!g || mc < MC_1_18)
        return 0;

    const BiomeNoise *bn = &g->gen.bn;
    for (int i = 0; i < 4; i++) {
        int x = centerX + offsets[i][0];
        int z = centerZ + offsets[i][1];
        mask |= classify_ocean_point(bn, x, z);
    }
    return mask;
}

int monument_classify_ocean_type_g(
    MonumentGenerator *g,
    int mc,
    int centerX,
    int centerZ)
{
    int mask = monument_classify_ocean_types_g(g, mc, centerX, centerZ);
    if (mask & OCEAN_MASK_COLD)
        return OCEAN_TYPE_COLD;
    if (mask & OCEAN_MASK_FROZEN)
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

// ==================== AFK coverage (multi-monument groups) ====================

// AFK position attracts (keeps alive) every spawning block within euclidean distance 128 on the horizontal plane.
static const double AFK_RSQ = 128.0 * 128.0;

// getStructurePos returns the region chunk corner; the 58x58 box spans [pos-29, pos+28] (centre = pos, in-game verified 2026-08-28). Area is anchor-invariant but the reported AFK point is only correct with this anchor.
#define AFK_BOX_LO(ax) (-(((ax) + 1) / 2))         // -29 for 58
#define AFK_BOX_HI(ax) (AFK_BOX_LO(ax) + (ax) - 1) // +28 for 58

typedef struct {
    const MonumentBlockPos *p;
    int n;
    int *buf; // flood-fill memo: -1 = unvisited, else covered count
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

// Iterative flood fill from a seed (recursion could get very deep on wide groups); expand neighbours only while the current block ties or beats the best, as in cubiomes quadbase.c.
static void afk_flood(AfkMeta *d, int sx, int sz)
{
    if (sx < 0 || sz < 0 || sx >= d->w || sz >= d->h)
        return;

    int cap = d->w * d->h;
    // a cell can be pushed once per neighbour (plus the seed), so allow 4x
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

// Exact full-window scan for n >= 3 groups: the pair flood fill only walks non-decreasing plateaus, so a group whose optimum is separated from every seed by a dip would score a local maximum (observed: 7895 reported where far more was achievable). The window [x0,x0+w)x[z0,z0+h) provably contains the optimum (clamping outside candidates towards the bounding box never decreases coverage), and a row decomposition with precomputed R(dz) = floor(sqrt(128^2-dz^2)) keeps it cheap.
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
        return; // keep the caller's single-box fallback
    }
    memset(cov, 0, sizeof(int) * (size_t) w * (size_t) h);

    for (int i = 0; i < n; i++) {
        const int bx = p[i].x + AFK_BOX_LO(ax);
        const int bx1 = p[i].x + AFK_BOX_HI(ax);
        for (int j = 0; j < az; j++) { // box block-rows
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

    // average only the plateau of globally optimal cells connected to bIdx (disconnected maxima must not be merged into the same point)
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

    // window over the box extents: [pos + 9 - ax, pos + 8] per axis
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

    // n >= 3: exact full-window scan; the plateau-limited flood fill below would miss optima separated from seeds by a dip
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

    // seed candidates for pairs: the pair midpoint (the two-box optimum lies on the centre segment, fully covered by the plateau walk from it)
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

// ==================== dual-dummy AFK coverage (two AFK points + single reference) ====================

// Same spawning model as above, but with TWO AFK points: a spawning block is kept when it is
// within 128 blocks of EITHER point. Area keeps the multiset semantics of the single-point code
// (overlapping boxes count per box). Farm-footprint constraint: the counted area must satisfy
// x_max - x_min <= 256 and z_max - z_min <= 256 (a single 128-disk spans exactly 256, i.e. 257
// block columns, so the constraint only ever rules out dual pairs sprawled wider than one disk).
// Identity used throughout, per box row span:
//   union(a, b) = cov(a) + |disk(b) \ disk(a)|  -> for a fixed anchor a the optimal partner and
//   its exact union come from one coverage field built with a's disk subtracted.

#define DUAL_DISK_R 128

static void dual_rt_build(int *rt)
{
    for (int dz = -DUAL_DISK_R; dz <= DUAL_DISK_R; dz++)
        rt[dz + DUAL_DISK_R] = (int) sqrt((double) DUAL_DISK_R * DUAL_DISK_R - (double) dz * dz);
}

// Add one source span [lo,hi] on box row `rowz` into the candidate field: for every candidate
// point (px,pz) the added value is |[lo,hi] ∩ disk((px,pz),128) \ excl|. The per-px count
// min(px+R,hi) - max(px-R,lo) + 1 is a trapezoid, so each disk row is applied as three branch-free
// ramps instead of per-cell min/max.
static void dual_field_add_span(
    int *field,
    int x0,
    int z0,
    int w,
    int h,
    int lo,
    int hi,
    int rowz,
    const int *rt)
{
    const int L = hi - lo + 1;
    int pzLo = rowz - DUAL_DISK_R > z0 ? rowz - DUAL_DISK_R : z0;
    int pzHi = rowz + DUAL_DISK_R < z0 + h - 1 ? rowz + DUAL_DISK_R : z0 + h - 1;

    for (int pz = pzLo; pz <= pzHi; pz++) {
        const int R = rt[pz - rowz + DUAL_DISK_R];
        int xlo = lo - R > x0 ? lo - R : x0;
        int xhi = hi + R < x0 + w - 1 ? hi + R : x0 + w - 1;
        if (xlo > xhi)
            continue;

        int *crow = field + (size_t) (pz - z0) * w;
        const int p1 = lo + R;
        const int p2 = hi - R;
        const int upEnd = p1 - 1 < p2 ? p1 - 1 : p2;
        const int downBeg = p1 > p2 + 1 ? p1 : p2 + 1;
        const int plateau = p1 <= p2 ? 2 * R + 1 : L;

        int xs = lo - R > xlo ? lo - R : xlo;
        int xe = upEnd < xhi ? upEnd : xhi;
        int c = xs - (lo - R) + 1;
        for (int x = xs; x <= xe; x++, c++)
            crow[x - x0] += c;

        xs = upEnd + 1 > xlo ? upEnd + 1 : xlo;
        xe = downBeg - 1 < xhi ? downBeg - 1 : xhi;
        for (int x = xs; x <= xe; x++)
            crow[x - x0] += plateau;

        xs = downBeg > xlo ? downBeg : xlo;
        xe = hi + R < xhi ? hi + R : xhi;
        c = hi + R - xs + 1;
        for (int x = xs; x <= xe; x++, c--)
            crow[x - x0] += c;
    }
}

// field[q] = sum over boxes of |box ∩ disk(q) \ (excl disk)| for every candidate q in the window;
// hasExcl=0 builds the plain single-point coverage field.
static void dual_field_build(
    const MonumentBlockPos *p,
    int n,
    int x0,
    int z0,
    int w,
    int h,
    int ax,
    int az,
    int exclX,
    int exclZ,
    int hasExcl,
    int *field,
    const int *rt)
{
    memset(field, 0, sizeof(int) * (size_t) w * (size_t) h);

    for (int i = 0; i < n; i++) {
        const int bx = p[i].x + AFK_BOX_LO(ax);
        const int bx1 = p[i].x + AFK_BOX_HI(ax);
        const int bz = p[i].z + AFK_BOX_LO(az);
        const int bz1 = p[i].z + AFK_BOX_HI(az);
        for (int rowz = bz; rowz <= bz1; rowz++) {
            int lo = bx;
            int hi = bx1;
            if (hasExcl) {
                int dze = rowz - exclZ;
                if (dze >= -DUAL_DISK_R && dze <= DUAL_DISK_R) {
                    const int R = rt[dze + DUAL_DISK_R];
                    const int elo = exclX - R;
                    const int ehi = exclX + R;
                    if (ehi >= lo) {
                        // right remainder [max(ehi+1,lo), hi]
                        const int s2 = ehi + 1 > lo ? ehi + 1 : lo;
                        if (s2 <= hi)
                            dual_field_add_span(field, x0, z0, w, h, s2, hi, rowz, rt);
                        // left remainder [lo, min(hi, elo-1)]; empty when the exclusion covers
                        // the row start. NB: when the exclusion row lies entirely LEFT of the
                        // box row (ehi < lo) the whole row must be kept - clamping hi here
                        // would drop it.
                        if (elo <= lo)
                            continue;
                        if (elo - 1 < hi)
                            hi = elo - 1;
                        if (lo > hi)
                            continue;
                    }
                }
            }
            dual_field_add_span(field, x0, z0, w, h, lo, hi, rowz, rt);
        }
    }
}

// Argmax of the field over the candidate rectangle [rx0,rx1]x[rz0,rz1] (absolute inclusive
// coords, clamped into the window). Returns the first maximum cell in row-major scan order - a
// REAL cell whose field value equals the return, so coverage identities stay exact. (Plateau
// averaging is deliberately NOT done here: the averaged centre can drift off the plateau and
// then covers fewer blocks than the reported best.)
static int dual_field_best(
    const int *field,
    int x0,
    int z0,
    int w,
    int h,
    int rx0,
    int rz0,
    int rx1,
    int rz1,
    int *outX,
    int *outZ)
{
    const int xa0 = rx0 > x0 ? rx0 : x0;
    const int xa1 = rx1 < x0 + w - 1 ? rx1 : x0 + w - 1;
    const int za0 = rz0 > z0 ? rz0 : z0;
    const int za1 = rz1 < z0 + h - 1 ? rz1 : z0 + h - 1;

    int best = 0;
    int bX = x0;
    int bZ = z0;
    for (int z = za0; z <= za1; z++) {
        const int *row = field + (size_t) (z - z0) * w;
        for (int x = xa0; x <= xa1; x++) {
            if (row[x - x0] > best) {
                best = row[x - x0];
                bX = x;
                bZ = z;
            }
        }
    }
    if (outX) *outX = bX;
    if (outZ) *outZ = bZ;
    return best;
}

// Exact union of two points (multiset count) plus the bounding box of the counted blocks
// (~n*ax*az ops; used for honest evaluation of candidates and the span check).
typedef struct {
    int x0, z0, x1, z1; // covered bounding box (INT_MAX/INT_MIN when count == 0)
    int count;
} DualCoverInfo;

static void dual_cover_union_info(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int x1,
    int z1,
    int x2,
    int z2,
    DualCoverInfo *out)
{
    out->x0 = INT_MAX;
    out->x1 = INT_MIN;
    out->z0 = INT_MAX;
    out->z1 = INT_MIN;
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        int bx = p[i].x + AFK_BOX_LO(ax);
        int bz = p[i].z + AFK_BOX_LO(az);
        for (int px = 0; px < ax; px++) {
            double ddx1 = (double) bx + px - x1;
            double ddx2 = (double) bx + px - x2;
            for (int pz = 0; pz < az; pz++) {
                double ddz1 = (double) bz + pz - z1;
                double ddz2 = (double) bz + pz - z2;
                if ((ddx1 * ddx1 + ddz1 * ddz1 <= AFK_RSQ) ||
                    (ddx2 * ddx2 + ddz2 * ddz2 <= AFK_RSQ)) {
                    cnt++;
                    int cx = bx + px;
                    int cz = bz + pz;
                    if (cx < out->x0) out->x0 = cx;
                    if (cx > out->x1) out->x1 = cx;
                    if (cz < out->z0) out->z0 = cz;
                    if (cz > out->z1) out->z1 = cz;
                }
            }
        }
    }
    out->count = cnt;
}

// Farm-footprint constraint: the counted spawning area must fit max-min <= 256 per axis
// (2*DUAL_DISK_R). A single 128-disk always satisfies it automatically (its projection is
// exactly 256 wide), so it only ever rules out dual pairs sprawled wider than one disk.
#define DUAL_SPAN_LIMIT (2 * DUAL_DISK_R)

static int dual_cover_valid(const DualCoverInfo *ci)
{
    if (ci->count == 0)
        return 1;
    return ci->x1 - ci->x0 <= DUAL_SPAN_LIMIT && ci->z1 - ci->z0 <= DUAL_SPAN_LIMIT;
}

static int dual_isqrt_exact(int v)
{
    int r = (int) sqrt((double) v);
    while (r * r > v) r--;
    while ((r + 1) * (r + 1) <= v) r++;
    return r;
}

// Exact span-validity of candidate partner q against the anchor's coverage bbox: computes the
// exact bbox of q's own coverage with a closed form per box (a box contributes iff the clamped
// distance to q is <= 128; its covered x-range is |x-qx| <= isqrt(128^2-dzmin^2) clamped to the
// box, and symmetrically for z), then combines it with the anchor bbox.
static int dual_valid_at(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    const DualCoverInfo *ca,
    int qx,
    int qz)
{
    int covX0 = INT_MAX, covX1 = INT_MIN, covZ0 = INT_MAX, covZ1 = INT_MIN;
    const int R2 = DUAL_DISK_R * DUAL_DISK_R;

    for (int i = 0; i < n; i++) {
        const int bx = p[i].x + AFK_BOX_LO(ax);
        const int bxi = p[i].x + AFK_BOX_HI(ax);
        const int bz = p[i].z + AFK_BOX_LO(az);
        const int bzi = p[i].z + AFK_BOX_HI(az);
        int dx = 0, dz = 0;
        if (qx < bx) dx = bx - qx; else if (qx > bxi) dx = qx - bxi;
        if (qz < bz) dz = bz - qz; else if (qz > bzi) dz = qz - bzi;
        if (dx * dx + dz * dz > R2)
            continue; // disk does not reach this box

        int r = dual_isqrt_exact(R2 - dz * dz);
        int lo = bx > qx - r ? bx : qx - r;
        int hi = bxi < qx + r ? bxi : qx + r;
        if (lo < covX0) covX0 = lo;
        if (hi > covX1) covX1 = hi;

        r = dual_isqrt_exact(R2 - dx * dx);
        lo = bz > qz - r ? bz : qz - r;
        hi = bzi < qz + r ? bzi : qz + r;
        if (lo < covZ0) covZ0 = lo;
        if (hi > covZ1) covZ1 = hi;
    }

    if (covX0 == INT_MAX)
        return 1; // q covers nothing: the union keeps the anchor's (span-valid) bbox
    if (ca->count != 0) {
        if (ca->x0 < covX0) covX0 = ca->x0;
        if (ca->x1 > covX1) covX1 = ca->x1;
        if (ca->z0 < covZ0) covZ0 = ca->z0;
        if (ca->z1 > covZ1) covZ1 = ca->z1;
    }
    return covX1 - covX0 <= DUAL_SPAN_LIMIT && covZ1 - covZ0 <= DUAL_SPAN_LIMIT;
}

// Partners with qx in [Ba.x1-128, Ba.x0+128] (symmetric in z) keep the combined coverage inside
// the span limit by construction (coverage of q lies in [q-128, q+128] and Ba itself spans
// <= 256); candidates outside may still be valid via box clipping and are validated exactly.
static void dual_safe_rect(
    const DualCoverInfo *ca,
    int x0,
    int z0,
    int w,
    int h,
    int *rx0,
    int *rz0,
    int *rx1,
    int *rz1)
{
    int ax0, az0, ax1, az1;
    if (ca->count == 0) {
        ax0 = x0;
        az0 = z0;
        ax1 = x0 + w - 1;
        az1 = z0 + h - 1;
    } else {
        ax0 = ca->x1 - DUAL_DISK_R;
        az0 = ca->z1 - DUAL_DISK_R;
        ax1 = ca->x0 + DUAL_DISK_R;
        az1 = ca->z0 + DUAL_DISK_R;
    }
    if (ax0 < x0) ax0 = x0;
    if (az0 < z0) az0 = z0;
    if (ax1 > x0 + w - 1) ax1 = x0 + w - 1;
    if (az1 > z0 + h - 1) az1 = z0 + h - 1;
    *rx0 = ax0;
    *rz0 = az0;
    *rx1 = ax1;
    *rz1 = az1;
}

typedef struct {
    const MonumentBlockPos *p;
    int n;
    int ax, az;
    int x0, z0, w, h;
    int *field;
    const int *rt;
    int cap;    // n * ax * az: full multiset coverage, upper bound for early exit
    int stopAt; // >0: stop as soon as the best found pair reaches this area (0 = exact optimum)
} DualSolve;

// True when the search can stop: the theoretical maximum is reached, or the caller only needed
// to know whether the target area is reachable and it has been reached.
static int dual_done(const DualSolve *s, int best)
{
    if (best >= s->cap)
        return 1;
    return s->stopAt > 0 && best >= s->stopAt;
}

// Best span-valid partner for the anchor (ax_,az_) whose coverage bbox is ca: argmax of the
// anchor-excluded coverage field over the provably-valid safe rectangle, then exactly
// validated cells outside it that could still beat that maximum (usually none). The returned
// cell's field value is exact, so the identity union = cov(anchor) + value holds.
static int dual_pick_partner(
    const DualSolve *s,
    const DualCoverInfo *ca,
    int ax_,
    int az_,
    int *outX,
    int *outZ)
{
    dual_field_build(s->p, s->n, s->x0, s->z0, s->w, s->h, s->ax, s->az,
                     ax_, az_, 1, s->field, s->rt);
    int rx0, rz0, rx1, rz1;
    dual_safe_rect(ca, s->x0, s->z0, s->w, s->h, &rx0, &rz0, &rx1, &rz1);

    int gx = ax_, gz = az_, gval = -1;
    if (rx0 <= rx1 && rz0 <= rz1)
        gval = dual_field_best(s->field, s->x0, s->z0, s->w, s->h,
                               rx0, rz0, rx1, rz1, &gx, &gz);

    for (int pz = s->z0; pz < s->z0 + s->h; pz++) {
        const int *row = s->field + (size_t) (pz - s->z0) * s->w;
        for (int px = s->x0; px < s->x0 + s->w; px++) {
            int v = row[px - s->x0];
            if (v <= gval)
                continue;
            if (px >= rx0 && px <= rx1 && pz >= rz0 && pz <= rz1)
                continue; // safe-rectangle cells are already covered by the argmax above
            if (dual_valid_at(s->p, s->n, s->ax, s->az, ca, px, pz)) {
                gval = v;
                gx = px;
                gz = pz;
            }
        }
    }

    if (outX) *outX = gx;
    if (outZ) *outZ = gz;
    return gval;
}

// Alternating exact maximization under the 256-per-axis span constraint: with one point fixed,
// the identity above turns the optimal span-valid partner into one argmax (dual_pick_partner),
// so each half-step is the global optimum for that anchor among span-valid partners.
// Gauss-Seidel with a strict-improvement stop; only span-valid pairs are ever tracked, so
// invalid starts (e.g. bipartitions of far clusters) simply reseed from the first half-step.
static int dual_refine(DualSolve *s, int *inOutXa, int *inOutZa, int *inOutXb, int *inOutZb)
{
    int xa = *inOutXa, za = *inOutZa, xb = *inOutXb, zb = *inOutZb;
    DualCoverInfo ci;
    dual_cover_union_info(s->p, s->n, s->ax, s->az, xa, za, xb, zb, &ci);
    int best = dual_cover_valid(&ci) ? ci.count : -1;

    for (int round = 0; round < 8 && !dual_done(s, best); round++) {
        int improved = 0;
        int gx, gz;
        DualCoverInfo ca;

        // optimize b | a
        dual_cover_union_info(s->p, s->n, s->ax, s->az, xa, za, xa, za, &ca);
        if (dual_pick_partner(s, &ca, xa, za, &gx, &gz) >= 0) {
            dual_cover_union_info(s->p, s->n, s->ax, s->az, xa, za, gx, gz, &ci);
            if (dual_cover_valid(&ci) && ci.count > best) {
                best = ci.count;
                xb = gx;
                zb = gz;
                improved = 1;
            }
        }

        // optimize a | b
        dual_cover_union_info(s->p, s->n, s->ax, s->az, xb, zb, xb, zb, &ca);
        if (dual_pick_partner(s, &ca, xb, zb, &gx, &gz) >= 0) {
            dual_cover_union_info(s->p, s->n, s->ax, s->az, gx, gz, xb, zb, &ci);
            if (dual_cover_valid(&ci) && ci.count > best) {
                best = ci.count;
                xa = gx;
                za = gz;
                improved = 1;
            }
        }

        if (!improved || dual_done(s, best))
            break;
    }

    if (best < 0) {
        // no span-valid pair found anywhere: collapse to a coincident single point
        DualCoverInfo cs;
        dual_cover_union_info(s->p, s->n, s->ax, s->az, xa, za, xa, za, &cs);
        best = cs.count;
        xb = xa;
        zb = za;
    }

    *inOutXa = xa;
    *inOutZa = za;
    *inOutXb = xb;
    *inOutZb = zb;
    return best;
}

// Starting pairs already refined in this call. Different bipartitions of a tight cluster usually
// resolve to the same two single-point optima, and refining the identical start twice is pure
// waste (the refine is deterministic). The pair is compared ORDERED on purpose: the refine is
// Gauss-Seidel ("optimize b | a" first), so (a,b) and (b,a) are different trajectories and both
// must run.
#define DUAL_MAX_STARTS 10

static int dual_start_seen(int (*tried)[4], int *nTried, int xa, int za, int xb, int zb)
{
    for (int i = 0; i < *nTried; i++) {
        if (tried[i][0] == xa && tried[i][1] == za && tried[i][2] == xb && tried[i][3] == zb)
            return 1;
    }
    if (*nTried < DUAL_MAX_STARTS) {
        tried[*nTried][0] = xa;
        tried[*nTried][1] = za;
        tried[*nTried][2] = xb;
        tried[*nTried][3] = zb;
        (*nTried)++;
    }
    return 0;
}

// Shared engine. stopAt = 0 returns the exact optimum; stopAt > 0 stops as soon as a span-valid
// pair reaching stopAt is found, so the caller may only read the return value (>= stopAt means
// reachable, < stopAt means the true optimum is below it).
static int dual_solve(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int stopAt,
    int *outX1,
    int *outZ1,
    int *outX2,
    int *outZ2,
    int *outSingleArea,
    int *outSingleX,
    int *outSingleZ)
{
    if (!p || n < 2 || n > 4 || ax <= 0 || az <= 0)
        return 0;

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

    // Degenerate window (single stack of boxes): both points collapse onto the box centre
    int cx = p[0].x + AFK_BOX_LO(ax) + ax / 2;
    int cz = p[0].z + AFK_BOX_LO(az) + az / 2;
    if (w < 1 || h < 1 || w > 4096 || h > 4096) {
        if (outX1) *outX1 = cx;
        if (outZ1) *outZ1 = cz;
        if (outX2) *outX2 = cx;
        if (outZ2) *outZ2 = cz;
        if (outSingleArea) *outSingleArea = ax * az;
        if (outSingleX) *outSingleX = cx;
        if (outSingleZ) *outSingleZ = cz;
        return ax * az;
    }

    // Clamping any outside candidate towards the window never decreases its coverage, so both
    // points of an optimal pair lie inside this window.
    int rt[2 * DUAL_DISK_R + 1];
    dual_rt_build(rt);

    int *field = (int *) malloc(sizeof(int) * (size_t) w * (size_t) h);
    if (!field) {
        if (outX1) *outX1 = cx;
        if (outZ1) *outZ1 = cz;
        if (outX2) *outX2 = cx;
        if (outZ2) *outZ2 = cz;
        if (outSingleArea) *outSingleArea = ax * az;
        if (outSingleX) *outSingleX = cx;
        if (outSingleZ) *outSingleZ = cz;
        return ax * az;
    }

    DualSolve s;
    s.p = p;
    s.n = n;
    s.ax = ax;
    s.az = az;
    s.x0 = minX;
    s.z0 = minZ;
    s.w = w;
    s.h = h;
    s.field = field;
    s.rt = rt;
    s.cap = n * ax * az;
    s.stopAt = stopAt;

    // single-point optimum (also the reported single reference area); a single 128-disk is
    // always span-valid, so no rectangle restriction applies here. The returned cell attains
    // the returned value exactly.
    dual_field_build(p, n, minX, minZ, w, h, ax, az, 0, 0, 0, field, rt);
    int sx, sz;
    int singleArea = dual_field_best(field, minX, minZ, w, h,
                                     minX, minZ, minX + w - 1, minZ + h - 1, &sx, &sz);
    if (outSingleArea) *outSingleArea = singleArea;
    if (outSingleX) *outSingleX = sx;
    if (outSingleZ) *outSingleZ = sz;

    // A single point already reaches the requested area: the union cannot be smaller than the
    // single optimum, so there is nothing left to search.
    if (stopAt > 0 && singleArea >= stopAt) {
        free(field);
        if (outX1) *outX1 = sx;
        if (outZ1) *outZ1 = sz;
        if (outX2) *outX2 = sx;
        if (outZ2) *outZ2 = sz;
        return singleArea;
    }

    int tried[DUAL_MAX_STARTS][4];
    int nTried = 0;
    int bx1 = sx, bz1 = sz, bx2 = sx, bz2 = sz;
    int best = singleArea;

    // Start 1: from the single-point optimum (first refine half-step splits it)
    {
        int xa = sx, za = sz, xb = sx, zb = sz;
        dual_start_seen(tried, &nTried, xa, za, xb, zb);
        int v = dual_refine(&s, &xa, &za, &xb, &zb);
        if (v > best) {
            best = v;
            bx1 = xa;
            bz1 = za;
            bx2 = xb;
            bz2 = zb;
        }
    }

    // Starts 2..: every non-trivial bipartition, each side at its own single-point optimum
    // (the typical dual-farm layout: each point owns one cluster)
    const int full = (1 << n) - 1;
    for (int mask = 1; mask < full && !dual_done(&s, best); mask++) {
        int comp = full ^ mask;
        if (mask > comp)
            continue; // symmetric bipartition
        MonumentBlockPos pa[4], pb[4];
        int na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            if (mask & (1 << i))
                pa[na++] = p[i];
            else
                pb[nb++] = p[i];
        }

        int xa, za, xb, zb;
        dual_field_build(pa, na, minX, minZ, w, h, ax, az, 0, 0, 0, field, rt);
        dual_field_best(field, minX, minZ, w, h,
                        minX, minZ, minX + w - 1, minZ + h - 1, &xa, &za);
        dual_field_build(pb, nb, minX, minZ, w, h, ax, az, 0, 0, 0, field, rt);
        dual_field_best(field, minX, minZ, w, h,
                        minX, minZ, minX + w - 1, minZ + h - 1, &xb, &zb);

        if (dual_start_seen(tried, &nTried, xa, za, xb, zb))
            continue; // identical start already refined to its fixed point

        int v = dual_refine(&s, &xa, &za, &xb, &zb);
        if (v > best) {
            best = v;
            bx1 = xa;
            bz1 = za;
            bx2 = xb;
            bz2 = zb;
        }
    }

    free(field);

    // report the honest union of the returned points; degrade to the single optimum if the pair
    // somehow violates the span limit or fails to beat it (defensive - partners are only ever
    // taken from safe rectangles, and the single optimum is span-valid by construction)
    DualCoverInfo cf;
    dual_cover_union_info(p, n, ax, az, bx1, bz1, bx2, bz2, &cf);
    if (!dual_cover_valid(&cf) || cf.count < singleArea) {
        bx1 = sx;
        bz1 = sz;
        bx2 = sx;
        bz2 = sz;
        cf.count = singleArea;
    }

    if (outX1) *outX1 = bx1;
    if (outZ1) *outZ1 = bz1;
    if (outX2) *outX2 = bx2;
    if (outZ2) *outZ2 = bz2;
    return cf.count;
}

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
    int *outSingleZ)
{
    return dual_solve(p, n, ax, az, 0, outX1, outZ1, outX2, outZ2,
                      outSingleArea, outSingleX, outSingleZ);
}

int monument_afk_coverage_dual_reaches(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int target)
{
    if (target <= 0)
        return 1;
    return dual_solve(p, n, ax, az, target, NULL, NULL, NULL, NULL, NULL, NULL, NULL) >= target;
}

// ==================== dummy-count-agnostic area ceiling ====================

// Largest spawning area the group can ever yield when the number of AFK dummies is unbounded
// but the farm-footprint constraint still holds: the counted blocks must fit max-min <= 256 per
// axis, i.e. inside some 257x257 block window (DUAL_SPAN_LIMIT + 1 columns). With unlimited
// dummies every block of every box inside such a window can be covered, and any feasible counted
// set fits some window, so the optimum is exactly the largest number of box blocks any 257x257
// window contains (multiset semantics: overlapping boxes count per box, like every other area
// in this file). Sliding a 2D prefix sum makes it O(W*H) with W,H <= 400 (a few 100k adds).
//
// This is a reference ceiling for the reported two-dummy area, not something reachable with the
// in-game one/two dummies: area_single <= area_dual <= monument_group_max_area <= n*ax*az.
// Centre of a footprint window [wx0, wx0+wWin) x [wz0, wz0+hWin): the point the single
// gate/portal links to, and the point AIO draws the 257x257 box around.
static void group_window_center(
    int wx0,
    int wz0,
    int wWin,
    int hWin,
    int *outX,
    int *outZ)
{
    if (outX) *outX = wx0 + wWin / 2;
    if (outZ) *outZ = wz0 + hWin / 2;
}

int monument_group_max_area(
    const MonumentBlockPos *p,
    int n,
    int ax,
    int az,
    int *outX,
    int *outZ)
{
    if (!p || n < 1 || n > 4 || ax <= 0 || az <= 0)
        return 0;

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
    const int w = maxX - minX + 1;
    const int h = maxZ - minZ + 1;
    const int total = n * ax * az;
    const int win = DUAL_SPAN_LIMIT + 1; // 257 columns: max-min <= 256

    // the whole group already fits one footprint: centre a 257x257 window on its bbox
    if (w <= win && h <= win) {
        const int winMinX = minX - (win - w) / 2;
        const int winMinZ = minZ - (win - h) / 2;
        group_window_center(winMinX, winMinZ, win, win, outX, outZ);
        return total;
    }

    int *grid = (int *) calloc((size_t) w * (size_t) h, sizeof(int));
    if (!grid)
        return 0; // unknown (callers treat 0 as "no information")

    for (int i = 0; i < n; i++) {
        const int bx = p[i].x + AFK_BOX_LO(ax) - minX;
        const int bz = p[i].z + AFK_BOX_LO(az) - minZ;
        for (int dz = 0; dz < az; dz++) {
            int *row = grid + (size_t) (bz + dz) * (size_t) w;
            for (int dx = 0; dx < ax; dx++)
                row[bx + dx]++;
        }
    }

    // 2D prefix sums in place (rows first, then columns)
    for (int z = 0; z < h; z++) {
        int *row = grid + (size_t) z * (size_t) w;
        for (int x = 1; x < w; x++)
            row[x] += row[x - 1];
    }
    for (int z = 1; z < h; z++) {
        int *row = grid + (size_t) z * (size_t) w;
        const int *prev = grid + (size_t) (z - 1) * (size_t) w;
        for (int x = 0; x < w; x++)
            row[x] += prev[x];
    }

    const int wWin = w < win ? w : win;
    const int hWin = h < win ? h : win;
    int best = 0;
    int bestX0 = 0, bestZ0 = 0;
    for (int z0 = 0; z0 + hWin <= h; z0++) {
        const int *row1 = grid + (size_t) (z0 + hWin - 1) * (size_t) w;
        const int *row0 = z0 > 0 ? grid + (size_t) (z0 - 1) * (size_t) w : NULL;
        for (int x0 = 0; x0 + wWin <= w; x0++) {
            const int x1 = x0 + wWin - 1;
            int s = row1[x1];
            if (x0 > 0)
                s -= row1[x0 - 1];
            if (row0) {
                s -= row0[x1];
                if (x0 > 0)
                    s += row0[x0 - 1];
            }
            if (s > best) {
                best = s;
                bestX0 = x0;
                bestZ0 = z0;
            }
        }
    }

    free(grid);

    // centre of the winning 257x257 footprint
    group_window_center(minX + bestX0, minZ + bestZ0, wWin, hWin, outX, outZ);
    return best;
}

// ==================== pairwise joint-coverage upper bound table ====================

// Structure positions are always 16-aligned, so pairwise block distances are multiples of 16 and <= 336 (group adjacency bound, see GROUP_LINK_DIST_SQ in monument_search.cpp); discretize as idx = dist/16.
#define PAIR_UB_STEPS 22
#define PAIR_UB_MAX_AREA (2 * 58 * 58)

static int g_pairUB[PAIR_UB_STEPS][PAIR_UB_STEPS];
static int g_pairUBReady = 0;

void monument_ensure_pair_upper_bounds(void)
{
    if (g_pairUBReady)
        return;

    const int box = 58;
    for (int i = 0; i < PAIR_UB_STEPS; i++) {
        for (int j = 0; j <= i; j++) {
            MonumentBlockPos p[2] = {{0, 0}, {i * 16, j * 16}};
            MonumentBlockPos best = {0, 0};
            int bestCnt = -1;
            // window: bounding rectangle of both box extents [pos-29, pos+28]
            int w = i * 16 + box;
            int h = j * 16 + box;
            afk_coverage_exact(p, 2, AFK_BOX_LO(box), AFK_BOX_LO(box), w, h,
                               box, box, &best, &bestCnt);
            // exact-scan maximum is the upper bound; fall back to the theoretical max on allocation failure to preserve the bound
            int ub = bestCnt > 0 ? bestCnt : PAIR_UB_MAX_AREA;
            g_pairUB[i][j] = ub;
            g_pairUB[j][i] = ub;
        }
    }
    g_pairUBReady = 1;
}

int monument_pair_upper_bound(int dxBlocks, int dzBlocks)
{
    if (!g_pairUBReady)
        return PAIR_UB_MAX_AREA;
    int i = dxBlocks / 16;
    int j = dzBlocks / 16;
    if (i >= PAIR_UB_STEPS)
        i = PAIR_UB_STEPS - 1;
    if (j >= PAIR_UB_STEPS)
        j = PAIR_UB_STEPS - 1;
    return g_pairUB[i][j];
}
