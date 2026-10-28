package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final Instant T0 = LocalDateTime.of(2026, 3, 10, 12, 0).atZone(PRAGUE).toInstant();

    private static RateLimiter limiter(int cd, int pc, long pt, int gc, long gt) {
        return new RateLimiter(new RateLimiter.Config(true, cd, pc, pt, gc, gt), PRAGUE);
    }

    @Test
    void disabledAllowsEverything() {
        var l = new RateLimiter(RateLimiter.Config.defaults(), PRAGUE);
        for (int i = 0; i < 100; i++) assertTrue(l.tryAcquire(A, T0).allowed());
    }

    @Test
    void zeroMeansUnlimited() {
        var l = limiter(0, 0, 0, 0, 0);
        for (int i = 0; i < 100; i++) assertTrue(l.tryAcquire(A, T0).allowed());
        var r = l.remaining(A, T0);
        assertEquals(RateLimiter.UNLIMITED, r.playerCalls());
        assertEquals(RateLimiter.UNLIMITED, r.globalTokens());
    }

    @Test
    void cooldownBoundary() {
        var l = limiter(10, 0, 0, 0, 0);
        assertTrue(l.tryAcquire(A, T0).allowed());
        var d = l.tryAcquire(A, T0.plusSeconds(9));
        assertFalse(d.allowed());
        assertEquals(RateLimiter.Reason.COOLDOWN, d.reason());
        assertEquals(Duration.ofSeconds(1), d.retryAfter());
        assertTrue(l.tryAcquire(A, T0.plusSeconds(10)).allowed()); // exactly at the boundary
        assertTrue(l.tryAcquire(B, T0.plusSeconds(10)).allowed()); // other player unaffected
    }

    @Test
    void deniedCallDoesNotRestartCooldownOrCount() {
        var l = limiter(10, 0, 0, 0, 0);
        l.tryAcquire(A, T0);
        l.tryAcquire(A, T0.plusSeconds(5));
        assertTrue(l.tryAcquire(A, T0.plusSeconds(10)).allowed());
    }

    @Test
    void playerDailyCalls() {
        var l = limiter(0, 2, 0, 0, 0);
        assertTrue(l.tryAcquire(A, T0).allowed());
        assertTrue(l.tryAcquire(A, T0).allowed());
        var d = l.tryAcquire(A, T0);
        assertEquals(RateLimiter.Reason.PLAYER_CALLS, d.reason());
        assertTrue(l.tryAcquire(B, T0).allowed());
        assertEquals(0, l.remaining(A, T0).playerCalls());
        assertEquals(1, l.remaining(B, T0).playerCalls());
    }

    @Test
    void tokenLimitsUseRecordedUsage() {
        var l = limiter(0, 0, 100, 0, 150);
        assertTrue(l.tryAcquire(A, T0).allowed());
        l.recordUsage(A, 100, T0);
        assertEquals(RateLimiter.Reason.PLAYER_TOKENS, l.tryAcquire(A, T0).reason());
        assertTrue(l.tryAcquire(B, T0).allowed());
        l.recordUsage(B, 50, T0);
        assertEquals(RateLimiter.Reason.GLOBAL_TOKENS, l.tryAcquire(B, T0).reason());
        assertEquals(0, l.remaining(B, T0).globalTokens());
    }

    @Test
    void globalCallsAndNullPlayer() {
        var l = limiter(30, 0, 0, 2, 0);
        assertTrue(l.tryAcquire(null, T0).allowed());
        assertTrue(l.tryAcquire(null, T0).allowed()); // no cooldown for a null player
        assertEquals(RateLimiter.Reason.GLOBAL_CALLS, l.tryAcquire(A, T0).reason());
    }

    @Test
    void globalOnlyRecordUsage() {
        var l = limiter(0, 0, 0, 0, 10);
        l.recordUsage(10, T0);
        assertEquals(RateLimiter.Reason.GLOBAL_TOKENS, l.tryAcquire(A, T0).reason());
        l.recordUsage(-5, T0); // ignored
        assertEquals(0, l.remaining(A, T0).globalTokens());
    }

    @Test
    void resetsAtLocalMidnight() {
        var l = limiter(0, 1, 0, 0, 0);
        Instant late = LocalDateTime.of(2026, 3, 10, 23, 59, 59).atZone(PRAGUE).toInstant();
        Instant midnight = LocalDateTime.of(2026, 3, 11, 0, 0).atZone(PRAGUE).toInstant();
        assertTrue(l.tryAcquire(A, late).allowed());
        var d = l.tryAcquire(A, late.plusMillis(500));
        assertFalse(d.allowed());
        assertEquals(Duration.ofMillis(500), d.retryAfter());
        assertTrue(l.tryAcquire(A, midnight).allowed());
        assertEquals(midnight.plus(Duration.ofDays(1)), l.remaining(A, midnight).resetsAt());
    }

    @Test
    void midnightFollowsZoneAcrossDst() {
        // 2026-03-29 is the spring-forward day in Prague; the next midnight is still local midnight
        Instant t = LocalDateTime.of(2026, 3, 29, 1, 0).atZone(PRAGUE).toInstant();
        var l = limiter(0, 1, 0, 0, 0);
        l.tryAcquire(A, t);
        Instant next = LocalDateTime.of(2026, 3, 30, 0, 0).atZone(PRAGUE).toInstant();
        assertEquals(next, l.remaining(A, t).resetsAt());
        assertEquals(Duration.ofHours(22), Duration.between(t, next)); // 01:00 to 24:00 with one hour skipped
    }

    @Test
    void loadValidates() throws Exception {
        var y = new YamlConfiguration();
        y.loadFromString("enabled: true\ncooldown-seconds: -3\nplayer-daily-calls: 20\n");
        var c = RateLimiter.Config.load(y, Logger.getAnonymousLogger());
        assertTrue(c.enabled());
        assertEquals(0, c.cooldownSeconds());
        assertEquals(20, c.playerDailyCalls());
        assertFalse(RateLimiter.Config.load(null, Logger.getAnonymousLogger()).enabled());
    }
}
