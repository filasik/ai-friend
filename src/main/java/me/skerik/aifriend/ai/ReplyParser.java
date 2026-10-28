package me.skerik.aifriend.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Turns the proxy's answer into a {@link Reply}. The proxy returns the model output as a JSON string; the model was
 * asked for {@code {"action":"say","text":"..."}}. Plain text is accepted too (older proxy behaviour, or a model that
 * ignored the format), and the exact legacy answer {@code STOP MESSAGING} still means sleep. Text is cleaned and cut.
 */
public final class ReplyParser {

    private static final String LEGACY_STOP = "STOP MESSAGING";

    private ReplyParser() {
    }

    /** @throws IOException when the proxy reported an error instead of an answer */
    public static Reply parse(String body, String botName, int maxLength) throws IOException {
        String content = unwrap(body);
        if (content == null) return Reply.NONE;
        content = stripFences(content.strip());
        if (content.isEmpty()) return Reply.NONE;

        if (content.startsWith("{")) {
            try {
                JsonObject o = JsonParser.parseString(content).getAsJsonObject();
                if (o.has("action")) return fromAction(o, botName, maxLength);
            } catch (JsonParseException | IllegalStateException ignored) {
                // not JSON after all: treat it as plain text below
            }
        }
        String plain = Text.singleLine(content).replaceAll("^[\"'\\s]+|[\"'.!\\s]+$", "");
        if (plain.equalsIgnoreCase(LEGACY_STOP)) return Reply.SLEEP;
        return say(content, botName, maxLength);
    }

    private static Reply fromAction(JsonObject o, String botName, int maxLength) {
        String action = o.get("action").isJsonPrimitive() ? o.get("action").getAsString().strip().toLowerCase(Locale.ROOT) : "";
        return switch (action) {
            case "sleep", "stop" -> Reply.SLEEP;
            case "say" -> o.has("text") && o.get("text").isJsonPrimitive()
                    ? say(o.get("text").getAsString(), botName, maxLength) : Reply.NONE;
            default -> Reply.NONE;
        };
    }

    private static Reply say(String raw, String botName, int maxLength) {
        String text = Text.singleLine(raw);
        // the model sometimes repeats its own name although it was told not to
        String prefix = botName + ":";
        if (text.regionMatches(true, 0, prefix, 0, prefix.length())) text = text.substring(prefix.length()).strip();
        if (text.isEmpty()) return Reply.NONE;
        return Reply.say(Text.cut(text, maxLength));
    }

    /** The answer text inside the proxy's JSON string; the raw body when it is not JSON at all. */
    private static @Nullable String unwrap(String body) throws IOException {
        String trimmed = body.strip();
        if (trimmed.isEmpty()) return null;
        try {
            JsonElement e = JsonParser.parseString(trimmed);
            if (e.isJsonPrimitive()) return e.getAsString();
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("action")) return trimmed;
                if (o.has("error")) throw new IOException("The AI service reported an error: " + o.get("error"));
                for (String key : new String[] {"response", "reply", "message", "text"}) {
                    if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsString();
                }
                return null;
            }
            return null;
        } catch (JsonParseException ex) {
            return trimmed;
        }
    }

    private static String stripFences(String s) {
        if (!s.startsWith("```")) return s;
        int firstNewline = s.indexOf('\n');
        int end = s.lastIndexOf("```");
        if (firstNewline < 0 || end <= firstNewline) return s;
        return s.substring(firstNewline + 1, end).strip();
    }
}
