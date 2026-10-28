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
        assertTrue(out.contains("Model to use"), out); // the comment of a new key came along

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
        assertEquals("sk-test123456789", user.getString("api-key"));
        assertFalse(user.getBoolean("stop-when-ignored"));
        assertEquals(120, user.getInt("interval-seconds"));
        assertEquals("gpt-4o", user.getString("model"));
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
        assertTrue(out.contains("Model to use"), out);
        assertTrue(out.contains("How the bot is named in chat"), out);
        assertFalse(out.contains("openai_api_key:"), out);

        // a second pass changes nothing
        YamlConfiguration again = load(out);
        assertEquals(0, ConfigUpdater.merge(again, defaults));
        assertEquals(0, ConfigUpdater.migrate(again, ConfigUpdater.RENAMES));
        assertEquals(out, again.saveToString());

        Logger log = Logger.getLogger("ConfigUpdaterTest");
        log.setLevel(Level.OFF);
        Settings s = Settings.load(again, log);
        assertEquals("sk-test123456789", s.apiKey());
        assertTrue(s.hasApiKey());
        assertTrue(s.enabled());
        assertFalse(s.stopWhenIgnored());
        assertEquals(120, s.intervalSeconds());
        assertEquals("gpt-4o", s.model());
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
        assertTrue(bundled().getInt(ConfigUpdater.VERSION_KEY, 0) >= 1);
    }
}
