package me.skerik.aifriend.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Turns the provider's answer text into a {@link Reply}. The model was asked for
 * {@code {"action":"say","text":"..."}}. Plain text (or a JSON string) is accepted too, for a model that ignored the
 * format, and the exact legacy answer {@code STOP MESSAGING} still means sleep. Text is cleaned and cut.
 */
public final class ReplyParser {

    private static final String LEGACY_STOP = "STOP MESSAGING";

    private ReplyParser() {
    }

    /** @throws IOException when the answer is an error object instead of text instead of an answer */
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
                // broken JSON, most often cut off by the output token limit: rescue what was said, never post raw JSON
                return salvage(content, botName, maxLength);
            }
        }
        String plain = Text.singleLine(content).replaceAll("^[\"'\\s]+|[\"'.!\\s]+$", "");
        if (plain.equalsIgnoreCase(LEGACY_STOP)) return Reply.SLEEP;
        return say(content, botName, maxLength);
    }

    private static final java.util.regex.Pattern TEXT_FIELD =
            java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"");
    private static final java.util.regex.Pattern ACTION_SLEEP =
            java.util.regex.Pattern.compile("\"action\"\\s*:\\s*\"(sleep|stop)\"", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Text of a JSON answer that is cut off or malformed: the {@code text} value up to where it ends, else nothing. */
    private static Reply salvage(String content, String botName, int maxLength) {
        if (ACTION_SLEEP.matcher(content).find()) return Reply.SLEEP;
        var m = TEXT_FIELD.matcher(content);
        if (!m.find()) return Reply.NONE;
        StringBuilder out = new StringBuilder();
        for (int i = m.end(); i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') break;
            if (c != '\\' || i + 1 >= content.length()) {
                out.append(c);
                continue;
            }
            char n = content.charAt(++i);
            switch (n) {
                case 'n', 't', 'r' -> out.append(' ');
                case 'u' -> {
                    if (i + 4 < content.length()) {
                        try {
                            out.append((char) Integer.parseInt(content.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            return say(out.toString(), botName, maxLength);
                        }
                    }
                }
                default -> out.append(n);
            }
        }
        return say(out.toString(), botName, maxLength);
    }

    private static Reply fromAction(JsonObject o, String botName, int maxLength) {
        String action = o.get("action").isJsonPrimitive() ? o.get("action").getAsString().strip().toLowerCase(Locale.ROOT) : "";
        return switch (action) {
            case "sleep", "stop" -> Reply.SLEEP;
            case "say" -> o.has("text") && o.get("text").isJsonPrimitive()
                    ? say(o.get("text").getAsString(), botName, maxLength) : Reply.NONE;
            case "skip", "pass", "silent", "none" -> Reply.NONE; // the model chose not to join this exchange
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

    /** The answer text inside a JSON string or object; the raw body when it is not JSON at all. */
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
