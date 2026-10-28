package me.skerik.aifriend.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a chat line addresses the bot and extracts the question. Matching ignores case and diacritics
 * ("Přítel" matches "pritel"). Recognised forms, for the bot name and every extra trigger word:
 * <ul>
 *   <li>{@code @Name question} (anywhere in the line; the mention is cut out of the question)</li>
 *   <li>{@code Name, question} / {@code Name: question} at the start of the line</li>
 *   <li>optionally ({@code react-anywhere}) the name as a whole word anywhere; the question is then the whole line</li>
 * </ul>
 * Names with spaces also match without them ({@code @AIFriend}). Immutable and thread-safe.
 */
public final class MentionDetector {

    /** The question may be empty ("@Bot" alone): the caller then answers from the recent chat. */
    public record Mention(String question) {
    }

    /** Config section {@code mentions}. Default: off. {@code directAnswer}: a mention triggers an immediate reply. */
    public record Config(boolean enabled, boolean directAnswer, List<String> triggers, boolean reactAnywhere) {

        public Config {
            triggers = List.copyOf(triggers);
        }

        public static Config defaults() {
            return new Config(false, true, List.of(), false);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false), Cfg.bool(s, "direct-answer", true),
                    Cfg.strings(s, "triggers"), Cfg.bool(s, "react-anywhere", false));
        }
    }

    private record Folded(String text, int[] origEnd) {
        /** Index in the original string just after the character that produced folded position {@code i}. */
        int end(int i) {
            return origEnd[i];
        }
    }

    private static final Pattern LEADING_PUNCT = Pattern.compile("^[\\s,:;\\-–—]+");

    private final Config config;
    private final List<String> names;

    public MentionDetector(String botName, Config config) {
        this.config = config;
        Set<String> set = new LinkedHashSet<>();
        List<String> raw = new ArrayList<>();
        raw.add(botName);
        raw.addAll(config.triggers());
        for (String r : raw) {
            String f = fold(ChatText.singleLine(r)).text();
            if (f.isEmpty()) continue;
            set.add(f);
            String compact = f.replace(" ", "");
            if (!compact.isEmpty()) set.add(compact);
        }
        this.names = set.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    public Optional<Mention> detect(String chatLine) {
        if (!config.enabled()) return Optional.empty();
        String line = ChatText.singleLine(chatLine);
        if (line.isEmpty()) return Optional.empty();
        Folded f = fold(line);
        String t = f.text();
        for (String name : names) {
            Optional<Mention> m = prefix(line, t, f, name);
            if (m.isPresent()) return m;
            m = at(line, t, f, name);
            if (m.isPresent()) return m;
        }
        if (config.reactAnywhere()) {
            for (String name : names) {
                if (word(t, name, 0) >= 0) return Optional.of(new Mention(line));
            }
        }
        return Optional.empty();
    }

    private Optional<Mention> prefix(String line, String t, Folded f, String name) {
        int start = t.startsWith("@") ? 1 : 0;
        if (!t.startsWith(name, start)) return Optional.empty();
        int end = start + name.length();
        if (end >= t.length()) return start == 1 ? Optional.of(new Mention("")) : Optional.empty();
        char next = t.charAt(end);
        if (start == 1 ? Character.isLetterOrDigit(next) : (next != ',' && next != ':')) return Optional.empty();
        return Optional.of(new Mention(clean(line.substring(f.end(end - 1)))));
    }

    private Optional<Mention> at(String line, String t, Folded f, String name) {
        for (int i = t.indexOf('@'); i >= 0; i = t.indexOf('@', i + 1)) {
            if (i > 0 && Character.isLetterOrDigit(t.charAt(i - 1))) continue;
            int end = i + 1 + name.length();
            if (!t.startsWith(name, i + 1)) continue;
            if (end < t.length() && Character.isLetterOrDigit(t.charAt(end))) continue;
            String before = i == 0 ? "" : line.substring(0, f.end(i - 1)).strip();
            String after = clean(line.substring(f.end(end - 1)));
            String q = before.isEmpty() ? after : (after.isEmpty() ? before : before + " " + after);
            return Optional.of(new Mention(q));
        }
        return Optional.empty();
    }

    /** Index of {@code name} as a whole word in {@code t}, or -1. */
    private static int word(String t, String name, int from) {
        for (int i = t.indexOf(name, from); i >= 0; i = t.indexOf(name, i + 1)) {
            int end = i + name.length();
            boolean left = i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1));
            boolean right = end >= t.length() || !Character.isLetterOrDigit(t.charAt(end));
            if (left && right) return i;
        }
        return -1;
    }

    private static String clean(String s) {
        return LEADING_PUNCT.matcher(s).replaceFirst("").strip();
    }

    /** Lower case, accents removed; remembers where each folded character ended in the original text. */
    private static Folded fold(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int[] ends = new int[s.length() * 4 + 4];
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            String f = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
            for (int k = 0; k < f.length(); k++) {
                ends[out.length()] = i;
                out.append(f.charAt(k));
            }
        }
        return new Folded(out.toString(), ends);
    }
}
