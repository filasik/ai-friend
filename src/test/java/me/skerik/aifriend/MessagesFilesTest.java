package me.skerik.aifriend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import me.skerik.aifriend.config.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every bundled language must have exactly the keys of the English file (no missing translations, no leftovers). */
class MessagesFilesTest {

    private static Set<String> keys(String locale) throws Exception {
        try (InputStream in = MessagesFilesTest.class.getResourceAsStream("/messages_" + locale + ".yml")) {
            assertTrue(in != null, "messages_" + locale + ".yml missing");
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> leaves = new TreeSet<>();
            for (String key : y.getKeys(true)) {
                if (!y.isConfigurationSection(key)) leaves.add(key);
            }
            return leaves;
        }
    }

    @Test
    void allLocalesHaveTheSameKeysAsEnglish() throws Exception {
        Set<String> english = keys("en");
        assertTrue(english.contains("prefix"));
        for (String locale : Messages.BUNDLED_LOCALES) {
            assertEquals(english, keys(locale), "keys of messages_" + locale + ".yml differ from messages_en.yml");
        }
    }

    /** Keys that src/main asks Messages for (get/list calls, plus the five command.state.* values built from State). */
    @Test
    void everyKeyTheCodeUsesExistsInEnglish() throws Exception {
        YamlConfiguration en = new YamlConfiguration();
        try (InputStream in = MessagesFilesTest.class.getResourceAsStream("/messages_en.yml")) {
            en.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> keys = List.of("chat.format", "update.available", "command.info", "command.reloaded",
                "error.not-ready", "command.state.running", "command.state.disabled", "command.state.no-key",
                "command.state.sleeping", "command.state.backoff", "prefix");
        for (String key : keys) {
            assertTrue(en.isString(key), "messages_en.yml lacks " + key);
        }
        assertTrue(en.isList("command.status"), "command.status must be a list");
    }

    @Test
    void stateMessagesCoverEveryBotState() throws Exception {
        YamlConfiguration en = new YamlConfiguration();
        try (InputStream in = MessagesFilesTest.class.getResourceAsStream("/messages_en.yml")) {
            en.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (me.skerik.aifriend.ai.ChatBot.State state : me.skerik.aifriend.ai.ChatBot.State.values()) {
            String key = "command.state." + state.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            assertTrue(en.isString(key), "messages_en.yml lacks " + key);
        }
    }

    private static YamlConfiguration yaml(String locale) throws Exception {
        try (InputStream in = MessagesFilesTest.class.getResourceAsStream("/messages_" + locale + ".yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return y;
        }
    }

    private static List<String> usedKeys() {
        List<String> keys = new java.util.ArrayList<>(List.of(
                "prefix", "error.not-ready", "error.players-only", "chat.format", "update.available",
                "command.info", "command.reloaded",
                "command.mute.done", "command.mute.already", "command.mute.disabled",
                "command.unmute.done", "command.unmute.already",
                "command.ask.usage", "command.ask.thinking", "command.ask.muted", "command.ask.disabled",
                "command.ask.failed", "command.ask.empty",
                "command.limit.remaining", "command.limit.unlimited",
                "command.sleep.done", "command.wake.done", "command.wake.already",
                "command.cost.header", "command.cost.disabled", "command.cost.warning",
                "command.simulate.header", "command.simulate.request", "command.simulate.none",
                "command.simulate.answer-yes", "command.simulate.answer-no"));
        for (me.skerik.aifriend.chat.RateLimiter.Reason r : me.skerik.aifriend.chat.RateLimiter.Reason.values()) {
            if (r != me.skerik.aifriend.chat.RateLimiter.Reason.OK) {
                keys.add("command.limit." + r.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
            }
        }
        for (me.skerik.aifriend.chat.SimulationResult.Blocker b : me.skerik.aifriend.chat.SimulationResult.Blocker.values()) {
            keys.add("command.simulate.blocker." + b.name());
        }
        for (me.skerik.aifriend.ai.ChatBot.State st : me.skerik.aifriend.ai.ChatBot.State.values()) {
            keys.add("command.state." + st.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        }
        return keys;
    }

    @Test
    void everyKeyUsedByTheCodeExistsInEveryLocale() throws Exception {
        for (String locale : Messages.BUNDLED_LOCALES) {
            YamlConfiguration y = yaml(locale);
            for (String key : usedKeys()) assertTrue(y.isString(key), "messages_" + locale + ".yml lacks " + key);
            for (String list : List.of("command.status", "command.status-extra", "command.cost.lines", "command.simulate.lines")) {
                assertTrue(y.isList(list) && !y.getStringList(list).isEmpty(), "messages_" + locale + ".yml lacks list " + list);
            }
        }
    }

    /** Message keys passed to Messages/notifier in src/main as plain literals must exist too. */
    @Test
    void literalKeysFoundInTheSourcesExist() throws Exception {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:get|list|tell|tellPermission)\\((?:[^\"()]*,\\s*)?\"((?:command|error|chat|update)[a-z.\\-]*)\"");
        Set<String> found = new TreeSet<>();
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            for (var f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                var m = p.matcher(java.nio.file.Files.readString(f));
                while (m.find()) found.add(m.group(1));
            }
        }
        assertFalse(found.isEmpty());
        for (String locale : Messages.BUNDLED_LOCALES) {
            YamlConfiguration y = yaml(locale);
            for (String key : found) assertTrue(y.contains(key), "messages_" + locale + ".yml lacks " + key);
        }
    }
}
