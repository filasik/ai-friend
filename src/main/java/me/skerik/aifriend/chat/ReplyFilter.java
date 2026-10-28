package me.skerik.aifriend.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Last line of defence between the AI and the chat. Always (even with {@code enabled: false}): colour codes and
 * control characters are removed, the text is one line and at most {@code maxLength} characters (word-boundary cut).
 * With {@code enabled: true} also: banned words (case-insensitive, whole words, Unicode aware) and URLs.
 * Immutable and thread-safe.
 */
public final class ReplyFilter {

    public enum BannedMode {
        /** Each banned word becomes {@code ***}. */
        REPLACE,
        /** A reply containing a banned word is dropped completely. */
        DROP
    }

    /** Config section {@code reply-filter}. Default: off (only the always-on sanitising applies). */
    public record Config(boolean enabled, int maxLength, List<String> bannedWords, BannedMode mode, boolean blockUrls) {

        public Config {
            bannedWords = List.copyOf(bannedWords);
        }

        public static Config defaults() {
            return new Config(false, 256, List.of(), BannedMode.REPLACE, false);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            BannedMode mode = BannedMode.REPLACE;
            String m = Cfg.string(s, "banned-mode", "replace").toUpperCase(Locale.ROOT);
            try {
                mode = BannedMode.valueOf(m);
            } catch (IllegalArgumentException e) {
                log.warning("reply-filter.banned-mode must be replace or drop; using replace");
            }
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "max-length", 20, 1000, 256, log),
                    Cfg.strings(s, "banned-words"), mode, Cfg.bool(s, "block-urls", false));
        }

        /** Same filter, different length limit (e.g. the global {@code max-reply-length}). */
        public Config withMaxLength(int max) {
            return new Config(enabled, max, bannedWords, mode, blockUrls);
        }
    }

    private static final String MASK = "***";
    private static final Pattern URL = Pattern.compile(
            "(?:(?:https?|ftp)://|www\\.)\\S+"
                    + "|[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*\\.(?:com|net|org|eu|cz|sk|de|io|gg|me|info|xyz|ru|uk|us|tv|co|app|dev)"
                    + "(?![\\p{L}\\p{N}])(?:/\\S*)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final Config config;
    private final @Nullable Pattern banned;

    public ReplyFilter(Config config) {
        this.config = config;
        if (config.enabled() && !config.bannedWords().isEmpty()) {
            StringBuilder sb = new StringBuilder("(?<![\\p{L}\\p{N}])(?:");
            boolean first = true;
            for (String w : config.bannedWords().stream().sorted((a, b) -> b.length() - a.length()).toList()) {
                if (!first) sb.append('|');
                sb.append(Pattern.quote(w));
                first = false;
            }
            sb.append(")(?![\\p{L}\\p{N}])");
            this.banned = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } else {
            this.banned = null;
        }
    }

    /** The text to post, or empty when nothing (usable) remains or the reply must be dropped. */
    public Optional<String> apply(String reply) {
        String text = ChatText.singleLine(reply);
        if (config.enabled()) {
            if (config.blockUrls()) text = URL.matcher(text).replaceAll(" ");
            if (banned != null) {
                var m = banned.matcher(text);
                if (config.mode() == BannedMode.DROP) {
                    if (m.find()) return Optional.empty();
                } else {
                    text = m.replaceAll(MASK);
                }
            }
            text = ChatText.singleLine(text);
        }
        if (text.isEmpty()) return Optional.empty();
        return Optional.of(ChatText.cut(text, config.maxLength()));
    }
}
