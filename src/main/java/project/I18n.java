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

    public static String formatPair(int hangX, int hangZ, int dx, int dz, boolean lowEfficiency) {
        // Use Locale.ROOT so coordinates never get thousand-separator commas (e.g. -1,024).
        MessageFormat mf = new MessageFormat(messages.getString("result.tpFormat"), Locale.ROOT);
        String base = mf.format(new Object[] {hangX, hangZ, dx, dz});
        if (lowEfficiency) {
            return base + get("result.lowEfficiencySuffix");
        }
        return base;
    }
}
