package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ReplyFilterTest {

    private static ReplyFilter filter(List<String> words, ReplyFilter.BannedMode mode, boolean urls, int max) {
        return new ReplyFilter(new ReplyFilter.Config(true, max, words, mode, urls));
    }

    @Test
    void defaultOnlySanitises() {
        var f = new ReplyFilter(ReplyFilter.Config.defaults());
        assertEquals(Optional.of("Hello world"), f.apply("§aHello\n\u0007 §cworld"));
        assertEquals(Optional.of("see https://x.com"), f.apply("see https://x.com"));
    }

    @Test
    void emptyResultsAreNone() {
        var f = new ReplyFilter(ReplyFilter.Config.defaults());
        assertTrue(f.apply("").isEmpty());
        assertTrue(f.apply(" \n\t ").isEmpty());
        assertTrue(f.apply("§a").isEmpty());
    }

    @Test
    void hardMaxLengthCutsAtWord() {
        var f = new ReplyFilter(new ReplyFilter.Config(false, 20, List.of(), ReplyFilter.BannedMode.REPLACE, false));
        String out = f.apply("The quick brown fox jumps over the lazy dog").orElseThrow();
        assertTrue(out.length() <= 20, out);
        assertTrue(out.endsWith("…"));
        assertFalse(out.contains("jum"), out);
    }

    @Test
    void cutDoesNotSplitSurrogatePair() {
        var f = new ReplyFilter(new ReplyFilter.Config(false, 20, List.of(), ReplyFilter.BannedMode.REPLACE, false));
        String out = f.apply("a".repeat(18) + "😀😀😀").orElseThrow();
        assertTrue(out.length() <= 20);
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 2)));
    }

    @Test
    void bannedWordsReplaceWholeWordsCaseInsensitive() {
        var f = filter(List.of("badword", "Zlý"), ReplyFilter.BannedMode.REPLACE, false, 256);
        assertEquals("you are ***", f.apply("you are BadWord").orElseThrow());
        assertEquals("*** a *** pes", f.apply("zlý a ZLÝ pes").orElseThrow());
        // inside a longer word: untouched
        assertEquals("badwords and zlým", f.apply("badwords and zlým").orElseThrow());
        // punctuation counts as boundary
        assertEquals("oh, ***!", f.apply("oh, badword!").orElseThrow());
    }

    @Test
    void bannedWordWithRegexCharsIsLiteral() {
        var f = filter(List.of("a+b", "(x)"), ReplyFilter.BannedMode.REPLACE, false, 256);
        assertEquals("*** and ***", f.apply("a+b and (x)").orElseThrow());
        assertEquals("aab", f.apply("aab").orElseThrow());
    }

    @Test
    void dropModeDropsWholeReply() {
        var f = filter(List.of("bad"), ReplyFilter.BannedMode.DROP, false, 256);
        assertTrue(f.apply("this is BAD").isEmpty());
        assertEquals("this is fine", f.apply("this is fine").orElseThrow());
    }

    @Test
    void emptyBannedListIsHarmless() {
        var f = filter(List.of(), ReplyFilter.BannedMode.DROP, false, 256);
        assertEquals("anything", f.apply("anything").orElseThrow());
    }

    @Test
    void removesUrls() {
        var f = filter(List.of(), ReplyFilter.BannedMode.REPLACE, true, 256);
        assertEquals("visit and enjoy", f.apply("visit https://example.com/a?b=1 and enjoy").orElseThrow());
        assertEquals("go to now", f.apply("go to www.example.org now").orElseThrow());
        assertEquals("join now", f.apply("join play.server.com now").orElseThrow());
        assertEquals("a nice com", f.apply("a nice com").orElseThrow());
        assertTrue(f.apply("https://only.link").isEmpty());
    }

    @Test
    void onlyBannedWordWithDropOffStillGivesStars() {
        var f = filter(List.of("bad"), ReplyFilter.BannedMode.REPLACE, false, 256);
        assertEquals("***", f.apply("bad").orElseThrow());
    }

    @Test
    void loadValidates() throws Exception {
        var y = new YamlConfiguration();
        y.loadFromString("enabled: true\nmax-length: 5\nbanned-mode: nope\nbanned-words: [a, '', ' b ']\nblock-urls: true\n");
        var c = ReplyFilter.Config.load(y, Logger.getAnonymousLogger());
        assertEquals(256, c.maxLength());
        assertEquals(ReplyFilter.BannedMode.REPLACE, c.mode());
        assertEquals(List.of("a", "b"), c.bannedWords());
        assertTrue(c.blockUrls());
        assertFalse(ReplyFilter.Config.load(null, Logger.getAnonymousLogger()).enabled());
        assertEquals(30, c.withMaxLength(30).maxLength());
    }
}
