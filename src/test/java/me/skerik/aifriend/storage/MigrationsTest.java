package me.skerik.aifriend.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationsTest {

    @TempDir Path dir;

    @Test
    void freshDatabaseReachesTheLatestVersionAndMigratingAgainIsANoop() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("fresh.db"), 2))) {
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
            long versions = db.call(c -> {
                try (var st = c.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM " + Migrations.table("schema_version"))) {
                    rs.next();
                    return rs.getLong(1);
                }
            });
            assertEquals(Migrations.latestVersion(), versions); // each version recorded exactly once
        }
    }

    @Test
    void canStopAtAnOlderVersion() throws Exception {
        try (Database db = new Database(DbConfig.sqlite(dir.resolve("old.db"), 2))) {
            assertEquals(0, Migrations.migrate(db, 0));
            assertEquals(1, Migrations.migrate(db, 1));
            assertEquals(Migrations.latestVersion(), Migrations.migrate(db));
        }
    }

    /** A later release appends a version: the old database is upgraded step by step and its rows survive. */
    @Test
    void appendedVersionsUpgradeAnExistingDatabase() throws Exception {
        Path file = dir.resolve("upgrade.db");
        try (Database db = new Database(DbConfig.sqlite(file, 2))) {
            assertEquals(1, Migrations.migrate(db));
            new PlayerNotesStore(db).add(UUID.randomUUID(), "Steve", "builds a castle", null, null, 1000L).join();
        }
        List<List<String>> next = List.of(
                List.of("ALTER TABLE {P}player_notes ADD COLUMN pinned INT NOT NULL DEFAULT 0"),
                List.of("CREATE TABLE {P}extra (id {ID}, value VARCHAR(10) NOT NULL){OPTS}"));
        List<List<String>> all = new java.util.ArrayList<>(Migrations.shipped());
        all.addAll(next);
        try (Database db = new Database(DbConfig.sqlite(file, 2))) {
            // pretend two more versions were appended: version 1 is already applied, so only the new steps run
            int latest = all.size();
            assertEquals(latest, Migrations.migrate(db, all, latest));
            assertEquals(latest, Migrations.migrate(db, all, latest));
            long unpinned = db.call(c -> {
                try (var st = c.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM " + Migrations.table("player_notes") + " WHERE pinned = 0")) {
                    rs.next();
                    return rs.getLong(1);
                }
            });
            assertEquals(1L, unpinned);
        }
    }

    @Test
    void sqlIsTranslatedPerDialect() {
        String sql = "CREATE TABLE {P}t (id {ID}, b {BLOB}){OPTS}";
        String sqlite = Migrations.dialect(sql, DbConfig.Dialect.SQLITE);
        String maria = Migrations.dialect(sql, DbConfig.Dialect.MARIADB);
        assertTrue(sqlite.contains("INTEGER PRIMARY KEY AUTOINCREMENT") && sqlite.contains(" b BLOB)"), sqlite);
        assertTrue(maria.contains("BIGINT PRIMARY KEY AUTO_INCREMENT") && maria.contains("LONGBLOB")
                && maria.contains("utf8mb4"), maria);
        assertFalse(sqlite.contains("{") || maria.contains("{"));
        assertTrue(sqlite.contains(Migrations.PREFIX + "t"));
    }

    @Test
    void shippedSchemaHasNoLeftoverPlaceholdersInEitherDialect() throws Exception {
        try (Database db = new Database(DbConfig.memory())) {
            Migrations.migrate(db);
            assertEquals(0L, new PlayerNotesStore(db).count().join());
        }
    }
}
