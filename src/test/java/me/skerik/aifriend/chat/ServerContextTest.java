package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ServerContextTest {

    @Test
    void timeOfDayFollowsTheMinecraftClock() {
        assertEquals("morning", ServerContext.timeOfDay(0));
        assertEquals("afternoon", ServerContext.timeOfDay(6000));
        assertEquals("evening", ServerContext.timeOfDay(12_500));
        assertEquals("night", ServerContext.timeOfDay(18_000));
        assertEquals("dawn", ServerContext.timeOfDay(23_000));
        assertEquals("morning", ServerContext.timeOfDay(24_000 + 100)); // full time, several days in
        assertEquals("night", ServerContext.timeOfDay(-6000)); // never throws on odd values
    }

    @Test
    void biomeKeysBecomeWords() {
        assertEquals("dark forest", ServerContext.readable("minecraft:dark_forest"));
        assertEquals("plains", ServerContext.readable("PLAINS"));
        assertEquals("custom biome", ServerContext.readable("mypack:custom_biome"));
    }

    @Test
    void snapshotKeepsOnlyTheRequestedPlayersCaseInsensitively() {
        var steve = new ServerContext.PlayerView("Steve", "world", null, null, "plains", "2 zombie");
        var alex = new ServerContext.PlayerView("Alex", "world_nether", null, null, "nether wastes", null);
        var kim = new ServerContext.PlayerView("Kim", null, null, null, null, null);
        var s = new ServerContext.Snapshot("night", "raining", 3, List.of(steve, alex, kim));
        var picked = s.forPlayers(List.of("steve", "KIM"), 5);
        assertEquals(List.of(steve, kim), picked.players());
        assertEquals("night", picked.time());
        assertEquals(3, picked.online());
        assertEquals(1, s.forPlayers(List.of("steve", "kim", "alex"), 1).players().size());
        assertTrue(s.forPlayers(List.of(), 5).players().isEmpty());
    }

    @Test
    void emptySnapshot() {
        assertTrue(ServerContext.Snapshot.empty().isEmpty());
        assertFalse(new ServerContext.Snapshot("day", null, 0, List.of()).isEmpty());
    }

    @Test
    void configLoadsAndFallsBackOnBadValues() {
        Logger log = Logger.getLogger("ServerContextTest");
        log.setLevel(Level.OFF);
        YamlConfiguration y = new YamlConfiguration();
        y.set("server-context.enabled", true);
        y.set("server-context.biome", false);
        y.set("server-context.radius", 1000);
        y.set("server-context.refresh-seconds", 60);
        ServerContext.Config c = ServerContext.Config.load(y.getConfigurationSection("server-context"), log);
        assertTrue(c.enabled());
        assertFalse(c.biome());
        assertTrue(c.weather());
        assertEquals(16, c.radius()); // too big -> default
        assertEquals(60, c.refreshSeconds());
        assertEquals(ServerContext.Config.defaults(), ServerContext.Config.load(null, log));
        assertFalse(ServerContext.Config.defaults().enabled());
    }

    @Test
    void surroundingsAreOnlyInAFractionOfTheRequestsUnlessPlayersAskAboutThem() {
        var quarter = new ServerContext.Config(true, 30, 30, true, true, true, true, true, 16, 25);
        var never = new ServerContext.Config(true, 30, 30, true, true, true, true, true, 16, 0);
        var always = new ServerContext.Config(true, 30, 30, true, true, true, true, true, 16);
        assertEquals(10, ServerContext.Config.defaults().sharePercent());
        assertEquals(100, always.sharePercent());

        java.util.Random random = new java.util.Random(42);
        int shared = 0;
        for (int i = 0; i < 2000; i++) if (ServerContext.share(quarter, "lol that was funny", random)) shared++;
        assertTrue(shared > 350 && shared < 650, "about a quarter, was " + shared);
        for (int i = 0; i < 100; i++) assertFalse(ServerContext.share(never, "lol that was funny", random));
        for (int i = 0; i < 100; i++) assertTrue(ServerContext.share(always, "lol that was funny", random));

        for (String about : new String[] {"kde jsem?", "jaké je počasí", "je tu noc", "Where am I", "prší", "co je za biom",
                "něco za mnou, zombie!", "nepřátelé všude"}) {
            assertTrue(ServerContext.share(never, about, random), about);
        }
        assertFalse(ServerContext.share(never, "kdepak, denní doba nic", random), "whole words only");
        assertFalse(ServerContext.share(never, "dobrý den všem, good day", random), "greetings are not about the surroundings");
    }

    @Test
    void sharePercentIsValidated() {
        var y = new YamlConfiguration();
        y.set("enabled", true);
        y.set("share-percent", 150);
        Logger log = Logger.getLogger("ServerContextTest");
        log.setLevel(Level.OFF);
        assertEquals(10, ServerContext.Config.load(y, log).sharePercent());
        y.set("share-percent", 0);
        assertEquals(0, ServerContext.Config.load(y, log).sharePercent());
    }
}
