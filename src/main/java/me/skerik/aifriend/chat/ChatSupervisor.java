package me.skerik.aifriend.chat;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Friendly chat supervision: spots shouting (caps lock), spam and swearing and says which one it was, so the plugin can
 * send that player a gentle private note. It never punishes (no kick, mute or ban), never stores what players wrote
 * (spam detection keeps hashes of the last few lines in memory for a few seconds) and warns at most once per
 * {@code cooldown-seconds} per player and kind. Pure logic, time is passed in. Thread-safe.
 */
public final class ChatSupervisor {

    public enum Issue { LANGUAGE, SPAM, CAPS }

    /** Config section {@code supervision}. Default: off. */
    public record Config(boolean enabled, boolean caps, boolean spam, boolean swearing, List<String> badWords,
                         int capsMinLetters, int capsPercent, int cooldownSeconds) {

        public Config {
            badWords = List.copyOf(badWords);
        }

        public static Config defaults() {
            return new Config(false, true, true, true, List.of(), 12, 70, 60);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false), Cfg.bool(s, "caps", true), Cfg.bool(s, "spam", true),
                    Cfg.bool(s, "swearing", true), Cfg.strings(s, "bad-words"),
                    Cfg.bounded(s, "caps-min-letters", 4, 200, 12, log),
                    Cfg.bounded(s, "caps-percent", 50, 100, 70, log),
                    Cfg.bounded(s, "cooldown-seconds", 0, 3600, 60, log));
        }
    }

    static final long SPAM_WINDOW_MS = 10_000;
    static final int SPAM_SAME_LINES = 3;
    static final int SPAM_ANY_LINES = 6;
    static final int MAX_TRACKED_PLAYERS = 2000;

    private record Seen(long at, int hash) {
    }

    private final Config config;
    private final Set<String> bad;
    private final ConcurrentHashMap<UUID, ArrayDeque<Seen>> recent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> warned = new ConcurrentHashMap<>();

    public ChatSupervisor(Config config) {
        this.config = config;
        Set<String> words = new HashSet<>();
        for (String w : config.badWords()) {
            String n = collapse(fold(w));
            if (!n.isEmpty()) words.add(n);
        }
        this.bad = Set.copyOf(words);
    }

    public boolean enabled() {
        return config.enabled();
    }

    /**
     * The problem with this line, if there is one and the player has not just been told about it. Every line must be
     * passed in (also harmless ones) because spam is about the rhythm of a player's lines.
     */
    public Optional<Issue> inspect(UUID player, String text, long nowMs) {
        if (!config.enabled()) return Optional.empty();
        boolean spam = config.spam() && looksLikeSpam(player, text, nowMs);
        Issue found = config.swearing() && swears(text) ? Issue.LANGUAGE : spam ? Issue.SPAM
                : config.caps() && shouting(text) ? Issue.CAPS : null;
        if (found == null) return Optional.empty();
        return allowed(player, found, nowMs) ? Optional.of(found) : Optional.empty();
    }

    private boolean allowed(UUID player, Issue issue, long nowMs) {
        if (warned.size() > MAX_TRACKED_PLAYERS) warned.clear();
        String key = player + ":" + issue;
        Long last = warned.get(key);
        if (last != null && nowMs - last < config.cooldownSeconds() * 1000L) return false;
        warned.put(key, nowMs);
        return true;
    }

    private boolean looksLikeSpam(UUID player, String text, long nowMs) {
        if (recent.size() > MAX_TRACKED_PLAYERS) recent.clear();
        ArrayDeque<Seen> seen = recent.computeIfAbsent(player, k -> new ArrayDeque<>());
        int hash = collapse(fold(text).replaceAll("[^\\p{L}\\p{N}]+", "")).hashCode();
        synchronized (seen) {
            while (!seen.isEmpty() && nowMs - seen.peekFirst().at() > SPAM_WINDOW_MS) seen.pollFirst();
            seen.addLast(new Seen(nowMs, hash));
            if (seen.size() >= SPAM_ANY_LINES) return true;
            int same = 0;
            for (Seen s : seen) if (s.hash() == hash) same++;
            return same >= SPAM_SAME_LINES;
        }
    }

    boolean shouting(String text) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters >= config.capsMinLetters() && upper * 100 >= letters * config.capsPercent();
    }

    boolean swears(String text) {
        if (bad.isEmpty()) return false;
        for (String token : fold(text).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty() && bad.contains(collapse(token))) return true;
        }
        return false;
    }

    /** Lower case, no accents, the usual look-alike digits and symbols turned back into letters. */
    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            out.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return out.toString();
    }

    /** "kuuurva" and "kurva" are the same word: repeated letters count once. */
    static String collapse(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (out.isEmpty() || out.charAt(out.length() - 1) != c) out.append(c);
        }
        return out.toString();
    }
}
