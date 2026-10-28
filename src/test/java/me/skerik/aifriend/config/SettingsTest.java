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
        assertEquals("openai", s.provider().primary());
        assertFalse(s.provider().retiredProxy());
        assertEquals("AI Friend", s.chatbotName());
        assertEquals("English", s.language());
        assertEquals("", s.personalization());
        assertEquals(60, s.intervalSeconds());
        assertTrue(s.stopWhenIgnored());
        assertEquals(30, s.sleepMinutes());
        assertEquals(Settings.Chattiness.NORMAL, s.chattiness());
        assertEquals(50, s.maxQueuedMessages());
        assertEquals(256, s.maxReplyLength());
        assertEquals(10, s.connectTimeoutSeconds());
        assertEquals(30, s.readTimeoutSeconds());
        assertEquals("-", s.activeMaskedKey(), "no key shipped");
        assertFalse(bundled().contains("api-key", true), "no top-level key any more");
        assertFalse(bundled().contains("model", true), "no top-level model any more");
    }

    @Test
    void chattinessIsParsedAndAnInvalidValueFallsBackToNormal() throws Exception {
        assertEquals(Settings.Chattiness.HIGH, with("chattiness", "High").chattiness());
        assertEquals(Settings.Chattiness.LOW, with("chattiness", "low").chattiness());
        assertEquals(Settings.Chattiness.NORMAL, with("chattiness", "chatty").chattiness());
    }

    @Test
    void emptyConfigGivesTheSameDefaults() throws Exception {
        Settings shipped = Settings.load(bundled(), LOG);
        Settings empty = Settings.load(new YamlConfiguration(), LOG);
        assertEquals(empty.provider(), shipped.provider());
        ChatFeatures a = shipped.features();
        ChatFeatures b = empty.features();
        // the shipped file carries example values (two personas, prices, limits), all features switched off
        assertEquals(b.personas().enabled(), a.personas().enabled());
        assertEquals(2, a.personas().personas().size());
        assertFalse(a.memory().enabled(), "memory must be off in the shipped config");
        assertEquals(b.memory().enabled(), a.memory().enabled());
        assertFalse(a.limits().enabled(), "limits must be off in the shipped config");
        assertEquals(b.limits().enabled(), a.limits().enabled());
        assertFalse(a.cost().enabled(), "cost must be off in the shipped config");
        assertEquals(b.cost().enabled(), a.cost().enabled());
        assertFalse(a.replyFilter().enabled(), "replyFilter must be off in the shipped config");
        assertEquals(b.replyFilter().enabled(), a.replyFilter().enabled());
        assertFalse(a.mentions().enabled(), "mentions must be off in the shipped config");
        assertEquals(b.mentions().enabled(), a.mentions().enabled());
        assertFalse(a.optOut().enabled(), "optOut must be off in the shipped config");
        assertEquals(b.optOut().enabled(), a.optOut().enabled());
        assertFalse(a.events().enabled(), "events must be off in the shipped config");
        assertEquals(b.events().enabled(), a.events().enabled());
        assertEquals(empty.intervalSeconds(), shipped.intervalSeconds());
        assertEquals(empty.chatbotName(), shipped.chatbotName());
        assertEquals(empty.maxReplyLength(), shipped.maxReplyLength());
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
        assertEquals("AI Friend", with("chatbot-name", "").chatbotName());
        assertEquals("English", with("language", " ").language());
        assertEquals("Bob", with("chatbot-name", "  Bob ").chatbotName());
    }

    @Test
    void activeKeyAndModelComeFromTheChosenProvider() throws Exception {
        assertEquals("gpt-4o-mini", bundled().getString("provider.providers.openai.model"));
        assertEquals("gpt-4o-mini", with("provider.primary", "openai").activeModel());
        assertEquals("claude-haiku-5-5", with("provider.primary", "anthropic").activeModel());
        assertEquals("-", with("provider.primary", "custom").activeModel(), "custom has no default model");
    }

    @Test
    void maskedKeyNeverShowsTheSecret() throws Exception {
        String secret = "sk-abcdefghijklmnop1234";
        String masked = with("provider.providers.openai.api-key", secret).activeMaskedKey();
        assertEquals("sk-...1234", masked);
        assertFalse(masked.contains("abcdefghijklmnop"));

        String path = "provider.providers.openai.api-key";
        assertEquals("-", with(path, "your_openai_api_key_here").activeMaskedKey());
        assertEquals("-", with(path, "").activeMaskedKey());
        assertEquals("****", with(path, "sk-short").activeMaskedKey());
        assertEquals("****", with(path, "0123456789").activeMaskedKey());
        assertEquals("012...7890", with(path, "01234567890").activeMaskedKey());
    }

    @Test
    void aStrayTopLevelKeyIsWarnedAboutAndNeverUsed() throws Exception {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        YamlConfiguration y = bundled();
        y.set("api-key", "sk-leftover-1234567890");
        Settings s = Settings.load(y, log);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no longer used")), warnings.toString());
        assertEquals("-", s.activeMaskedKey());
        assertFalse(warnings.toString().contains("leftover"), "the key is never logged");
    }

    @Test
    void storageAndLongTermMemoryDefaults() throws Exception {
        Settings s = Settings.load(bundled(), LOG);
        assertEquals("sqlite", s.storage().type());
        assertEquals("ai-friend.db", s.storage().sqliteFile());
        assertEquals(3, s.storage().sqlitePool());
        assertEquals(3306, s.storage().port());
        var lm = s.features().longMemory();
        assertFalse(lm.enabled());
        assertEquals(6, lm.extractEveryExchanges());
        assertEquals(20, lm.maxNotesPerPlayer());
        assertEquals(0, lm.retentionDays());
        assertEquals(5, lm.retrievalTopK());
        assertTrue(lm.deleteOnMute());
        assertEquals("none", lm.embeddings().backend());
        assertEquals(s.features().longMemory(), me.skerik.aifriend.longmemory.LongTermMemory.Config.defaults());
        assertEquals("change-me", s.storage().password()); // the shipped example password
    }

    @Test
    void storageValuesAreValidatedWithFallback() throws Exception {
        assertEquals("mariadb", with("storage.type", "MySQL").storage().type());
        assertEquals("sqlite", with("storage.type", "oracle").storage().type());
        assertEquals("ai-friend.db", with("storage.sqlite.file", "../evil.db").storage().sqliteFile());
        assertEquals("ai-friend.db", with("storage.sqlite.file", "a/b.db").storage().sqliteFile());
        assertEquals("x.db", with("storage.sqlite.file", "x.db").storage().sqliteFile());
        assertEquals(3306, with("storage.mariadb.port", 0).storage().port());
        Settings m = with("storage.type", "mariadb");
        assertTrue(m.storage().dbConfig(java.nio.file.Path.of("x")).dialect() == me.skerik.aifriend.storage.DbConfig.Dialect.MARIADB);
        assertFalse(m.storage().describe().contains("change-me"));
    }

    @Test
    void longTermMemoryValuesAreValidatedWithFallback() throws Exception {
        assertEquals(6, with("long-term-memory.extract-every-exchanges", 0).features().longMemory().extractEveryExchanges());
        assertEquals(20, with("long-term-memory.max-notes-per-player", 9999).features().longMemory().maxNotesPerPlayer());
        assertEquals(5, with("long-term-memory.retrieval-top-k", -1).features().longMemory().retrievalTopK());
        assertEquals(30, with("long-term-memory.retention-days", 30).features().longMemory().retentionDays());
        assertEquals("openai", with("long-term-memory.embeddings.backend", " OpenAI ").features().longMemory().embeddings().backend());
        assertEquals("", with("long-term-memory.embeddings.base-url", "ftp://x").features().longMemory().embeddings().baseUrl());
    }
}
