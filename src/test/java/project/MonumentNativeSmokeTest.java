package project;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Quick JNI smoke test (run after buildNative).
 * Optional args: seed minX maxX minZ maxZ pairLevel biomeCheck minGroupArea searchMode
 */
public final class MonumentNativeSmokeTest {

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1L;
        int minX = args.length > 1 ? Integer.parseInt(args[1]) : -2;
        int maxX = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        int minZ = args.length > 3 ? Integer.parseInt(args[3]) : -2;
        int maxZ = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        int pairLevel = args.length > 5 ? Integer.parseInt(args[5]) : 2;
        boolean biomeCheck = args.length <= 6 || Boolean.parseBoolean(args[6]);
        // 0 = built-in default (2*58*58); only effective for pairLevel >= 3
        int minGroupArea = args.length > 7 ? Integer.parseInt(args[7]) : 0;
        // search modes for 3/4 groups, bitmask: 1 = single dummy, 2 = dual dummy, 4 = single gate
        int searchModes = args.length > 8 ? Integer.parseInt(args[8]) : 1;
        // area the threshold applies to: 0 = single, 1 = dual, 2 = gate
        int filterMode = args.length > 9 ? Integer.parseInt(args[9]) : 0;

        MonumentFinderBridge.resetSearchState();
        long t0 = System.currentTimeMillis();
        int[] raw = MonumentFinderBridge.monumentSearch(
                seed,
                minX,
                maxX,
                minZ,
                maxZ,
                MonumentFinderBridge.MC_1_21,
                Runtime.getRuntime().availableProcessors(),
                false,
                pairLevel,
                biomeCheck,
                minGroupArea,
                searchModes,
                filterMode);
        long ms = System.currentTimeMillis() - t0;
        printHits(seed, raw);
        long[] progress = MonumentFinderBridge.getSearchProgress();
        if (progress != null) {
            System.out.printf("Progress: %d / %d status=%d (%d ms)%n",
                    progress[0], progress[1], progress[2], ms);
        }
        System.out.println("JNI smoke test OK.");
    }

    private static void printHits(long seed, int[] raw) {
        if (raw == null) {
            System.out.println("No hits (ok if none in range).");
            return;
        }
        Set<String> seen = new HashSet<>();
        int dups = 0;
        // CSV columns: x1,z1,x2,z2,area_dual,area_single,area_max,gate_x,gate_z,sx,sz,count,dx,dz,dual
        System.out.println("x1,z1,x2,z2,area_dual,area_single,area_max,gate_x,gate_z,sx,sz,count,dx,dz,dual");
        for (int i = 0; i + MonumentFinderBridge.PAIR_STRIDE <= raw.length; i += MonumentFinderBridge.PAIR_STRIDE) {
            int x = raw[i];
            int z = raw[i + 1];
            int x2 = raw[i + 2];
            int z2 = raw[i + 3];
            int sx = raw[i + 4];
            int sz = raw[i + 5];
            int dx = raw[i + 6];
            int dz = raw[i + 7];
            int count = raw[i + 8];
            int area = raw[i + 9];
            int singleArea = raw[i + 10];
            int areaMax = raw[i + 11];
            int gateX = raw[i + 12] >> 3;
            int gateZ = raw[i + 13] >> 3;
            int dual = raw[i + 14];
            // a true re-emission repeats every field; dual groups may share the first dummy
            if (!seen.add(x + "," + z + "," + x2 + "," + z2 + "," + count + "," + area)) {
                dups++;
                System.out.println("DUPLICATE: " + x + "," + z + " count=" + count);
            }
            System.out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    x, z, x2, z2, area, singleArea, areaMax, gateX, gateZ, sx, sz, count, dx, dz, dual);
        }
        System.out.println("hits=" + seen.size() + " duplicates=" + dups);
    }
}
