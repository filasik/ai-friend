package me.skerik.aifriend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
