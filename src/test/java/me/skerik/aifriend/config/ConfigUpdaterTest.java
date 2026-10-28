package me.skerik.aifriend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** An old config.yml must gain the new keys (with comments) and keep every value and comment the admin wrote. */
class ConfigUpdaterTest {

    /** A config from "version 0": only what the admin changed, plus their own comment. */
    private static final String OLD = """
            # my own note
            locale: cs   # czech
            update-check: false
            """;

    /** A realistic AI Friend 1.x config. */
    private static final String LEGACY = """
            openai_api_key: sk-test123456789
            enabled: true
            player_dissinterest: false
            timer_interval: 120
            gpt_version: gpt-4o
            chatbot_name: Bob
            preffered_language: Czech
            personalization: "hi"
            """;

    private static YamlConfiguration bundled() throws Exception {
        try (InputStream in = ConfigUpdaterTest.class.getResourceAsStream("/config.yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return y;
        }
    }

    private static YamlConfiguration load(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void addsMissingKeysKeepsUserValuesAndComments() throws Exception {
        YamlConfiguration user = load(OLD);
        YamlConfiguration defaults = bundled();
        assertFalse(user.contains("sleep-minutes", true));
        assertFalse(user.contains("http.read-timeout-seconds", true));

        int added = ConfigUpdater.merge(user, defaults);

        assertTrue(added > 0);
        assertEquals("cs", user.getString("locale"));
        assertFalse(user.getBoolean("update-check"));
        assertTrue(user.contains("sleep-minutes", true));
        assertTrue(user.contains("http.read-timeout-seconds", true));
        assertEquals(defaults.getInt(ConfigUpdater.VERSION_KEY), user.getInt(ConfigUpdater.VERSION_KEY));

        String out = user.saveToString();
        assertTrue(out.contains("# my own note"), out);
        assertTrue(out.contains("How the bot is named in chat"), out); // the comment of a new key came along

        // a second pass changes nothing
        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());
    }

    @Test
    void legacyOneDotXConfigIsMigratedToTheNewKeys() throws Exception {
        YamlConfiguration user = load(LEGACY);
        YamlConfiguration defaults = bundled();

        ConfigUpdater.merge(user, defaults);
        int moved = ConfigUpdater.migrate(user, ConfigUpdater.RENAMES);

        assertEquals(ConfigUpdater.RENAMES.size(), moved);
        assertFalse(user.getBoolean("stop-when-ignored"));
        assertEquals(120, user.getInt("interval-seconds"));
        assertEquals("Bob", user.getString("chatbot-name"));
        assertEquals("Czech", user.getString("language"));
        assertTrue(user.getBoolean("enabled"));
        assertEquals("hi", user.getString("personalization"));
        for (String old : ConfigUpdater.RENAMES.keySet()) {
            assertFalse(user.contains(old, true), old + " should be gone");
        }
        assertTrue(user.contains("sleep-minutes", true));
        assertTrue(user.contains("http.connect-timeout-seconds", true));

        String out = user.saveToString();
        assertTrue(out.contains("How the bot is named in chat"), out);

        // a second pass changes nothing
        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(0, ConfigUpdater.migrate(again, ConfigUpdater.RENAMES));
        assertEquals(out, again.saveToString());

        Logger log = Logger.getLogger("ConfigUpdaterTest");
        log.setLevel(Level.OFF);
        Settings s = Settings.load(again, log);
        assertTrue(s.enabled());
        assertFalse(s.stopWhenIgnored());
        assertEquals(120, s.intervalSeconds());
        assertEquals("Bob", s.chatbotName());
        assertEquals("Czech", s.language());
        assertEquals("hi", s.personalization());
    }

    @Test
    void migrateWithoutOldKeysChangesNothing() throws Exception {
        YamlConfiguration user = load(OLD);
        assertEquals(0, ConfigUpdater.migrate(user, ConfigUpdater.RENAMES));
    }

    @Test
    void bundledConfigDeclaresAVersion() throws Exception {
        assertEquals(5, bundled().getInt(ConfigUpdater.VERSION_KEY, 0));
    }

    private static final java.util.List<String> NEW_SECTIONS = java.util.List.of("provider", "personas", "memory",
            "limits", "cost", "reply-filter", "mentions", "opt-out", "events", "storage", "long-term-memory",
            "server-context", "mood", "relationships", "boredom", "supervision");

    /** What a 2.0.0 install wrote: config-version 1, only the 2.0.0 keys, one admin comment. */
    private static final String V200 = """
            config-version: 1
            locale: cs
            update-check: false
            command:
              aliases: [friend]
            metrics: true
            enabled: true
            # my key note
            api-key: sk-abcdefghijkl
            model: gpt-4o
            chatbot-name: Bob
            language: Czech
            personalization: "hello"
            interval-seconds: 120
            stop-when-ignored: false
            sleep-minutes: 10
            max-queued-messages: 20
            max-reply-length: 100
            http:
              connect-timeout-seconds: 5
              read-timeout-seconds: 15
            """;

    private static void assertNewSectionsWithComments(YamlConfiguration user, String out) {
        for (String section : NEW_SECTIONS) {
            assertTrue(user.isConfigurationSection(section), section + " missing");
            assertFalse(user.getComments(section).isEmpty(), section + " has no comment");
        }
        assertEquals(5, user.getInt(ConfigUpdater.VERSION_KEY));
        assertTrue(out.contains("Conversation memory"), out);
        assertTrue(out.contains("Rate limits"), out);
    }

    @Test
    void oneDotXConfigGetsTheNewSections() throws Exception {
        YamlConfiguration user = load(LEGACY);
        YamlConfiguration defaults = bundled();
        ConfigUpdater.merge(user, defaults);
        ConfigUpdater.migrate(user, ConfigUpdater.RENAMES);
        String out = user.saveToString();
        assertNewSectionsWithComments(user, out);
        assertEquals(2, user.getMapList("personas.list").size());
        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());
    }

