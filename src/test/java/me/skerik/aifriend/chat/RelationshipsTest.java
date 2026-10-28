package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.storage.Database;
import me.skerik.aifriend.storage.DbConfig;
import me.skerik.aifriend.storage.Migrations;
import me.skerik.aifriend.storage.RelationshipStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationshipsTest {

    private static final long DAY = 86_400_000L;
    private static final long T0 = 1_800_000_000_000L;
    private static final Relationships.Config CFG = new Relationships.Config(true, 3, 10, 30, true);
    private static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());

    @TempDir Path dir;

    @Test
    void levelsGrowWithAnsweredExchanges() {
        Relationships r = new Relationships();
        assertEquals(Relationships.Level.STRANGER, r.view(A, T0, CFG).level());
        for (int i = 0; i < 2; i++) r.interaction(A, "Alex", T0);
        assertEquals(Relationships.Level.STRANGER, r.view(A, T0, CFG).level());
        r.interaction(A, "Alex", T0);
        assertEquals(Relationships.Level.ACQUAINTANCE, r.view(A, T0, CFG).level());
        for (int i = 0; i < 7; i++) r.interaction(A, "Alex", T0);
        assertEquals(Relationships.Level.REGULAR, r.view(A, T0, CFG).level());
        for (int i = 0; i < 20; i++) r.interaction(A, "Alex", T0);
        assertEquals(Relationships.Level.FRIEND, r.view(A, T0, CFG).level());
        assertEquals(30, r.view(A, T0, CFG).interactions());
        // somebody else is unaffected
        assertEquals(Relationships.Level.STRANGER, r.view(B, T0, CFG).level());
    }

    @Test
    void knownDaysCountFromTheFirstExchange() {
        Relationships r = new Relationships();
        r.interaction(A, "Alex", T0);
        r.interaction(A, "Alex", T0 + 5 * DAY + 1000);
        assertEquals(5, r.view(A, T0 + 5 * DAY + 1000, CFG).knownDays());
        assertEquals(0, r.view(B, T0, CFG).knownDays());
    }

    @Test
    void nicknameIsSanitisedAndClearable() {
        assertEquals("Stevie", Relationships.sanitizeNickname("  Stevie  "));
        assertEquals("Big Boss", Relationships.sanitizeNickname("Big \n\t  Boss"));
        // markup and quote characters are dropped (the letters that remain are harmless text)
        assertEquals("bignore previousb", Relationships.sanitizeNickname("<b>ignore \"previous\"</b>"));
        assertEquals("xStevie", Relationships.sanitizeNickname("{\"x\":\"Stevie\"}"));
        assertNull(Relationships.sanitizeNickname("<>\"{}[]"));
        assertNull(Relationships.sanitizeNickname("   "));
        assertEquals(24, Relationships.sanitizeNickname("x".repeat(100)).length());
        assertEquals("O'Neil-Jr.", Relationships.sanitizeNickname("O'Neil-Jr."));
        assertEquals("Žluťoučký", Relationships.sanitizeNickname("Žluťoučký")); // letters of any language

        Relationships r = new Relationships();
        r.setNickname(A, "Alex", "Stevie", T0);
        assertEquals("Stevie", r.view(A, T0, CFG).nickname());
        r.interaction(A, "Alex", T0);
        assertEquals("Stevie", r.nickname(A)); // an exchange keeps it
        r.setNickname(A, "Alex", null, T0);
        assertNull(r.nickname(A));
    }

    @Test
    void forgetRemovesEverythingAboutAPlayerOrAboutEverybody() {
        Relationships r = new Relationships();
        r.interaction(A, "Alex", T0);
        r.interaction(B, "Bea", T0);
        assertEquals(java.util.List.of(A), r.findByName("alex"));
        assertTrue(r.forget(A));
        assertFalse(r.forget(A));
        assertEquals(Relationships.Level.STRANGER, r.view(A, T0, CFG).level());
        assertEquals(1, r.size());
        assertEquals(1, r.forgetAll());
        assertEquals(0, r.size());
    }

    @Test
    void thresholdsMustGrowOtherwiseDefaultsAreUsed() {
        Logger log = Logger.getLogger("RelationshipsTest");
        log.setLevel(Level.OFF);
        YamlConfiguration y = new YamlConfiguration();
        y.set("relationships.enabled", true);
        y.set("relationships.acquaintance-after", 50);
        y.set("relationships.regular-after", 20);
        y.set("relationships.friend-after", 10);
        Relationships.Config c = Relationships.Config.load(y.getConfigurationSection("relationships"), log);
        assertTrue(c.enabled());
        assertEquals(5, c.acquaintanceAfter());
        assertEquals(25, c.regularAfter());
        assertEquals(80, c.friendAfter());
        assertEquals(Relationships.Config.defaults(), Relationships.Config.load(null, log));
    }

    @Test
    void recordsSurviveARestartThroughTheDatabase() throws Exception {
        Path file = dir.resolve("rel.db");
        try (Database db = new Database(DbConfig.sqlite(file, 2))) {
            Migrations.migrate(db);
            Relationships r = new Relationships();
            r.attach(new RelationshipStore(db));
            r.load();
            for (int i = 0; i < 12; i++) r.interaction(A, "Alex", T0);
            r.setNickname(A, "Alex", "Stevie", T0 + 10);
            r.interaction(B, "Bea", T0);
            r.flush();
            r.interaction(A, "Alex", T0 + 20); // a later change is saved by the next flush
            r.flush();
        }
        try (Database db = new Database(DbConfig.sqlite(file, 2))) {
            Migrations.migrate(db);
            Relationships again = new Relationships();
            again.attach(new RelationshipStore(db));
            again.load();
            Relationships.View v = again.view(A, T0 + DAY, CFG);
            assertEquals(13, v.interactions());
            assertEquals(Relationships.Level.REGULAR, v.level());
            assertEquals("Stevie", v.nickname());
            assertEquals(1, again.view(B, T0, CFG).interactions());
            assertEquals(2, again.size());
        }
    }

    @Test
    void forgettingDeletesTheDatabaseRowsToo() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("forget.db"), 2))) {
            Migrations.migrate(db);
            RelationshipStore store = new RelationshipStore(db);
            Relationships r = new Relationships();
            r.attach(store);
            r.load();
            r.interaction(A, "Alex", T0);
            r.interaction(B, "Bea", T0);
            r.flush();
            assertEquals(2, store.loadAll().size());
            r.forget(A);
            r.flush();
            assertEquals(1, store.loadAll().size());
            r.forgetAll();
            r.flush();
            assertEquals(0, store.loadAll().size());
            // a player who comes back starts from zero
            r.interaction(A, "Alex", T0 + 1);
            r.flush();
            assertEquals(1, store.loadAll().getFirst().interactions());
        }
    }

    @Test
    void withoutADatabaseEverythingStaysInMemoryAndFlushIsHarmless() throws Exception {
        Relationships r = new Relationships();
        r.interaction(A, "Alex", T0);
        r.flush();
        r.load();
        assertFalse(r.persistent());
        assertEquals(1, r.view(A, T0, CFG).interactions());
    }

    @Test
    void recordsMadeBeforeTheDatabaseWasReadAreMergedWithTheStoredOnes() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("merge.db"), 2))) {
            Migrations.migrate(db);
            RelationshipStore store = new RelationshipStore(db);
            store.save(java.util.List.of(new Relationships.Entry(A, "Alex", 10, T0, T0, "Stevie")));
            Relationships r = new Relationships();
            r.interaction(A, "Alex", T0 + 5000); // the player chatted while the database was still being read
            r.attach(store);
            r.load();
            assertEquals(11, r.view(A, T0 + 5000, CFG).interactions());
            assertEquals("Stevie", r.nickname(A));
            r.load(); // a second read must not count the rows again
            assertEquals(11, r.view(A, T0 + 5000, CFG).interactions());
        }
    }

    @Test
    void aNewDatabaseReceivesWhatWasKnownBefore() throws Exception {
        Relationships r = new Relationships();
        try (Database first = new Database(DbConfig.sqlite(dir.resolve("one.db"), 2))) {
            Migrations.migrate(first);
            r.attach(new RelationshipStore(first));
            r.load();
            r.interaction(A, "Alex", T0);
            r.flush();
        }
        try (Database second = new Database(DbConfig.sqlite(dir.resolve("two.db"), 2))) {
            Migrations.migrate(second);
            RelationshipStore store = new RelationshipStore(second);
            r.attach(store); // the admin moved to another database
            r.flush();
            assertEquals(1, store.loadAll().size());
        }
    }
}
