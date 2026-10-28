package me.skerik.aifriend.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Builds the short "event" lines the bot may react to (the line is context for the AI, not text shown to players)
 * and keeps a global cooldown so game events cannot spam the bot. Thread-safe, time is passed in, no Bukkit types.
 */
public final class EventPrompts {

    public enum Type { DEATH, ADVANCEMENT, FIRST_JOIN, BOSS_KILLED, EXPLOSION }

    public enum Boss {
        ENDER_DRAGON("Ender Dragon"), WITHER("Wither");

        private final String label;

        Boss(String label) {
            this.label = label;
        }
    }

    /** A game event as plain values. Use the factories; texts are sanitised when the line is built. */
    public record GameEvent(Type type, String player, String detail, double power, @Nullable Boss boss) {

        /** {@code cause} e.g. "slain by Zombie" or "fell from a high place" (any text, may be empty). */
        public static GameEvent death(String player, String cause) {
            return new GameEvent(Type.DEATH, player, cause, 0, null);
        }

        public static GameEvent advancement(String player, String advancementName) {
            return new GameEvent(Type.ADVANCEMENT, player, advancementName, 0, null);
        }

        public static GameEvent firstJoin(String player) {
            return new GameEvent(Type.FIRST_JOIN, player, "", 0, null);
        }

        /** {@code killer} may be empty when unknown. */
        public static GameEvent bossKilled(Boss boss, String killer) {
            return new GameEvent(Type.BOSS_KILLED, killer, "", 0, boss);
        }

        /** {@code source} e.g. "Creeper" or "TNT" (may be empty); {@code power} is the explosion yield. */
        public static GameEvent explosion(String source, double power) {
            return new GameEvent(Type.EXPLOSION, "", source, power, null);
        }
    }

    /** Config section {@code events}. Default: off. The master switch gates everything. */
    public record Config(boolean enabled, int cooldownSeconds, boolean death, boolean advancement, boolean firstJoin,
                         boolean bossKill, boolean explosion, double explosionMinPower) {

        public static Config defaults() {
            return new Config(false, 30, true, true, true, true, false, 6);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "cooldown-seconds", 0, 3600, 30, log),
                    Cfg.bool(s, "death", true), Cfg.bool(s, "advancement", true), Cfg.bool(s, "first-join", true),
                    Cfg.bool(s, "boss-kill", true), Cfg.bool(s, "explosion", false),
                    Cfg.boundedDouble(s, "explosion-min-power", 1, 1000, 6, log));
        }
    }

    private static final int MAX_NAME = 32;
    private static final int MAX_DETAIL = 100;

    private final Config config;
    private @Nullable Instant last;

    public EventPrompts(Config config) {
        this.config = config;
    }

    /** True when this event type is switched on (does not look at the cooldown or the explosion size). */
    public boolean isEnabled(Type type) {
        if (!config.enabled()) return false;
        return switch (type) {
            case DEATH -> config.death();
            case ADVANCEMENT -> config.advancement();
            case FIRST_JOIN -> config.firstJoin();
            case BOSS_KILLED -> config.bossKill();
            case EXPLOSION -> config.explosion();
        };
    }

    /**
     * The line for this event when the bot may react now, otherwise empty. A returned line starts the global
     * cooldown (0 = none); an event that is switched off, too small or blocked by the cooldown changes nothing.
     */
    public synchronized Optional<String> accept(GameEvent event, Instant now) {
        if (!isEnabled(event.type())) return Optional.empty();
        if (event.type() == Type.EXPLOSION && event.power() < config.explosionMinPower()) return Optional.empty();
        if (last != null && config.cooldownSeconds() > 0
                && Duration.between(last, now).compareTo(Duration.ofSeconds(config.cooldownSeconds())) < 0) {
            return Optional.empty();
        }
        last = now;
        return Optional.of(describe(event));
    }

    /** The line without any switch or cooldown (for {@code /aifriend simulate} and tests). */
    public static String describe(GameEvent e) {
        String player = name(e.player(), "A player");
        String detail = ChatText.cut(ChatText.singleLine(e.detail()), MAX_DETAIL);
        return switch (e.type()) {
            case DEATH -> "[event] " + player + " died" + (detail.isEmpty() ? "." : ": " + detail + ".");
            case ADVANCEMENT -> "[event] " + player + " earned the advancement \"" + detail + "\".";
            case FIRST_JOIN -> "[event] " + player + " joined the server for the first time.";
            case BOSS_KILLED -> {
                String boss = e.boss() == null ? "A boss" : "The " + e.boss().label;
                String killer = ChatText.singleLine(e.player());
                yield "[event] " + boss + " was defeated" + (killer.isEmpty() ? "." : " by " + name(killer, "a player") + ".");
            }
            case EXPLOSION -> "[event] A huge explosion (power " + String.format(Locale.ROOT, "%.0f", e.power()) + ")"
                    + (detail.isEmpty() ? "" : " caused by " + detail) + " just happened.";
        };
    }

    private static String name(String raw, String fallback) {
        String s = ChatText.cut(ChatText.singleLine(raw), MAX_NAME);
        return s.isEmpty() ? fallback : s;
    }
}
