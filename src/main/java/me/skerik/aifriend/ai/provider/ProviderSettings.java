package me.skerik.aifriend.ai.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable snapshot of the {@code provider:} section of config.yml. Same rules as {@code config/Settings}: every
 * value is validated, an invalid one falls back to the default and is logged. The five built-in names
 * (proxy, openai, anthropic, ollama, custom) always exist with defaults; {@code providers.<name>} overrides them, and
 * additional names can be declared with an explicit {@code type}.
 */
public record ProviderSettings(
        String primary, Map<String, Entry> providers, List<String> failover,
        boolean cacheEnabled, int cacheMaxEntries, int cacheTtlMinutes, int failoverCooldownSeconds
) {

    public enum Type {
        PROXY, OPENAI, ANTHROPIC, OLLAMA, CUSTOM;

        static Type parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** One configured provider. {@code model}, {@code baseUrl} and {@code apiKey} may be empty where the type allows. */
    public record Entry(String name, Type type, String model, String baseUrl, String apiKey, int maxOutputTokens) {
    }

    public static final String DEFAULT_PRIMARY = "proxy";
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 200;

    public ProviderSettings {
        providers = Map.copyOf(providers);
        failover = List.copyOf(failover);
    }

    /** Max output tokens of the primary provider (what ChatBot should put into {@link ProviderRequest}). */
    public int maxOutputTokens() {
        Entry e = providers.get(primary);
        return e == null ? DEFAULT_MAX_OUTPUT_TOKENS : e.maxOutputTokens();
    }

    /** All defaults, as when the {@code provider:} section is missing. */
    public static ProviderSettings defaults() {
        return load(null, Logger.getAnonymousLogger());
    }

    /** @param section the {@code provider} section; null/missing means all defaults */
    public static ProviderSettings load(ConfigurationSection section, Logger log) {
        ConfigurationSection root = section;
        ConfigurationSection configured = root == null ? null : root.getConfigurationSection("providers");

        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Type t : Type.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            entries.put(name, entry(name, t, configured == null ? null : configured.getConfigurationSection(name), log));
        }
        if (configured != null) {
            for (String name : configured.getKeys(false)) {
                String key = name.strip().toLowerCase(Locale.ROOT);
                if (entries.containsKey(key)) continue;
                ConfigurationSection s = configured.getConfigurationSection(name);
                Type type = s == null ? null : Type.parse(s.getString("type"));
                if (type == null) {
                    log.warning("provider.providers." + name + ".type must be one of proxy, openai, anthropic, ollama, "
                            + "custom; ignoring this provider");
                    continue;
                }
                entries.put(key, entry(key, type, s, log));
            }
        }

        String primary = root == null ? DEFAULT_PRIMARY : root.getString("primary", DEFAULT_PRIMARY).strip()
                .toLowerCase(Locale.ROOT);
        if (!entries.containsKey(primary)) {
            log.warning("provider.primary \"" + primary + "\" is not a known provider; using \"" + DEFAULT_PRIMARY + "\"");
            primary = DEFAULT_PRIMARY;
        }

        List<String> failover = new ArrayList<>();
        if (root != null) {
            for (String raw : root.getStringList("failover")) {
                String name = raw.strip().toLowerCase(Locale.ROOT);
                if (!entries.containsKey(name)) {
                    log.warning("provider.failover: unknown provider \"" + name + "\" ignored");
                } else if (name.equals(primary) || failover.contains(name)) {
                    log.warning("provider.failover: \"" + name + "\" is already in the chain and was ignored");
                } else {
                    failover.add(name);
                }
            }
        }

        ConfigurationSection cache = root == null ? null : root.getConfigurationSection("cache");
        return new ProviderSettings(primary, entries, failover,
                cache != null && cache.getBoolean("enabled", false),
                bounded(cache, "provider.cache.max-entries", "max-entries", 1, 10_000, 200, log),
                bounded(cache, "provider.cache.ttl-minutes", "ttl-minutes", 1, 1440, 10, log),
                bounded(root, "provider.failover-cooldown-seconds", "failover-cooldown-seconds", 5, 3600, 60, log));
    }

    private static Entry entry(String name, Type type, ConfigurationSection s, Logger log) {
        String path = "provider.providers." + name;
        String model = s == null ? "" : s.getString("model", "").strip();
        if (model.isEmpty()) model = defaultModel(type);

        String defaultUrl = defaultBaseUrl(type);
        String baseUrl = s == null ? "" : s.getString("base-url", "").strip();
        if (baseUrl.isEmpty()) {
            baseUrl = defaultUrl;
        } else if (!validUrl(baseUrl)) {
            log.warning(path + ".base-url must be an http(s) URL; using " + (defaultUrl.isEmpty() ? "none" : defaultUrl));
            baseUrl = defaultUrl;
        }

        String key = s == null ? "" : s.getString("api-key", "").strip();
        if (isUnset(key)) key = "";

        return new Entry(name, type, model, baseUrl, key,
                bounded(s, path + ".max-output-tokens", "max-output-tokens", 16, 4000, DEFAULT_MAX_OUTPUT_TOKENS, log));
    }

    /** Blank or a "your_..._here" style placeholder means "not set". */
    public static boolean isUnset(String key) {
        return key == null || key.isBlank() || key.matches("(?i)your_.*_here");
    }

    static String defaultModel(Type type) {
        return switch (type) {
            case OPENAI -> "gpt-4o-mini";
            case ANTHROPIC -> "claude-haiku-5-5";
            case OLLAMA -> "llama3.2";
            case PROXY, CUSTOM -> "";
        };
    }

    static String defaultBaseUrl(Type type) {
        return switch (type) {
            case OPENAI -> OpenAiProvider.DEFAULT_BASE_URL;
            case ANTHROPIC -> AnthropicProvider.DEFAULT_BASE_URL;
            case OLLAMA -> OllamaProvider.DEFAULT_BASE_URL;
            case PROXY, CUSTOM -> "";
        };
    }

    private static boolean validUrl(String s) {
        try {
            java.net.URI u = java.net.URI.create(s);
            return u.getHost() != null && ("http".equals(u.getScheme()) || "https".equals(u.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int bounded(ConfigurationSection s, String fullPath, String key, int min, int max, int fallback,
                               Logger log) {
        if (s == null) return fallback;
        int value = s.getInt(key, fallback);
        if (value < min || value > max) {
            log.warning(fullPath + " must be between " + min + " and " + max + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
