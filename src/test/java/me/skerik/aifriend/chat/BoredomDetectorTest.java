package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BoredomDetectorTest {

    private static final long MIN = 60_000L;
    private static final long T0 = java.time.Instant.parse("2026-06-15T10:00:00Z").toEpochMilli();

    private static BoredomDetector.Config cfg(int minPlayers, int maxPerDay) {
        return new BoredomDetector.Config(true, 10, 30, minPlayers, maxPerDay, EnumSet.allOf(BoredomDetector.Kind.class));
    }

    private static BoredomDetector detector(int minPlayers, int maxPerDay) {
        BoredomDetector d = new BoredomDetector(cfg(minPlayers, maxPerDay), ZoneOffset.UTC);
        d.touch(T0);
        return d;
    }

    private static Optional<BoredomDetector.Kind> ready(BoredomDetector d, long at, int online) {
        return d.ready(at, online, new Random(1));
    }

    @Test
    void offByDefault() {
        BoredomDetector d = new BoredomDetector(BoredomDetector.Config.defaults(), ZoneOffset.UTC);
        d.touch(T0);
        assertTrue(ready(d, T0 + 999 * MIN, 5).isEmpty());
    }

    @Test
    void needsAtLeastOnePlayerOnlineAndAtLeastMinPlayers() {
        BoredomDetector d = detector(1, 10);
        assertTrue(ready(d, T0 + 20 * MIN, 0).isEmpty()); // an empty server is never bored
        assertTrue(ready(d, T0 + 20 * MIN, 1).isPresent());
        BoredomDetector three = detector(3, 10);
        assertTrue(ready(three, T0 + 20 * MIN, 2).isEmpty());
        assertTrue(ready(three, T0 + 20 * MIN, 3).isPresent());
    }

    @Test
    void needsTheQuietTimeToPass() {
        BoredomDetector d = detector(1, 10);
        assertTrue(ready(d, T0 + 9 * MIN, 2).isEmpty());
        assertTrue(ready(d, T0 + 10 * MIN, 2).isPresent());
        d.playerChatted(T0 + 10 * MIN); // somebody spoke: the timer starts again
        assertTrue(ready(d, T0 + 19 * MIN, 2).isEmpty());
        assertTrue(ready(d, T0 + 20 * MIN, 2).isPresent());
    }

    @Test
    void theFirstLookOnlyStartsTheClock() {
        BoredomDetector d = new BoredomDetector(cfg(1, 10), ZoneOffset.UTC);
        assertTrue(ready(d, T0, 2).isEmpty()); // never active before: now is the start of the quiet time
        assertTrue(ready(d, T0 + 9 * MIN, 2).isEmpty());
        assertTrue(ready(d, T0 + 10 * MIN, 2).isPresent());
    }

    @Test
    void askingChangesNothingOnlyCommitCounts() {
        BoredomDetector d = detector(1, 10);
        assertTrue(ready(d, T0 + 20 * MIN, 2).isPresent());
        assertTrue(ready(d, T0 + 20 * MIN, 2).isPresent());
        d.commit(T0 + 20 * MIN);
        assertTrue(ready(d, T0 + 21 * MIN, 2).isEmpty());
    }

    @Test
    void cooldownDoublesWhileNobodyReacts() {
        BoredomDetector d = detector(1, 100);
        long t = T0 + 10 * MIN;
        d.commit(t); // 1st starter, nobody reacts
        assertTrue(ready(d, t + 29 * MIN, 2).isEmpty());
        assertTrue(ready(d, t + 30 * MIN, 2).isPresent());
        t += 30 * MIN;
        d.commit(t); // 2nd: cooldown is now 60 minutes
        assertTrue(ready(d, t + 59 * MIN, 2).isEmpty());
        assertTrue(ready(d, t + 60 * MIN, 2).isPresent());
        t += 60 * MIN;
        d.commit(t); // 3rd: 120
        t += 120 * MIN;
        d.commit(t); // 4th: 240 (the 8x cap is 240 for a 30 minute cooldown)
        assertTrue(ready(d, t + 239 * MIN, 2).isEmpty());
        assertTrue(ready(d, t + 240 * MIN, 2).isPresent());
        d.commit(t + 240 * MIN);
        assertTrue(ready(d, t + 240 * MIN + 239 * MIN, 2).isEmpty()); // still capped at 8x, not 16x
        assertTrue(ready(d, t + 240 * MIN + 240 * MIN, 2).isPresent());
    }

    @Test
    void aPlayerAnsweringResetsTheCooldownGrowth() {
        BoredomDetector d = detector(1, 100);
        long t = T0 + 10 * MIN;
        d.commit(t);
        d.commit(t + 30 * MIN);
        d.playerChatted(t + 31 * MIN);
        // quiet again for 10 minutes and the normal 30 minute cooldown is over
        assertTrue(ready(d, t + 60 * MIN, 2).isPresent());
    }

    @Test
    void dailyMaximumAndNewDay() {
        BoredomDetector d = detector(1, 2);
        long t = T0 + 10 * MIN;
        d.commit(t);
        d.playerChatted(t + MIN);
        d.commit(t + 40 * MIN);
        d.playerChatted(t + 41 * MIN);
        assertTrue(ready(d, t + 100 * MIN, 2).isEmpty()); // two today
        long tomorrow = T0 + 24 * 60 * MIN;
        assertTrue(ready(d, tomorrow, 2).isPresent());
    }

    @Test
    void onlyTheConfiguredKindsAreUsed() {
        BoredomDetector d = new BoredomDetector(new BoredomDetector.Config(true, 10, 30, 1, 10,
                EnumSet.of(BoredomDetector.Kind.QUIZ)), ZoneOffset.UTC);
        d.touch(T0);
        for (int i = 0; i < 20; i++) {
            assertEquals(BoredomDetector.Kind.QUIZ, ready(d, T0 + 20 * MIN, 2).orElseThrow());
        }
        BoredomDetector none = new BoredomDetector(new BoredomDetector.Config(true, 10, 30, 1, 10, Set.of()), ZoneOffset.UTC);
        none.touch(T0);
        assertTrue(ready(none, T0 + 20 * MIN, 2).isEmpty());
    }

    @Test
    void quietMinutes() {
        BoredomDetector d = detector(1, 10);
        assertEquals(7, d.quietMinutes(T0 + 7 * MIN + 5000));
    }

    @Test
    void promptIsTrustedPlainTextWithTheNumbers() {
        String p = BoredomDetector.prompt(BoredomDetector.Kind.QUIZ, 12, 1);
        assertTrue(p.contains("12 minutes") && p.contains("1 player is") && p.contains("quiz"), p);
        assertTrue(BoredomDetector.prompt(BoredomDetector.Kind.JOKE, 5, 4).contains("4 players are"));
    }

    @Test
    void configLoadsIdeasAndRejectsGarbage() {
        Logger log = Logger.getLogger("BoredomTest");
        log.setLevel(Level.OFF);
        YamlConfiguration y = new YamlConfiguration();
        y.set("boredom.enabled", true);
        y.set("boredom.ideas", java.util.List.of("quiz", "JOKE", "nonsense"));
        y.set("boredom.quiet-minutes", 0);
        y.set("boredom.min-players", 3);
        BoredomDetector.Config c = BoredomDetector.Config.load(y.getConfigurationSection("boredom"), log);
        assertTrue(c.enabled());
        assertEquals(Set.of(BoredomDetector.Kind.QUIZ, BoredomDetector.Kind.JOKE), c.kinds());
        assertEquals(10, c.quietMinutes()); // 0 is invalid -> default
        assertEquals(3, c.minPlayers());
        assertFalse(BoredomDetector.Config.load(null, log).enabled());
        assertEquals(5, BoredomDetector.Config.load(null, log).kinds().size());
    }
}
