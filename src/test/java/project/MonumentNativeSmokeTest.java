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
            System.out.println(MonumentFinderBridge.formatResults(raw));
        }
        int[] progress = MonumentFinderBridge.getSearchProgress();
        if (progress != null) {
            System.out.printf("Progress: %d / %d status=%d%n", progress[0], progress[1], progress[2]);
        }
        System.out.println("JNI smoke test OK.");
    }
}
