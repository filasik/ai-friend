package me.skerik.aifriend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void stripsColourCodes() {
        assertEquals("Hello red", Text.singleLine("§aHello §cred"));
        assertFalse(Text.singleLine("§l§nbold").contains("§"));
        assertEquals("end", Text.singleLine("end§"));
    }

    @Test
    void controlCharactersAreNotKept() {
        String out = Text.singleLine("a\u0000b\u0007c\u001b[0md");
        out.codePoints().forEach(cp -> assertFalse(Character.isISOControl(cp), out));
    }

    @Test
    void collapsesWhitespace() {
        assertEquals("a b c", Text.singleLine("  a \n\t b\r\n   c  "));
    }

    @Test
    void blankLineIsNull() {
        assertNull(ChatLine.of("p", "   \n\t "));
        assertNull(ChatLine.of("p", "§a"));
        assertNull(ChatLine.of("p", ""));
    }

    @Test
    void chatLineKeepsPlayerAndCleanText() {
        ChatLine l = ChatLine.of("Steve", " hi  §cthere ");
        assertNotNull(l);
        assertEquals("Steve", l.player());
        assertEquals("hi there", l.text());
    }

    @Test
    void longTextIsCutAtMaxWithEllipsis() {
        ChatLine l = ChatLine.of("p", "a".repeat(1000));
        assertNotNull(l);
        assertTrue(l.text().length() <= ChatLine.MAX_TEXT);
        assertTrue(l.text().endsWith("…"));
    }

    @Test
    void shortTextIsUntouched() {
        assertEquals("abc", Text.cut("abc", 3));
    }

    @Test
    void cutNeverSplitsASurrogatePair() {
        String emoji = "😀";
        for (int prefix = 290; prefix < 300; prefix++) {
            ChatLine l = ChatLine.of("p", "a".repeat(prefix) + emoji.repeat(10));
            assertNotNull(l);
            String t = l.text();
            assertTrue(t.length() <= ChatLine.MAX_TEXT, "length " + t.length());
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    assertTrue(i + 1 < t.length() && Character.isLowSurrogate(t.charAt(i + 1)), "prefix " + prefix);
                    i++;
                } else {
                    assertFalse(Character.isLowSurrogate(c), "lone low surrogate, prefix " + prefix);
                }
            }
        }
    }

    @Test
    void cutPrefersAWordBoundary() {
        String text = "word ".repeat(100).strip();
        String cut = Text.cut(text, 50);
        assertTrue(cut.endsWith("word…"), cut);
        assertTrue(cut.length() <= 50);
    }
}
