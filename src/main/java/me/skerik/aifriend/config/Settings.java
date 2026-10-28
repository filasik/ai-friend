package me.skerik.aifriend.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import me.skerik.aifriend.ai.provider.ProviderSettings;
import me.skerik.aifriend.storage.DbConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable snapshot of config.yml. Rules: every option is read here and nowhere else, every value is validated,
 * an invalid value falls back to the default and is logged (the plugin never refuses to start over a typo).
 */
public record Settings(
        String locale, boolean updateCheck, boolean metrics, List<String> aliases,
        boolean enabled, String chatbotName, String language, String personalization,
        int intervalSeconds, boolean stopWhenIgnored, int sleepMinutes, Chattiness chattiness,
        int maxQueuedMessages, int maxReplyLength, int connectTimeoutSeconds, int readTimeoutSeconds,
        ProviderSettings provider, ChatFeatures features, Storage storage
) {

    /** How eagerly the bot joins in when players talk among themselves (option {@code chattiness}). */
    public enum Chattiness {
        LOW, NORMAL, HIGH;

        static Chattiness parse(String raw, Logger log) {
            String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
            for (Chattiness c : values()) if (c.name().toLowerCase(Locale.ROOT).equals(v)) return c;
            log.warning("chattiness must be low, normal or high; using normal");
            return NORMAL;
        }
    }

    /** The shared {@code storage:} section (used by every feature that keeps data in a database). */
    public record Storage(String type, String sqliteFile, int sqlitePool, String host, int port, String database,
                          String user, String password, int poolSize, String sslMode) {

        public static final String DEFAULT_FILE = "ai-friend.db";

        public boolean mariadb() {
            return type.equals("mariadb");
        }

        public DbConfig dbConfig(Path dataFolder) {
            if (mariadb()) {
                return new DbConfig(DbConfig.Dialect.MARIADB, null, host, port, database, user, password, poolSize, sslMode);
            }
            return DbConfig.sqlite(dataFolder.resolve(sqliteFile), sqlitePool);
        }

        /** Short text for the banner and /aifriend status, never contains the password. */
        public String describe() {
            return mariadb() ? "MariaDB " + host + ":" + port + "/" + database : "SQLite " + sqliteFile;
        }

        public static Storage defaults() {
            return new Storage("sqlite", DEFAULT_FILE, 3, "localhost", 3306, "aifriend", "aifriend", "", 6, "");
        }
    }

    public static Settings load(FileConfiguration c, Logger log) {
        for (String stray : new String[] {"api-key", "openai_api_key"}) {
            if (!ProviderSettings.isUnset(c.getString(stray, ""))) {
                log.warning("The top-level " + stray + " in config.yml is no longer used (keys now live under "
                        + "provider.providers.<name>.api-key). Move it there if you need it and delete this line.");
            }
        }
        return new Settings(
                c.getString("locale", "en").toLowerCase(Locale.ROOT).strip(),
                c.getBoolean("update-check", true),
                c.getBoolean("metrics", true),
                c.getStringList("command.aliases").stream().map(String::strip).filter(a -> !a.isEmpty()).toList(),
                c.getBoolean("enabled", true),
                text(c, "chatbot-name", "AI Friend", log),
                text(c, "language", "English", log),
                c.getString("personalization", "").strip(),
                bounded(c, "interval-seconds", 5, 3600, 60, log),
                c.getBoolean("stop-when-ignored", true),
                bounded(c, "sleep-minutes", 1, 1440, 30, log),
                Chattiness.parse(c.getString("chattiness", "normal"), log),
                bounded(c, "max-queued-messages", 5, 500, 50, log),
                bounded(c, "max-reply-length", 20, 1000, 256, log),
                bounded(c, "http.connect-timeout-seconds", 1, 120, 10, log),
                bounded(c, "http.read-timeout-seconds", 1, 120, 30, log),
                ProviderSettings.load(c.getConfigurationSection("provider"), log),
                ChatFeatures.load(c, log),
                storage(c, log)
        );
    }

    private static Storage storage(FileConfiguration c, Logger log) {
        String type = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT).strip();
        if (type.equals("mysql")) type = "mariadb"; // same driver and dialect
        if (!type.equals("sqlite") && !type.equals("mariadb")) {
            log.warning("storage.type must be sqlite or mariadb; using sqlite");
            type = "sqlite";
        }
        String file = c.getString("storage.sqlite.file", Storage.DEFAULT_FILE).strip();
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..")) {
            log.warning("storage.sqlite.file must be a plain file name inside the plugin folder; using " + Storage.DEFAULT_FILE);
            file = Storage.DEFAULT_FILE;
        }
        ConfigurationSection m = c.getConfigurationSection("storage.mariadb");
        return new Storage(type, file,
                bounded(c, "storage.sqlite.pool-size", 1, 32, 3, log),
                m == null ? "localhost" : m.getString("host", "localhost").strip(),
                m == null ? 3306 : bounded(c, "storage.mariadb.port", 1, 65535, 3306, log),
                m == null ? "aifriend" : m.getString("database", "aifriend").strip(),
                m == null ? "aifriend" : m.getString("user", "aifriend").strip(),
                m == null ? "" : m.getString("password", ""),
                m == null ? 6 : bounded(c, "storage.mariadb.pool-size", 1, 64, 6, log),
                m == null ? "" : m.getString("ssl-mode", "").strip());
    }

    /** Key of the provider that answers, for status output: never the whole secret. */
    public String activeMaskedKey() {
        ProviderSettings.Entry primary = provider.providers().get(provider.primary());
        return primary == null || ProviderSettings.isUnset(primary.apiKey()) ? "-" : mask(primary.apiKey());
    }

    /** Model of the provider that answers ("-" when it has none, e.g. an unconfigured custom server). */
    public String activeModel() {
        ProviderSettings.Entry primary = provider.providers().get(provider.primary());
        return primary == null || primary.model().isEmpty() ? "-" : primary.model();
    }

    static String mask(String key) {
        if (key.isEmpty()) return "-";
        if (key.length() <= 10) return "****";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
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
