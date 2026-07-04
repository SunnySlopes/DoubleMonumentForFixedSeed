package project;

/**
 * Quick JNI smoke test (run after buildNative).
 */
public final class MonumentNativeSmokeTest {

    public static void main(String[] args) {
        MonumentFinderBridge.resetSearchState();
        int[] raw = MonumentFinderBridge.monumentSearch(
                1L,
                -2,
                2,
                -2,
                2,
                MonumentFinderBridge.MC_1_21,
                2,
                false);
        if (raw == null) {
            System.out.println("No pairs (ok if none in range).");
        } else {
            I18n.initFromSystemDefault();
            for (int i = 0; i + MonumentFinderBridge.PAIR_STRIDE <= raw.length; i += MonumentFinderBridge.PAIR_STRIDE) {
                int hangX = raw[i];
                int hangZ = raw[i + 1];
                int dx = raw[i + 2];
                int dz = raw[i + 3];
                boolean low = raw[i + 4] != 0;
                String type = MonumentFinderBridge.oceanTypeLabel(1L, MonumentFinderBridge.MC_1_21, hangX, hangZ);
                System.out.println(I18n.formatPair(hangX, hangZ, dx, dz, low, type));
            }
        }
        int[] progress = MonumentFinderBridge.getSearchProgress();
        if (progress != null) {
            System.out.printf("Progress: %d / %d status=%d%n", progress[0], progress[1], progress[2]);
        }
        System.out.println("JNI smoke test OK.");
    }
}
