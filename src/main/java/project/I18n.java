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
     * Formats a result line: x/z output point, count group size (2/3/4), area usable blocks,
     * dx/dz unused (formatter compatibility), oceanTypeKey nullable biome type.
     */
    public static String formatResult(int x, int z, int count, int area, int dx, int dz, String oceanTypeKey) {
        StringBuilder info = new StringBuilder();
        if (oceanTypeKey != null && !oceanTypeKey.isEmpty()) {
            info.append("type=").append(get("result.oceanType." + oceanTypeKey)).append(' ');
        }
        info.append(formatArea(area));
        MessageFormat mf;
        Object[] args;
        if (count == 2) {
            // Use Locale.ROOT so coordinates never get thousand-separator commas (e.g. -1,024).
            mf = new MessageFormat(messages.getString("result.tpFormat"), Locale.ROOT);
            args = new Object[] {x, z, info.toString()};
        } else {
            mf = new MessageFormat(messages.getString("result.tpFormatGroup"), Locale.ROOT);
            args = new Object[] {x, z, info.toString(), String.valueOf(count)};
        }
        return mf.format(args);
    }

    private static String formatArea(int area) {
        MessageFormat mf = new MessageFormat(messages.getString("result.area"), Locale.ROOT);
        return mf.format(new Object[] {area});
    }
}
