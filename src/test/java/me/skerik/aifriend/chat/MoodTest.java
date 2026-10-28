package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.skerik.aifriend.chat.EventPrompts.Boss;
import me.skerik.aifriend.chat.EventPrompts.GameEvent;
import org.junit.jupiter.api.Test;

class MoodTest {

    private static final long T0 = 1_000_000_000L;
    private static final long MIN = 60_000L;

    private static Mood on() {
        return new Mood(new Mood.Config(true, 30));
    }

    @Test
    void startsCalmAndDoesNothingWhileOff() {
        Mood off = new Mood(Mood.Config.defaults());
        off.record(GameEvent.bossKilled(Boss.WITHER, "Steve"), T0);
        assertEquals(0, off.score(T0));
        assertEquals(Mood.Level.CALM, on().level(T0));
    }

    @Test
    void goodNewsCheersTheBotUpAndBossKillsMoreThanAdvancements() {
        Mood m = on();
        m.record(GameEvent.advancement("A", "Stone Age"), T0);
        assertEquals(8, m.score(T0));
        m.record(GameEvent.bossKilled(Boss.ENDER_DRAGON, "A"), T0);
        assertEquals(Mood.Level.CHEERFUL, m.level(T0)); // 43
        m.record(GameEvent.bossKilled(Boss.WITHER, "A"), T0);
        assertEquals(Mood.Level.THRILLED, m.level(T0));
    }

    @Test
    void aSingleDeathIsMildButManyDeathsInAShortTimeUpsetTheBot() {
        Mood m = on();
        m.record(GameEvent.death("A", "fell"), T0);
        assertEquals(-12, m.score(T0));
        assertEquals(Mood.Level.CALM, m.level(T0));
        m.record(GameEvent.death("B", "burned"), T0 + 10_000);
        m.record(GameEvent.death("C", "drowned"), T0 + 20_000); // third within 5 minutes: mass death
        assertTrue(m.score(T0 + 20_000) <= -60, "score " + m.score(T0 + 20_000));
        assertEquals(Mood.Level.UPSET, m.level(T0 + 20_000));
    }

    @Test
    void deathsFarApartAreNotAMassDeath() {
        Mood m = on();
        m.record(GameEvent.death("A", "x"), T0);
        m.record(GameEvent.death("B", "x"), T0 + 6 * MIN);
        m.record(GameEvent.death("C", "x"), T0 + 12 * MIN);
        assertTrue(m.score(T0 + 12 * MIN) > -40, "score " + m.score(T0 + 12 * MIN)); // no mass-death penalty
    }

    @Test
    void repeatedBigExplosionsLookLikeGriefingSmallOnesAreIgnored() {
        Mood m = on();
        m.record(GameEvent.explosion("fireball", 1), T0);
        assertEquals(0, m.score(T0));
        m.record(GameEvent.explosion("tnt", 4), T0);
        m.record(GameEvent.explosion("tnt", 4), T0 + 1000);
        assertEquals(-12, m.score(T0 + 1000));
        m.record(GameEvent.explosion("tnt", 4), T0 + 2000);
        assertEquals(Mood.Level.UNEASY, m.level(T0 + 2000));
    }

    @Test
    void moodFadesBackWithTheHalfLife() {
        Mood m = on();
        m.record(GameEvent.bossKilled(Boss.WITHER, "A"), T0);
        assertEquals(35, m.score(T0));
        assertEquals(18, m.score(T0 + 30 * MIN)); // half of 35
        assertEquals(Mood.Level.CALM, m.level(T0 + 60 * MIN));
        assertEquals(0, m.score(T0 + 24 * 60 * MIN));
    }

    @Test
    void scoreStaysInsideTheRange() {
        Mood m = on();
        for (int i = 0; i < 20; i++) m.record(GameEvent.bossKilled(Boss.WITHER, "A"), T0);
        assertEquals(100, m.score(T0));
        for (int i = 0; i < 60; i++) m.record(GameEvent.death("A", "x"), T0 + i * 100_000_000L);
        assertTrue(m.score(T0 + 6_000_000_000L) >= -100);
    }

    @Test
    void configLoadsAndValidates() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("mood.enabled", true);
        y.set("mood.half-life-minutes", 0);
        var log = java.util.logging.Logger.getLogger("MoodTest");
        log.setLevel(java.util.logging.Level.OFF);
        Mood.Config c = Mood.Config.load(y.getConfigurationSection("mood"), log);
        assertTrue(c.enabled());
        assertEquals(30, c.halfLifeMinutes()); // 0 is invalid -> default
        assertEquals(Mood.Config.defaults(), Mood.Config.load(null, log));
    }
}
