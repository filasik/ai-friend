package me.skerik.aifriend.config;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable snapshot of config.yml. Rules: every option is read here and nowhere else, every value is validated,
 * an invalid value falls back to the default and is logged (the plugin never refuses to start over a typo).
 */
public record Settings(
        String locale, boolean updateCheck, boolean metrics, List<String> aliases,
        boolean enabled, String apiKey, String model, String chatbotName, String language, String personalization,
        int intervalSeconds, boolean stopWhenIgnored, int sleepMinutes,
        int maxQueuedMessages, int maxReplyLength, int connectTimeoutSeconds, int readTimeoutSeconds
) {

    public static final String KEY_PLACEHOLDER = "your_openai_api_key_here";

    public static Settings load(FileConfiguration c, Logger log) {
        return new Settings(
                c.getString("locale", "en").toLowerCase(Locale.ROOT).strip(),
                c.getBoolean("update-check", true),
                c.getBoolean("metrics", true),
                c.getStringList("command.aliases").stream().map(String::strip).filter(a -> !a.isEmpty()).toList(),
                c.getBoolean("enabled", true),
                c.getString("api-key", KEY_PLACEHOLDER).strip(),
                text(c, "model", "gpt-4o-mini", log),
                text(c, "chatbot-name", "AI Friend", log),
                text(c, "language", "English", log),
                c.getString("personalization", "").strip(),
                bounded(c, "interval-seconds", 5, 3600, 60, log),
                c.getBoolean("stop-when-ignored", true),
                bounded(c, "sleep-minutes", 1, 1440, 30, log),
                bounded(c, "max-queued-messages", 5, 500, 50, log),
                bounded(c, "max-reply-length", 20, 1000, 256, log),
                bounded(c, "http.connect-timeout-seconds", 1, 120, 10, log),
                bounded(c, "http.read-timeout-seconds", 1, 120, 30, log)
        );
    }

    /** True when the admin has put a key in (the shipped placeholder and an empty value mean "not set"). */
    public boolean hasApiKey() {
        return !apiKey.isEmpty() && !apiKey.equals(KEY_PLACEHOLDER);
    }

    /** The key for status output: never the whole secret. {@code trial} is not a secret. */
    public String maskedKey() {
        if (!hasApiKey()) return "-";
        if (apiKey.equals("trial")) return "trial";
        if (apiKey.length() <= 10) return "****";
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    /** A non-blank text option; a blank value is logged and replaced by the default. */
    static String text(FileConfiguration c, String path, String fallback, Logger log) {
        String value = c.getString(path, fallback).strip();
        if (value.isEmpty()) {
            log.warning(path + " must not be empty; using \"" + fallback + "\"");
            return fallback;
        }
        return value;
    }

    /** An int option inside [min, max]; anything else is logged and replaced by the default. */
    static int bounded(FileConfiguration c, String path, int min, int max, int fallback, Logger log) {
        int value = c.getInt(path, fallback);
        if (value < min || value > max) {
            log.warning(path + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
