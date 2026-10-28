package me.skerik.aifriend.chat;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * The bot's mood: one number from -100 to 100 that game events push around and that fades back to 0 with a half-life.
 * Pure arithmetic, no Bukkit types; time is passed in. The mood is only a hint in the system prompt, it never
 * changes what the bot is allowed to do. Thread-safe.
 */
public final class Mood {

    public enum Level {
        THRILLED("thrilled and excited"), CHEERFUL("cheerful"), CALM("calm"), UNEASY("a bit uneasy and worried"),
        UPSET("upset and shaken");

        private final String description;

        Level(String description) {
            this.description = description;
        }

        /** Plain English for the prompt. */
        public String description() {
            return description;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Config section {@code mood}. Default: off. */
    public record Config(boolean enabled, int halfLifeMinutes) {

        public static Config defaults() {
            return new Config(false, 30);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false), Cfg.bounded(s, "half-life-minutes", 1, 1440, 30, log));
        }
    }

    static final double ADVANCEMENT = 8;
    static final double FIRST_JOIN = 10;
    static final double BOSS_KILLED = 35;
    static final double DEATH = -12;
    static final double EXPLOSION = -6;
    /** Three deaths within {@link #MASS_DEATH_WINDOW_MS} are a bad moment for everybody. */
    static final double MASS_DEATH = -25;
    static final int MASS_DEATH_COUNT = 3;
    static final long MASS_DEATH_WINDOW_MS = 5 * 60_000L;
    /** Three big explosions within {@link #GRIEF_WINDOW_MS} look like griefing. */
    static final double GRIEF = -20;
    static final int GRIEF_COUNT = 3;
    static final long GRIEF_WINDOW_MS = 2 * 60_000L;
    /** Smaller explosions (fireballs, ...) do not count. A creeper is 3, TNT is 4. */
    static final double MIN_EXPLOSION_POWER = 3;

    private final Config config;
    private final ArrayDeque<Long> deaths = new ArrayDeque<>();
    private final ArrayDeque<Long> explosions = new ArrayDeque<>();
    private double value;
    private long updatedAt;

    public Mood(Config config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** Lets a game event change the mood. Does nothing while the feature is off. */
    public synchronized void record(EventPrompts.GameEvent event, long nowMs) {
        if (!config.enabled()) return;
        decay(nowMs);
        switch (event.type()) {
            case ADVANCEMENT -> add(ADVANCEMENT);
            case FIRST_JOIN -> add(FIRST_JOIN);
            case BOSS_KILLED -> add(BOSS_KILLED);
            case DEATH -> {
                add(DEATH);
                if (cluster(deaths, nowMs, MASS_DEATH_WINDOW_MS, MASS_DEATH_COUNT)) add(MASS_DEATH);
            }
            case EXPLOSION -> {
                if (event.power() < MIN_EXPLOSION_POWER) return;
                add(EXPLOSION);
                if (cluster(explosions, nowMs, GRIEF_WINDOW_MS, GRIEF_COUNT)) add(GRIEF);
            }
        }
    }

    /** -100..100, after fading to {@code nowMs}. */
    public synchronized int score(long nowMs) {
        decay(nowMs);
        return (int) Math.round(value);
    }

    public synchronized Level level(long nowMs) {
        int s = score(nowMs);
        if (s >= 60) return Level.THRILLED;
        if (s >= 25) return Level.CHEERFUL;
        if (s > -25) return Level.CALM;
        if (s > -60) return Level.UNEASY;
        return Level.UPSET;
    }

    private void add(double delta) {
        value = Math.max(-100, Math.min(100, value + delta));
    }

    private void decay(long nowMs) {
        if (updatedAt != 0 && nowMs > updatedAt) {
            double halfLifeMs = config.halfLifeMinutes() * 60_000.0;
            value *= Math.pow(0.5, (nowMs - updatedAt) / halfLifeMs);
        }
        updatedAt = nowMs;
    }

    /** Records the moment; true (and the record is cleared, so one cluster counts once) when {@code count} fit in the window. */
    private static boolean cluster(ArrayDeque<Long> times, long nowMs, long windowMs, int count) {
        times.addLast(nowMs);
        while (!times.isEmpty() && nowMs - times.peekFirst() > windowMs) times.pollFirst();
        if (times.size() >= count) {
            times.clear();
            return true;
        }
        return false;
    }
}
