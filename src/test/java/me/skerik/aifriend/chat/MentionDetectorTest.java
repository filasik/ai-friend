package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MentionDetectorTest {

    private static MentionDetector det(String name, List<String> triggers, boolean anywhere) {
        return new MentionDetector(name, new MentionDetector.Config(true, true, triggers, anywhere));
    }

    private static Optional<String> q(MentionDetector d, String line) {
        return d.detect(line).map(MentionDetector.Mention::question);
    }

    @Test
    void disabledByDefault() {
        var d = new MentionDetector("Bot", MentionDetector.Config.defaults());
        assertTrue(d.detect("@Bot hi").isEmpty());
    }

    @Test
    void atMention() {
        var d = det("AI Friend", List.of(), false);
        assertEquals(Optional.of("what time is it?"), q(d, "@AI Friend what time is it?"));
        assertEquals(Optional.of("what time is it?"), q(d, "@ai friend, what time is it?"));
        assertEquals(Optional.of("hello there"), q(d, "hello @AIFriend there"));
        assertEquals(Optional.of(""), q(d, "@AI Friend"));
    }

    @Test
    void prefixMention() {
        var d = det("Bot", List.of(), false);
        assertEquals(Optional.of("how are you"), q(d, "Bot, how are you"));
        assertEquals(Optional.of("how are you"), q(d, "bot: how are you"));
        assertEquals(Optional.of(""), q(d, "Bot:"));
    }

    @Test
    void notAMention() {
        var d = det("Bot", List.of(), false);
        assertTrue(q(d, "the Bot is nice").isEmpty());
        assertTrue(q(d, "Botanist, hi").isEmpty());
        assertTrue(q(d, "@Botanist hi").isEmpty());
        assertTrue(q(d, "me@Bot.com").isEmpty());
        assertTrue(q(d, "Bot").isEmpty());
        assertTrue(q(d, "   ").isEmpty());
        assertTrue(q(d, "").isEmpty());
    }

    @Test
    void diacriticsAndCaseInsensitive() {
        var d = det("Přítel", List.of(), false);
        assertEquals(Optional.of("ahoj"), q(d, "pritel, ahoj"));
        assertEquals(Optional.of("ahoj"), q(d, "@PŘÍTEL ahoj"));
        var d2 = det("Pritel", List.of(), false);
        assertEquals(Optional.of("ahoj"), q(d2, "Přítel: ahoj"));
    }

    @Test
    void unicodeNamesAndEmoji() {
        var d = det("Žabák", List.of(), false);
        assertEquals(Optional.of("co děláš 😀"), q(d, "@zabak co děláš 😀"));
        assertEquals(Optional.of("a 😀 b"), q(d, "a 😀 @Žabák b"));
    }

    @Test
    void extraTriggers() {
        var d = det("AI Friend", List.of("Robot", "hey bot"), false);
        assertEquals(Optional.of("help"), q(d, "Robot, help"));
        assertEquals(Optional.of("help"), q(d, "@robot help"));
        assertEquals(Optional.of("help"), q(d, "hey bot: help"));
        assertTrue(q(d, "my robot is broken").isEmpty());
    }

    @Test
    void reactAnywhereGivesWholeLine() {
        var d = det("Bot", List.of("Robot"), true);
        assertEquals(Optional.of("is the bot sleeping?"), q(d, "is the bot sleeping?"));
        assertEquals(Optional.of("hey ROBOT!"), q(d, "hey ROBOT!"));
        assertTrue(q(d, "botanical garden").isEmpty());
    }

    @Test
    void colourCodesAndControlCharsAreIgnored() {
        var d = det("Bot", List.of(), false);
        assertEquals(Optional.of("hi"), q(d, "§a@Bot\n hi"));
    }

    @Test
    void blankTriggersIgnored() {
        var d = det("Bot", List.of("", "  "), true);
        assertFalse(q(d, "just talking").isPresent());
    }
}