    @Test
    void twoDotZeroConfigGetsTheNewSectionsAndKeepsEveryValue() throws Exception {
        YamlConfiguration user = load(V200);
        YamlConfiguration defaults = bundled();
        int added = ConfigUpdater.merge(user, defaults);
        assertTrue(added > 0);
        String out = user.saveToString();
        assertNewSectionsWithComments(user, out);
        assertTrue(out.contains("# my key note"), out);
        assertEquals("cs", user.getString("locale"));
        assertFalse(user.getBoolean("update-check"));
        assertEquals(java.util.List.of("friend"), user.getStringList("command.aliases"));
        assertEquals("sk-abcdefghijkl", user.getString("api-key"), "the old value is never touched or deleted");
        assertEquals("Bob", user.getString("chatbot-name"));
        assertEquals("Czech", user.getString("language"));
        assertEquals("hello", user.getString("personalization"));
        assertEquals(120, user.getInt("interval-seconds"));
        assertFalse(user.getBoolean("stop-when-ignored"));
        assertEquals(10, user.getInt("sleep-minutes"));
        assertEquals(20, user.getInt("max-queued-messages"));
        assertEquals(100, user.getInt("max-reply-length"));
        assertEquals(5, user.getInt("http.connect-timeout-seconds"));
        assertEquals(15, user.getInt("http.read-timeout-seconds"));

        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());

