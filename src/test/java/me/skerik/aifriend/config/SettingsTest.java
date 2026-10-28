package me.skerik.aifriend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static final Logger LOG = Logger.getLogger("SettingsTest");

    static {
        LOG.setLevel(Level.OFF);
    }

    private static YamlConfiguration bundled() throws Exception {
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return y;
        }
    }

    private static Settings with(String path, Object value) throws Exception {
        YamlConfiguration y = bundled();
        y.set(path, value);
        return Settings.load(y, LOG);
    }

    @Test
    void defaultsMatchTheBundledConfig() throws Exception {
        Settings s = Settings.load(bundled(), LOG);
        assertEquals("en", s.locale());
        assertTrue(s.updateCheck());
        assertTrue(s.metrics());
        assertEquals(List.of(), s.aliases());
        assertTrue(s.enabled());
        assertEquals("gpt-4o-mini", s.model());
        assertEquals("AI Friend", s.chatbotName());
        assertEquals("English", s.language());
        assertEquals("", s.personalization());
        assertEquals(60, s.intervalSeconds());
        assertTrue(s.stopWhenIgnored());
        assertEquals(30, s.sleepMinutes());
        assertEquals(50, s.maxQueuedMessages());
        assertEquals(256, s.maxReplyLength());
        assertEquals(10, s.connectTimeoutSeconds());
        assertEquals(30, s.readTimeoutSeconds());
        assertFalse(s.hasApiKey());
    }

    @Test
    void emptyConfigGivesTheSameDefaults() throws Exception {
        assertEquals(Settings.load(bundled(), LOG), Settings.load(new YamlConfiguration(), LOG));
    }

    @Test
    void outOfRangeNumbersFallBack() throws Exception {
        assertEquals(60, with("interval-seconds", 4).intervalSeconds());
        assertEquals(60, with("interval-seconds", 3601).intervalSeconds());
        assertEquals(5, with("interval-seconds", 5).intervalSeconds());
        assertEquals(3600, with("interval-seconds", 3600).intervalSeconds());
        assertEquals(30, with("sleep-minutes", 0).sleepMinutes());
        assertEquals(50, with("max-queued-messages", 4).maxQueuedMessages());
        assertEquals(50, with("max-queued-messages", 501).maxQueuedMessages());
        assertEquals(256, with("max-reply-length", 19).maxReplyLength());
        assertEquals(10, with("http.connect-timeout-seconds", 0).connectTimeoutSeconds());
        assertEquals(30, with("http.read-timeout-seconds", 121).readTimeoutSeconds());
        assertEquals(60, with("interval-seconds", "soon").intervalSeconds());
    }

    @Test
    void blankTextOptionsFallBack() throws Exception {
        assertEquals("gpt-4o-mini", with("model", "   ").model());
        assertEquals("AI Friend", with("chatbot-name", "").chatbotName());
        assertEquals("English", with("language", " ").language());
        assertEquals("Bob", with("chatbot-name", "  Bob ").chatbotName());
    }

    @Test
    void hasApiKey() throws Exception {
        assertFalse(with("api-key", Settings.KEY_PLACEHOLDER).hasApiKey());
        assertFalse(with("api-key", "   ").hasApiKey());
        assertTrue(with("api-key", "sk-real-key-123456").hasApiKey());
        assertTrue(with("api-key", "trial").hasApiKey());
    }

    @Test
    void maskedKeyNeverShowsTheSecret() throws Exception {
        String secret = "sk-abcdefghijklmnop1234";
        String masked = with("api-key", secret).maskedKey();
        assertEquals("sk-...1234", masked);
        assertFalse(masked.contains("abcdefghijklmnop"));

        assertEquals("-", with("api-key", Settings.KEY_PLACEHOLDER).maskedKey());
        assertEquals("-", with("api-key", "").maskedKey());
        assertEquals("trial", with("api-key", "trial").maskedKey());
        assertEquals("****", with("api-key", "sk-short").maskedKey());
        assertEquals("****", with("api-key", "0123456789").maskedKey());
        assertEquals("012...7890", with("api-key", "01234567890").maskedKey());
    }
}
