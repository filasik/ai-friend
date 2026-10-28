package me.skerik.aifriend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ReplyParserTest {

    private static final String BOT = "AI Friend";
    private static final Gson GSON = new Gson();

    private static Reply parse(String body) throws IOException {
        return ReplyParser.parse(body, BOT, 256);
    }

    @Test
    void jsonStringWrappingAnObjectSay() throws IOException {
        String body = GSON.toJson("{\"action\":\"say\",\"text\":\"Hello world\"}");
        assertEquals(Reply.say("Hello world"), parse(body));
    }

    @Test
    void jsonStringWrappingAnObjectSleep() throws IOException {
        assertEquals(Reply.SLEEP, parse(GSON.toJson("{\"action\":\"sleep\"}")));
    }

    @Test
    void bareObject() throws IOException {
        assertEquals(Reply.say("Hi"), parse("{\"action\":\"say\",\"text\":\"Hi\"}"));
        assertEquals(Reply.SLEEP, parse("{\"action\":\"sleep\"}"));
    }

    @Test
    void markdownFencedJson() throws IOException {
        assertEquals(Reply.say("Hi"), parse("```json\n{\"action\":\"say\",\"text\":\"Hi\"}\n```"));
        assertEquals(Reply.say("Hi"), parse(GSON.toJson("```\n{\"action\":\"say\",\"text\":\"Hi\"}\n```")));
    }

    @Test
    void plainText() throws IOException {
        assertEquals(Reply.say("Hello there, friends!"), parse("Hello there, friends!"));
        assertEquals(Reply.say("Hello there"), parse(GSON.toJson("Hello there")));
    }

    @Test
    void leadingBotNameIsRemoved() throws IOException {
        assertEquals(Reply.say("Hi all"), parse("AI Friend: Hi all"));
        assertEquals(Reply.say("Hi all"), parse("{\"action\":\"say\",\"text\":\"ai friend: Hi all\"}"));
    }

    @Test
    void legacyStopMessagingIsSleep() throws IOException {
        for (String body : new String[] {"STOP MESSAGING", "\"STOP MESSAGING\"", "STOP MESSAGING.", "stop messaging",
                "  'Stop Messaging!'  ", GSON.toJson("STOP MESSAGING.")}) {
            assertEquals(Reply.SLEEP, parse(body), body);
        }
    }

    @Test
    void sentenceContainingStopMessagingIsJustSpeech() throws IOException {
        Reply r = parse("Please do not STOP MESSAGING me, friends");
        assertEquals(Reply.Action.SAY, r.action());
    }

    @Test
    void errorObjectIsAnIoException() {
        assertThrows(IOException.class, () -> parse("{\"error\":\"Invalid key\"}"));
    }

    @Test
    void emptyAndGarbageGiveNone() throws IOException {
        for (String body : new String[] {"", "   ", "{}", "[]", "null", "{\"foo\":1}", "{\"action\":\"dance\"}",
                "{\"action\":\"say\"}", "{\"action\":\"say\",\"text\":\"  \"}", "```\n```"}) {
            assertEquals(Reply.NONE, parse(body), body);
        }
    }

    @Test
    void truncatesToMaxLength() throws IOException {
        String text = "word ".repeat(100).strip();
        Reply r = ReplyParser.parse(GSON.toJson(text), BOT, 40);
        assertEquals(Reply.Action.SAY, r.action());
        assertTrue(r.text().length() <= 40, r.text());
        Reply obj = ReplyParser.parse("{\"action\":\"say\",\"text\":\"" + text + "\"}", BOT, 40);
        assertTrue(obj.text().length() <= 40);
    }

    @Test
    void colourCodesAndNewlinesAreStripped() throws IOException {
        Reply r = parse("{\"action\":\"say\",\"text\":\"§cRed\\nline §lbold\"}");
        assertEquals("Red line bold", r.text());
        assertFalse(parse("§4Plain §ctext").text().contains("§"));
    }

    @Test
    void unicodeEscapesAreDecoded() throws IOException {
        assertEquals("Příliš", parse("\"P\\u0159\\u00edli\\u0161\"").text());
        assertEquals("Přivítej", parse("{\"action\":\"say\",\"text\":\"P\\u0159iv\\u00edtej\"}").text());
    }

    @Test
    void legacyObjectWithResponseField() throws IOException {
        assertEquals(Reply.say("Hi"), parse("{\"response\":\"Hi\"}"));
    }

    @Test
    void truncatedJsonKeepsTheTextAndNeverPostsRawJson() throws IOException {
        Reply r = parse("{\"action\":\"say\",\"text\":\"Kubushacku, jeskyn\\u011b p\\u0159esn\\u011b nevid\\u00edm, a \\\"\\n\\\" jsi na");
        assertEquals(Reply.Action.SAY, r.action());
        assertEquals("Kubushacku, jeskyně přesně nevidím, a \" \" jsi na", r.text());
        assertEquals(Reply.NONE, parse("{\"action\":\"say\",\"te"));
        assertEquals(Reply.SLEEP, parse("{\"action\":\"sleep\", \"tex"));
    }

    @Test
    void skipMeansNoReplyAndNoSleep() throws IOException {
        for (String body : new String[] {"{\"action\":\"skip\"}", "{\"action\":\"Pass\"}", "{\"action\":\"silent\"}"}) {
            assertEquals(Reply.NONE, parse(body), body);
        }
    }
}
