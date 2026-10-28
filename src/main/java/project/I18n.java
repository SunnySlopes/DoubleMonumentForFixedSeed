package project;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * UI message bundle (zh_CN / en_US).
 */
public final class I18n {

    private static Locale currentLocale = Locale.US;
    private static ResourceBundle messages = ResourceBundle.getBundle("messages", currentLocale);

    private I18n() {}

    public static void initFromSystemDefault() {
        Locale systemLocale = Locale.getDefault();
        if (systemLocale.getLanguage().equals("zh")) {
            String country = systemLocale.getCountry().toLowerCase(Locale.ROOT);
            if (country.equals("cn") || country.equals("hk") || country.equals("tw")) {
                setLocale(new Locale("zh", "CN"));
                return;
            }
        }
        setLocale(new Locale("en", "US"));
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    public static void setLocale(Locale locale) {
        try {
            currentLocale = locale;
            messages = ResourceBundle.getBundle("messages", currentLocale);
        } catch (Exception e) {
            currentLocale = new Locale("en", "US");
            messages = ResourceBundle.getBundle("messages", currentLocale);
        }
    }

    public static String get(String key) {
        try {
            return messages.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    public static String get(String key, Object... args) {
        try {
            return MessageFormat.format(messages.getString(key), args);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Builds one result record (cells in {@link ResultTable#KEYS} order, tab separated).
     *
     * <p>Cells: seed, optimal single-dummy point, optimal single-dummy area, ocean type,
     * group size (2/3/4), dummy 1 point, dummy 2 point (dual mode only, "-" otherwise), reported
     * dual-dummy area (dual mode only, "-" otherwise), single-gate area ceiling, nether portal
     * coordinates of that ceiling (overworld centre / 8). Points are plain "x z" coordinates;
     * there are no {@code /tp} prefixes, labels or brackets - the header row carries the names.</p>
     *
     * @param oceanTypeKey nullable biome type key (null/empty renders as "-")
     * @param areaMaxX     overworld centre of the ceiling footprint (portal / gate location)
     * @param areaMaxZ     overworld centre of the ceiling footprint (portal / gate location)
     */
    public static String formatRow(long seed, int x, int z, int x2, int z2, int sx, int sz,
                                   int count, int area, int singleArea, int areaMax,
                                   int areaMaxX, int areaMaxZ, boolean dual,
                                   String oceanTypeKey) {
        String ocean = (oceanTypeKey == null || oceanTypeKey.isEmpty())
                ? "-" : get("result.oceanType." + oceanTypeKey);
        String singlePoint = sx + " " + sz;
        String d1 = x + " " + z;
        String d2 = dual ? x2 + " " + z2 : "-";
        String dualArea = dual ? String.valueOf(area) : "-";
        // nether side of the single gate: overworld coordinates / 8 (floor division)
        String gate = (areaMaxX >> 3) + " " + (areaMaxZ >> 3);
        return String.join("\t", String.valueOf(seed), singlePoint, String.valueOf(singleArea),
                ocean, String.valueOf(count), d1, d2, dualArea, String.valueOf(areaMax), gate);
    }
}
