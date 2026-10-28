package me.skerik.aifriend.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ProviderSettingsTest {

    private static final HttpOptions OPTS = TestServer.options(java.time.Duration.ofSeconds(5));

    private final List<String> warnings = new ArrayList<>();
    private final Logger log = logger();

    private Logger logger() {
        Logger l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        l.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return l;
    }

    private ProviderSettings load(String yaml) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        ConfigurationSection s = y.getConfigurationSection("provider");
        return ProviderSettings.load(s, log);
    }

    @Test
    void missingSectionGivesDefaults() {
        ProviderSettings s = ProviderSettings.load(null, log);
        assertEquals("proxy", s.primary());
        assertTrue(s.failover().isEmpty());
        assertFalse(s.cacheEnabled());
        assertEquals(200, s.cacheMaxEntries());
        assertEquals(10, s.cacheTtlMinutes());
        assertEquals(200, s.maxOutputTokens());
        assertEquals(Set5.names(), s.providers().keySet());
        assertEquals("claude-haiku-5-5", s.providers().get("anthropic").model());
        assertEquals("https://api.openai.com/v1", s.providers().get("openai").baseUrl());
        assertEquals("http://localhost:11434", s.providers().get("ollama").baseUrl());
        assertTrue(warnings.isEmpty());
    }

    /** The five built-in names. */
    private static final class Set5 {
        static java.util.Set<String> names() {
            return java.util.Set.of("proxy", "openai", "anthropic", "ollama", "custom");
        }
    }

    @Test
    void fullConfigIsParsed() throws Exception {
        ProviderSettings s = load("""
                provider:
                  primary: Anthropic
                  failover: [openai, ollama]
                  failover-cooldown-seconds: 120
                  cache: {enabled: true, max-entries: 50, ttl-minutes: 3}
                  providers:
                    anthropic: {model: claude-sonnet-5-5, api-key: "ak", max-output-tokens: 300}
                    openai: {api-key: "ok", base-url: "https://example.org/v1"}
                    mine: {type: custom, base-url: "http://localhost:1234/v1", model: qwen}
                """);
        assertEquals("anthropic", s.primary());
        assertEquals(List.of("openai", "ollama"), s.failover());
        assertEquals(120, s.failoverCooldownSeconds());
        assertTrue(s.cacheEnabled());
        assertEquals(50, s.cacheMaxEntries());
        assertEquals(3, s.cacheTtlMinutes());
        assertEquals(300, s.maxOutputTokens());
        assertEquals("claude-sonnet-5-5", s.providers().get("anthropic").model());
        assertEquals("https://example.org/v1", s.providers().get("openai").baseUrl());
        assertEquals(ProviderSettings.Type.CUSTOM, s.providers().get("mine").type());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void invalidValuesFallBackWithWarnings() throws Exception {
        ProviderSettings s = load("""
                provider:
                  primary: nonsense
                  failover: [openai, openai, ghost, nonsense]
                  failover-cooldown-seconds: 1
                  cache: {enabled: true, max-entries: 0, ttl-minutes: 99999}
                  providers:
                    openai: {base-url: "ftp://x", max-output-tokens: 999999, api-key: "your_openai_api_key_here"}
                    bad: {type: wat}
                    notype: {model: x}
                """);
        assertEquals("proxy", s.primary());
        assertEquals(List.of("openai"), s.failover());
        assertEquals(60, s.failoverCooldownSeconds());
        assertEquals(200, s.cacheMaxEntries());
        assertEquals(10, s.cacheTtlMinutes());
        assertEquals("https://api.openai.com/v1", s.providers().get("openai").baseUrl());
        assertEquals(200, s.providers().get("openai").maxOutputTokens());
        assertEquals("", s.providers().get("openai").apiKey(), "placeholder counts as unset");
        assertFalse(s.providers().containsKey("bad"));
        assertFalse(s.providers().containsKey("notype"));
        assertTrue(warnings.size() >= 8, warnings.toString());
    }

    // --- factory ---

    @Test
    void defaultsBuildTheProxyOnly() {
        ProviderSettings s = ProviderSettings.load(null, log);
        var p = ProviderFactory.create(s, "mykey", "gpt-4o-mini", OPTS, Clock.systemUTC(), log);
        assertInstanceOf(ProxyProvider.class, p.orElseThrow());
        assertTrue(ProviderFactory.create(s, "", "m", OPTS, Clock.systemUTC(), log).isEmpty());
        assertTrue(ProviderFactory.create(s, "your_openai_api_key_here", "m", OPTS, Clock.systemUTC(), log).isEmpty());
    }

    @Test
    void chainWithFailoverAndCache() throws Exception {
        ProviderSettings s = load("""
                provider:
                  primary: openai
                  failover: [ollama, anthropic, custom]
                  cache: {enabled: true}
                  providers:
                    openai: {api-key: "ok"}
                """);
        // anthropic (no key) and custom (no base-url) are skipped with warnings
        AiProvider p = ProviderFactory.create(s, "", "m", OPTS, Clock.systemUTC(), log).orElseThrow();
        assertInstanceOf(CachingProvider.class, p);
        assertEquals("openai", p.id());
        assertEquals(2, warnings.size(), warnings.toString());
        warnings.forEach(w -> assertFalse(w.contains("ok\"")));
    }

    @Test
    void singleProviderIsNotWrappedAndKeylessPrimaryFallsToFailover() throws Exception {
        ProviderSettings s = load("""
                provider:
                  primary: openai
                  failover: [ollama]
                """);
        AiProvider p = ProviderFactory.create(s, "", "m", OPTS, Clock.systemUTC(), log).orElseThrow();
        assertInstanceOf(OllamaProvider.class, p);
        assertTrue(warnings.getFirst().contains("openai"));
    }
}
