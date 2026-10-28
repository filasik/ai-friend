package me.skerik.aifriend.chat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Per-player cooldown plus per-player and global daily limits (calls and tokens). Thread-safe, time is always passed in.
 * The day ends at midnight in the injected zone. A limit of 0 means unlimited.
 * A {@code null} player id (e.g. an event reaction) is only checked against the global limits.
 */
public final class RateLimiter {

    public static final long UNLIMITED = -1;

    public enum Reason { OK, COOLDOWN, PLAYER_CALLS, PLAYER_TOKENS, GLOBAL_CALLS, GLOBAL_TOKENS }

    /** {@code retryAfter} is the cooldown left, or the time until midnight for a daily limit; zero when allowed. */
    public record Decision(boolean allowed, Reason reason, Duration retryAfter) {
    }

    /** -1 ({@link #UNLIMITED}) = no limit. {@code cooldown} is zero when the player may ask now. */
    public record Remaining(long playerCalls, long playerTokens, long globalCalls, long globalTokens,
                            Duration cooldown, Instant resetsAt) {
    }

    /** Config section {@code limits}. Default: off. Every number: 0 = unlimited. */
    public record Config(boolean enabled, int cooldownSeconds, int playerDailyCalls, long playerDailyTokens,
                         int globalDailyCalls, long globalDailyTokens) {

        public static Config defaults() {
            return new Config(false, 0, 0, 0, 0, 0);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "cooldown-seconds", 0, 86400, 0, log),
                    Cfg.bounded(s, "player-daily-calls", 0, 1_000_000, 0, log),
                    Cfg.bounded(s, "player-daily-tokens", 0, 1_000_000_000, 0, log),
                    Cfg.bounded(s, "global-daily-calls", 0, 10_000_000, 0, log),
                    Cfg.bounded(s, "global-daily-tokens", 0, 1_000_000_000, 0, log));
        }
    }

    private static final class PlayerState {
        @Nullable Instant last;
        long calls;
        long tokens;
    }

    private final Config config;
    private final ZoneId zone;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private LocalDate day = LocalDate.MIN;
    private long globalCalls;
    private long globalTokens;

    public RateLimiter(Config config, ZoneId zone) {
        this.config = config;
        this.zone = zone;
    }

    /** Checks the limits and, when allowed, counts the call and starts the cooldown. Denied calls change nothing. */
    public synchronized Decision tryAcquire(@Nullable UUID player, Instant now) {
        if (!config.enabled()) return new Decision(true, Reason.OK, Duration.ZERO);
        rollover(now);
        PlayerState st = player == null ? null : players.get(player);
        if (st != null && config.cooldownSeconds() > 0 && st.last != null) {
            Duration left = Duration.ofSeconds(config.cooldownSeconds()).minus(Duration.between(st.last, now));
            if (left.isPositive()) return new Decision(false, Reason.COOLDOWN, left);
        }
        if (st != null) {
            if (reached(st.calls, config.playerDailyCalls())) return denied(Reason.PLAYER_CALLS, now);
            if (reached(st.tokens, config.playerDailyTokens())) return denied(Reason.PLAYER_TOKENS, now);
        }
        if (reached(globalCalls, config.globalDailyCalls())) return denied(Reason.GLOBAL_CALLS, now);
        if (reached(globalTokens, config.globalDailyTokens())) return denied(Reason.GLOBAL_TOKENS, now);

        globalCalls++;
        if (player != null) {
            PlayerState s = players.computeIfAbsent(player, k -> new PlayerState());
            s.calls++;
            s.last = now;
        }
        return new Decision(true, Reason.OK, Duration.ZERO);
    }

    /** Adds the tokens of a finished request to the player (may be null) and the global day counter. */
    public synchronized void recordUsage(@Nullable UUID player, long tokens, Instant now) {
        if (tokens <= 0) return;
        rollover(now);
        globalTokens += tokens;
        if (player != null) players.computeIfAbsent(player, k -> new PlayerState()).tokens += tokens;
    }

    /** Global-only variant of {@link #recordUsage(UUID, long, Instant)}. */
    public void recordUsage(long tokens, Instant now) {
        recordUsage(null, tokens, now);
    }

    public synchronized Remaining remaining(@Nullable UUID player, Instant now) {
        rollover(now);
        PlayerState st = player == null ? null : players.get(player);
        long pCalls = left(st == null ? 0 : st.calls, config.playerDailyCalls());
        long pTokens = left(st == null ? 0 : st.tokens, config.playerDailyTokens());
        Duration cooldown = Duration.ZERO;
        if (st != null && st.last != null && config.cooldownSeconds() > 0) {
            Duration d = Duration.ofSeconds(config.cooldownSeconds()).minus(Duration.between(st.last, now));
            if (d.isPositive()) cooldown = d;
        }
        return new Remaining(pCalls, pTokens, left(globalCalls, config.globalDailyCalls()),
                left(globalTokens, config.globalDailyTokens()), cooldown, nextMidnight(now));
    }

    private Decision denied(Reason reason, Instant now) {
        return new Decision(false, reason, Duration.between(now, nextMidnight(now)));
    }

    private static boolean reached(long used, long limit) {
        return limit > 0 && used >= limit;
    }

    private static long left(long used, long limit) {
        return limit <= 0 ? UNLIMITED : Math.max(0, limit - used);
    }

    private Instant nextMidnight(Instant now) {
        return now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    private void rollover(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        if (!today.equals(day)) {
            day = today;
            players.clear();
            globalCalls = 0;
            globalTokens = 0;
        }
    }
}