        Logger log = Logger.getLogger("ConfigUpdaterTest");
        log.setLevel(Level.OFF);
        Settings s = Settings.load(again, log);
        assertEquals(120, s.intervalSeconds());
        assertEquals(2, s.features().personas().personas().size());
    }

    @Test
    void adminListValuesSurviveTheUpdate() throws Exception {
        String text = V200 + """
                personas:
                  enabled: true
                  list:
                    - name: Mine
                      prompt: "Be mine"
                """;
        YamlConfiguration user = load(text);
        ConfigUpdater.merge(user, bundled());
        assertTrue(user.getBoolean("personas.enabled"));
        var list = user.getMapList("personas.list");
        assertEquals(1, list.size());
        assertEquals("Mine", list.get(0).get("name"));
        YamlConfiguration again = load(user.saveToString());
        assertEquals(1, again.getMapList("personas.list").size());
        assertEquals(0, ConfigUpdater.merge(again, bundled()));
    }

    /** What a 2.1.0 install (config-version 2, everything before long-term memory) has on disk. */
    private static final String V210 = """
            config-version: 2
            locale: cs
            api-key: sk-abcdefghijkl
            # my provider note
            provider:
              primary: openai
              providers:
                openai:
                  api-key: sk-openai-abcdefgh
            memory:
              enabled: true
              exchanges: 9
            """;

    @Test
    void twoDotOneConfigGetsStorageAndLongTermMemoryWithoutTouchingAnything() throws Exception {
        YamlConfiguration user = load(V210);
        YamlConfiguration defaults = bundled();
        assertTrue(ConfigUpdater.merge(user, defaults) > 0);
        String out = user.saveToString();
        assertEquals(5, user.getInt(ConfigUpdater.VERSION_KEY));
        assertTrue(out.contains("# my provider note"), out);
        assertEquals("openai", user.getString("provider.primary"));
        assertEquals("sk-openai-abcdefgh", user.getString("provider.providers.openai.api-key"));
        assertEquals(9, user.getInt("memory.exchanges"));
        assertEquals("sqlite", user.getString("storage.type"));
        assertEquals("ai-friend.db", user.getString("storage.sqlite.file"));
        assertEquals(6, user.getInt("storage.mariadb.pool-size"));
        assertFalse(user.getBoolean("long-term-memory.enabled"));
        assertEquals("none", user.getString("long-term-memory.embeddings.backend"));
        assertFalse(user.getComments("long-term-memory").isEmpty());
        assertTrue(out.contains("PRIVACY"), out);
        assertTrue(out.contains("self-signed") || out.contains("Self signed"), out);

        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());

        Logger log = Logger.getLogger("ConfigUpdaterTest");
        log.setLevel(Level.OFF);
        Settings s = Settings.load(again, log);
        assertFalse(s.features().longMemory().enabled());
        assertEquals(6, s.features().longMemory().extractEveryExchanges());
        assertEquals(20, s.features().longMemory().maxNotesPerPlayer());
        assertEquals("sqlite", s.storage().type());
    }

    /** What a 2.1.0 install of the final config looks like (version 3, long-term memory present, nothing of 2.2.0). */
    private static final String V3 = """
            config-version: 3
            locale: en
            api-key: sk-abcdefghijkl
            interval-seconds: 45
            long-term-memory:
              enabled: true
              retrieval-top-k: 9
            """;

    @Test
    void versionThreeConfigGetsTheAwarenessSectionsOffByDefaultAndKeepsItsValues() throws Exception {
        YamlConfiguration user = load(V3);
        YamlConfiguration defaults = bundled();
        assertTrue(ConfigUpdater.merge(user, defaults) > 0);
        String out = user.saveToString();
        assertEquals(5, user.getInt(ConfigUpdater.VERSION_KEY));
        assertEquals(45, user.getInt("interval-seconds"));
        assertTrue(user.getBoolean("long-term-memory.enabled"));
        assertEquals(9, user.getInt("long-term-memory.retrieval-top-k"));
        for (String section : java.util.List.of("server-context", "mood", "relationships", "boredom", "supervision")) {
            assertTrue(user.isConfigurationSection(section), section);
            assertFalse(user.getBoolean(section + ".enabled"), section + " must be off by default");
            assertFalse(user.getComments(section).isEmpty(), section + " has no comment");
        }
        assertTrue(out.contains("PRIVACY"), out);
        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(out, again.saveToString());

        Logger log = Logger.getLogger("ConfigUpdaterTest");
        log.setLevel(Level.OFF);
        Awareness a = Settings.load(again, log).features().awareness();
        assertEquals(Awareness.defaults(), a);
    }

    // ---- removal of the proxy provider (config-version 5) ----

    private static boolean inactive(Settings s) {
        var http = new me.skerik.aifriend.ai.provider.HttpOptions(java.time.Duration.ofSeconds(1),
                java.time.Duration.ofSeconds(1), "test");
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        return me.skerik.aifriend.ai.provider.ProviderFactory.create(s.provider(), http, java.time.Clock.systemUTC(), quiet)
                .isEmpty();
    }

    private static Settings settingsOf(YamlConfiguration y, java.util.List<String> warnings) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return Settings.load(y, log);
    }

    @Test
    void oldProxyConfigIsNotSwitchedToAnotherProvider() throws Exception {
        // 2.0.0: top-level key (maybe "trial"), no provider section at all
        for (String text : new String[] {V200, "config-version: 1\napi-key: trial\n", LEGACY}) {
            YamlConfiguration user = load(text);
            ConfigUpdater.flagRetiredProxy(user);
            ConfigUpdater.merge(user, bundled());
            ConfigUpdater.migrate(user, ConfigUpdater.RENAMES);
            assertEquals("proxy", user.getString("provider.primary"), "marker instead of a silent switch to openai");
            java.util.List<String> warnings = new java.util.ArrayList<>();
            Settings s = settingsOf(user, warnings);
            assertTrue(s.provider().retiredProxy());
            assertTrue(inactive(s), "no provider may be built");
        }
    }

    @Test
    void configWithoutAnyKeyJustGetsTheNewDefault() throws Exception {
        YamlConfiguration user = load(OLD);
        ConfigUpdater.flagRetiredProxy(user);
        ConfigUpdater.merge(user, bundled());
        assertEquals("openai", user.getString("provider.primary"));
        assertFalse(settingsOf(user, new java.util.ArrayList<>()).provider().retiredProxy());
    }

    @Test
    void anExplicitProviderChoiceIsNeverTouched() throws Exception {
        String text = "config-version: 4\napi-key: trial\nprovider:\n  primary: ollama\n";
        YamlConfiguration user = load(text);
        ConfigUpdater.flagRetiredProxy(user);
        ConfigUpdater.merge(user, bundled());
        assertEquals("ollama", user.getString("provider.primary"));
        assertEquals("trial", user.getString("api-key"), "admin values stay");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        Settings s = settingsOf(user, warnings);
        assertFalse(s.provider().retiredProxy());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no longer used")), warnings.toString());
    }

    @Test
    void version4ConfigWithProxyPrimaryKeepsItsValueAndStaysInactive() throws Exception {
        YamlConfiguration user = load("config-version: 4\nprovider:\n  primary: proxy\n  failover: [openai]\n"
                + "  providers:\n    openai:\n      api-key: sk-real-abcdefghijkl\n");
        ConfigUpdater.flagRetiredProxy(user);
        ConfigUpdater.merge(user, bundled());
        assertEquals("proxy", user.getString("provider.primary"));
        assertEquals(5, user.getInt(ConfigUpdater.VERSION_KEY));
        Settings s = settingsOf(user, new java.util.ArrayList<>());
        assertTrue(s.provider().retiredProxy());
        assertTrue(inactive(s), "the openai key must not make the bot send chat elsewhere");
    }
}
