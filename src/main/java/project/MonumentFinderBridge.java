package project;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JNI bridge for monument pair search (cubiomes).
 */
public final class MonumentFinderBridge {

    private static boolean loaded = false;

    public static final int MC_1_18 = 22;
    public static final int MC_1_19 = 24;
    public static final int MC_1_20 = 25;
    /** MC_1_21_1 in cubiomes (xpple fork) */
    public static final int MC_1_21_1 = 26;
    /** MC_1_21_1 alias */
    public static final int MC_1_21 = MC_1_21_1;
    public static final int MC_26_1 = 33;
    public static final int MC_26_2 = 34;

    public static final String[] SUPPORTED_VERSION_LABELS = {
            "26.2", "26.1", "1.21.1", "1.20.1", "1.19.2", "1.18.2"
    };

    /** ints per pair result: hangX, hangZ, dx, dz, lowEfficiencyFlag */
    public static final int PAIR_STRIDE = 5;

    static {
        loadNativeLibrary();
    }

    private MonumentFinderBridge() {}

    private static void loadNativeLibrary() {
        if (loaded) return;
        try {
            System.loadLibrary("monumentFinderLibJ");
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            UnsatisfiedLinkError firstError = e;
            if (tryLoadFromBundledResources()) {
                loaded = true;
                return;
            }
            Path base = Paths.get(System.getProperty("user.dir", "."));
            List<String> libNames = getNativeLibNamesForCurrentOs();
            Path[] candidates = {
                base.resolve("native"),
                base.resolve("build").resolve("native"),
                base.resolve("build"),
            };
            for (Path dir : candidates) {
                for (String libName : libNames) {
                    File f = dir.resolve(libName).toFile();
                    if (f.exists()) {
                        System.load(f.getAbsolutePath());
                        loaded = true;
                        return;
                    }
                }
            }
            throw new UnsatisfiedLinkError(
                "monumentFinderLibJ native library not found. Build with: gradlew buildNative. "
                    + firstError.getMessage());
        }
    }

    private static boolean tryLoadFromBundledResources() {
        String osFolder = getOsResourceFolder();
        if (osFolder == null) return false;
        for (String libName : getNativeLibNamesForCurrentOs()) {
            String resourcePath = "/native/" + osFolder + "/" + libName;
            if (tryLoadSingleBundledResource(resourcePath, libName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryLoadSingleBundledResource(String resourcePath, String libName) {
        try (InputStream is = MonumentFinderBridge.class.getResourceAsStream(resourcePath)) {
            if (is == null) return false;
            Path tempDir = Files.createTempDirectory("monumentfinder-native-");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            System.load(tempLib.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static String getOsResourceFolder() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        if (os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("linux")) return "linux";
        return null;
    }

    private static List<String> getNativeLibNamesForCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> names = new ArrayList<>();
        if (os.contains("win")) {
            names.add("monumentFinderLibJ.dll");
            names.add("libmonumentFinderLibJ.dll");
        } else if (os.contains("mac")) {
            names.add("libmonumentFinderLibJ.dylib");
        } else {
            names.add("libmonumentFinderLibJ.so");
        }
        return names;
    }

    public static int mcFromVersionString(String versionString) {
        if (versionString == null) return MC_26_2;
        return switch (versionString) {
            case "1.18.2" -> MC_1_18;
            case "1.19.2" -> MC_1_19;
            case "1.20.1" -> MC_1_20;
            case "1.21.1" -> MC_1_21_1;
            case "26.1" -> MC_26_1;
            case "26.2" -> MC_26_2;
            default -> MC_26_2;
        };
    }

    public static native int[] monumentSearch(
            long seed,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int mc,
            int numThreads,
            boolean pauseOnPairFound);

    /** @return [processed, total, status] or null */
    public static native int[] getSearchProgress();

    public static native int[] getNowResult();

    public static native boolean pause();

    public static native boolean resume();

    public static native boolean stop();

    public static native void resetSearchState();

    /** @return 0=others, 1=cold, 2=frozen ocean type at pair center */
    public static native int classifyOceanType(long seed, int mc, int centerX, int centerZ);

    public static String oceanTypeLabel(long seed, int mc, int centerX, int centerZ) {
        try {
            return switch (classifyOceanType(seed, mc, centerX, centerZ)) {
                case 1 -> "cold";
                case 2 -> "frozen";
                default -> "others";
            };
        } catch (UnsatisfiedLinkError e) {
            return "others";
        }
    }
}
