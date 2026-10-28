package me.skerik.aifriend.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Decides when a quiet chat is worth a conversation starter. Pure logic, time is passed in. All of these must hold:
 * the feature is on, at least {@code min-players} are online, nobody has written for {@code quiet-minutes}, the cooldown
 * since the last starter is over and the daily maximum is not reached. Every further starter nobody reacted to doubles the
 * cooldown (up to 8 times), so an empty-headed chat is not nagged. {@link #ready} only asks, {@link #commit} counts.
 */
public final class BoredomDetector {

    public enum Kind {
        TOPIC("start a friendly conversation: suggest an interesting topic, or ask the players about their current builds or plans"),
        QUIZ("ask one short Minecraft quiz question and do not give the answer yet"),
        CHALLENGE("suggest a small, harmless in-game challenge, for example a building or exploring challenge"),
        JOKE("tell one short, light joke about Minecraft"),
        FACT("share one interesting Minecraft fact");

        private final String instruction;

        Kind(String instruction) {
            this.instruction = instruction;
        }

        /** The instruction for the system prompt (trusted text, written here, no player data). */
        public String instruction() {
            return instruction;
        }
    }

    /** Config section {@code boredom}. Default: off. */
    public record Config(boolean enabled, int quietMinutes, int cooldownMinutes, int minPlayers, int maxPerDay,
                         Set<Kind> kinds) {

        public Config {
            kinds = kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
        }

        public static Config defaults() {
            return new Config(false, 10, 30, 1, 10, EnumSet.allOf(Kind.class));
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            Set<Kind> kinds = EnumSet.noneOf(Kind.class);
            List<String> names = new ArrayList<>(Cfg.strings(s, "ideas"));
            if (s == null || !s.contains("ideas")) names = List.of("topic", "quiz", "challenge", "joke", "fact");
            for (String n : names) {
                try {
                    kinds.add(Kind.valueOf(n.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    log.warning("boredom.ideas: unknown idea \"" + n + "\" (use topic, quiz, challenge, joke, fact); skipped");
                }
            }
            if (kinds.isEmpty() && s != null && s.contains("ideas")) {
                log.warning("boredom.ideas has no valid entry; the bot will not start conversations");
            }
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "quiet-minutes", 1, 1440, 10, log),
                    Cfg.bounded(s, "cooldown-minutes", 1, 1440, 30, log),
                    Cfg.bounded(s, "min-players", 1, 200, 1, log),
                    Cfg.bounded(s, "max-per-day", 1, 200, 10, log),
                    kinds);
        }
    }

    static final int MAX_BACKOFF_STEPS = 3;

    private final Config config;
    private final ZoneId zone;
    private long lastActivity;
    private long lastStarter = Long.MIN_VALUE;
    private int unanswered;
    private long day = Long.MIN_VALUE;
    private int today;

    public BoredomDetector(Config config, ZoneId zone) {
        this.config = config;
        this.zone = zone;
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** Something happened in the chat (the bot's own message, an empty server): the quiet timer starts again. */
    public synchronized void touch(long nowMs) {
        lastActivity = nowMs;
    }

    /** A player wrote in public chat: the quiet timer restarts and the starters were not ignored. */
    public synchronized void playerChatted(long nowMs) {
        lastActivity = nowMs;
        unanswered = 0;
    }

    /** Minutes since the last activity (0 before the first one). */
    public synchronized long quietMinutes(long nowMs) {
        return lastActivity == 0 ? 0 : Math.max(0, (nowMs - lastActivity) / 60_000L);
    }

    /** The kind of starter to use now, or empty. Changes nothing; call {@link #commit} when a request is really made. */
    public synchronized Optional<Kind> ready(long nowMs, int onlinePlayers, RandomGenerator random) {
        if (!config.enabled() || config.kinds().isEmpty() || onlinePlayers < config.minPlayers()) return Optional.empty();
        if (lastActivity == 0) {
            lastActivity = nowMs; // the quiet time starts counting now, not at an arbitrary moment
            return Optional.empty();
        }
        if (nowMs - lastActivity < config.quietMinutes() * 60_000L) return Optional.empty();
        long cooldown = config.cooldownMinutes() * 60_000L << Math.min(Math.max(unanswered - 1, 0), MAX_BACKOFF_STEPS);
        if (lastStarter != Long.MIN_VALUE && nowMs - lastStarter < cooldown) return Optional.empty();
        rollDay(nowMs);
        if (today >= config.maxPerDay()) return Optional.empty();
        List<Kind> kinds = config.kinds().stream().sorted().toList();
        return Optional.of(kinds.get(random.nextInt(kinds.size())));
    }

    /** Counts one starter (cooldown, daily maximum, "nobody answered" streak). */
    public synchronized void commit(long nowMs) {
        rollDay(nowMs);
        lastStarter = nowMs;
        lastActivity = nowMs;
        unanswered++;
        today++;
    }

    private void rollDay(long nowMs) {
        long d = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).toEpochDay();
        if (d != day) {
            day = d;
            today = 0;
        }
    }

    /** The text for the system prompt: what to do now (trusted, no player data). */
    public static String prompt(Kind kind, long quietMinutes, int online) {
        return "Nobody has written in the chat for " + quietMinutes + " minutes and " + online
                + (online == 1 ? " player is" : " players are") + " online. Break the silence once: " + kind.instruction() + ".";
    }
}
