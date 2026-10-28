package me.skerik.aifriend.chat;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/** Null-safe, validate-with-fallback readers for the settings records of this package (same style as config/Settings). */
public final class Cfg {

    private Cfg() {
    }

    public static boolean bool(@Nullable ConfigurationSection s, String path, boolean fallback) {
        return s == null ? fallback : s.getBoolean(path, fallback);
    }

    public static int bounded(@Nullable ConfigurationSection s, String path, int min, int max, int fallback, Logger log) {
        if (s == null) return fallback;
        int value = s.getInt(path, fallback);
        if (value < min || value > max) {
            log.warning(s.getCurrentPath() + "." + path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    public static double boundedDouble(@Nullable ConfigurationSection s, String path, double min, double max, double fallback,
                                Logger log) {
        if (s == null) return fallback;
        double value = s.getDouble(path, fallback);
        if (Double.isNaN(value) || value < min || value > max) {
            log.warning(s.getCurrentPath() + "." + path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }

    /** Non-blank, stripped strings; blanks are dropped. */
    public static List<String> strings(@Nullable ConfigurationSection s, String path) {
        if (s == null) return List.of();
        return s.getStringList(path).stream().map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    public static String string(@Nullable ConfigurationSection s, String path, String fallback) {
        if (s == null) return fallback;
        String v = s.getString(path, fallback);
        return v == null ? fallback : v.strip();
    }
}
