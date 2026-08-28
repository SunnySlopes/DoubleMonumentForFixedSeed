package project;

import java.util.HashSet;
import java.util.Set;

/**
 * Quick JNI smoke test (run after buildNative).
 * Optional args: seed minX maxX minZ maxZ pairLevel biomeCheck minGroupArea
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
                minGroupArea);
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
        I18n.initFromSystemDefault();
        Set<String> seen = new HashSet<>();
        int dups = 0;
        for (int i = 0; i + MonumentFinderBridge.PAIR_STRIDE <= raw.length; i += MonumentFinderBridge.PAIR_STRIDE) {
            int x = raw[i];
            int z = raw[i + 1];
            int dx = raw[i + 2];
            int dz = raw[i + 3];
            int count = raw[i + 4];
            int area = raw[i + 5];
            if (!seen.add(x + "," + z + "," + count)) {
                dups++;
                System.out.println("DUPLICATE: " + x + "," + z + " count=" + count);
            }
            String type = MonumentFinderBridge.oceanTypeLabel(seed, MonumentFinderBridge.MC_1_21, x, z);
            System.out.println(I18n.formatResult(x, z, count, area, dx, dz, type));
        }
        System.out.println("hits=" + seen.size() + " duplicates=" + dups);
    }
}
