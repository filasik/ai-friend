package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CostTrackerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Prague");
    private static final Instant T0 = LocalDateTime.of(2026, 5, 1, 10, 0).atZone(ZONE).toInstant();

    @Test
    void disabledRecordsNothing() {
        var c = new CostTracker(CostTracker.Config.defaults(), ZONE);
        assertFalse(c.record(1_000_000, 1_000_000, T0));
        assertEquals(0, c.totals(T0).totalInput());
    }

    @Test
    void computesCost() {
        var c = new CostTracker(new CostTracker.Config(true, 2.0, 10.0, 0), ZONE);
        c.record(500_000, 100_000, T0);
        var t = c.totals(T0);
        assertEquals(500_000, t.todayInput());
        assertEquals(100_000, t.todayOutput());
        assertEquals(2.0, t.todayCost(), 1e-9); // 1.0 + 1.0
        assertEquals(2.0, t.totalCost(), 1e-9);
    }

    @Test
    void warningFiresOncePerDay() {
        var c = new CostTracker(new CostTracker.Config(true, 1.0, 1.0, 1.0), ZONE);
        assertFalse(c.record(400_000, 0, T0));
        assertTrue(c.record(600_000, 0, T0)); // reaches exactly 1.0
        assertFalse(c.record(1_000_000, 0, T0));
        Instant tomorrow = T0.plus(java.time.Duration.ofDays(1));
        assertFalse(c.record(100, 0, tomorrow));
        assertTrue(c.record(1_000_000, 0, tomorrow));
    }

    @Test
    void midnightResetsTodayButKeepsTotal() {
        var c = new CostTracker(new CostTracker.Config(true, 1.0, 1.0, 0), ZONE);
        Instant late = LocalDateTime.of(2026, 5, 1, 23, 59, 59).atZone(ZONE).toInstant();
        c.record(100, 50, late);
        var t = c.totals(late.plusSeconds(2));
        assertEquals(0, t.todayInput());
        assertEquals(100, t.totalInput());
        assertEquals(50, t.totalOutput());
    }

    @Test
    void negativeTokensIgnoredAndZeroThresholdNeverWarns() {
        var c = new CostTracker(new CostTracker.Config(true, 1.0, 1.0, 0), ZONE);
        assertFalse(c.record(-5, -5, T0));
        assertFalse(c.record(Integer.MAX_VALUE, Integer.MAX_VALUE, T0));
        assertEquals(0.0, new CostTracker(new CostTracker.Config(true, 1, 1, 0), ZONE).totals(T0).todayCost());
    }
}
