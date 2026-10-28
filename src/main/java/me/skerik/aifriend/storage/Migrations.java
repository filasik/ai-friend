package me.skerik.aifriend.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Versioned schema. Rules:
 * <ul>
 *   <li>Append a new list to {@link #VERSIONS} for every schema change; NEVER edit or reorder an old one
 *       (servers in the wild have already run it). Later releases add tables or columns as v2, v3, ...</li>
 *   <li>Each version runs in one transaction and is recorded in the {@code schema_version} table. SQLite rolls a failed
 *       version back completely; MariaDB DDL auto-commits, so keep a version small and, where possible, idempotent.</li>
 *   <li>Write plain SQL that works on both dialects. Placeholders: {@code {P}} table prefix, {@code {ID}} auto-increment
 *       primary key, {@code {BLOB}} binary column, {@code {OPTS}} table options (utf8mb4 on MariaDB). For anything
 *       else (upserts!) branch on the dialect in the store class. Avoid DELETE statements that select from the same
 *       table (MySQL rejects them) and anything case-sensitivity dependent (the dialects differ).</li>
 *   <li>Add an upgrade test for every new version: migrate to the previous version with
 *       {@link #migrate(Database, int)}, insert old-shaped data, migrate to latest, assert the data survived.</li>
 * </ul>
 */
public final class Migrations {

    /** Prefix of every table (several plugins can share one MariaDB database). */
    public static final String PREFIX = "aifriend_";

    private Migrations() {
    }

    public static String table(String name) {
        return PREFIX + name;
    }

    private static final List<List<String>> VERSIONS = List.of(
            // v1 (2.1.0): long-term memory
            List.of(
                    """
                    CREATE TABLE {P}player_notes (
                      id {ID},
                      player_uuid VARCHAR(36) NOT NULL,
                      player_name VARCHAR(32) NOT NULL,
                      note TEXT NOT NULL,
                      embedding {BLOB} NULL,
                      embedding_model VARCHAR(100) NULL,
                      created_at BIGINT NOT NULL,
                      last_used_at BIGINT NOT NULL,
                      use_count INT NOT NULL DEFAULT 0
                    ){OPTS}""",
                    "CREATE INDEX {P}player_notes_uuid ON {P}player_notes (player_uuid)"),
            // v2 (2.2.0): relationships (how well the bot knows a player, nickname)
            List.of(
                    """
                    CREATE TABLE {P}relationships (
                      player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                      player_name VARCHAR(32) NOT NULL,
                      interactions INT NOT NULL DEFAULT 0,
                      first_seen BIGINT NOT NULL,
                      last_seen BIGINT NOT NULL,
                      nickname VARCHAR(32) NULL
                    ){OPTS}"""));
            // v3 example, once you need it:
            // List.of("ALTER TABLE {P}player_notes ADD COLUMN something INT NOT NULL DEFAULT 0")

    /** The shipped versions (tests append their own to prove an upgrade). */
    static List<List<String>> shipped() {
        return VERSIONS;
    }

    public static int latestVersion() {
        return VERSIONS.size();
    }

    /** @return the schema version after migrating */
    public static int migrate(Database db) throws SQLException {
        return migrate(db, VERSIONS, VERSIONS.size());
    }

    /** Migrates up to {@code target} (tests use this to simulate an older install). */
    public static int migrate(Database db, int target) throws SQLException {
        return migrate(db, VERSIONS, target);
    }

    /** The real work; the list is a parameter so tests can prove that appended versions upgrade an existing database. */
    static int migrate(Database db, List<List<String>> versions, int target) throws SQLException {
        return db.call(c -> {
            ensureVersionTable(c);
            int current = currentVersion(c);
            for (int v = current + 1; v <= Math.min(target, versions.size()); v++) {
                int version = v;
                Database.transaction(c, tx -> {
                    for (String sql : versions.get(version - 1)) {
                        try (Statement st = tx.createStatement()) {
                            st.execute(dialect(sql, db.dialect()));
                        }
                    }
                    try (PreparedStatement ps = tx.prepareStatement("INSERT INTO " + table("schema_version") + " (version) VALUES (?)")) {
                        ps.setInt(1, version);
                        ps.executeUpdate();
                    }
                    return null;
                });
            }
            return currentVersion(c);
        });
    }

    private static void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + table("schema_version") + " (version INT NOT NULL PRIMARY KEY)");
        }
    }

    private static int currentVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(version) FROM " + table("schema_version"))) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static String dialect(String sql, DbConfig.Dialect dialect) {
        String withPrefix = sql.replace("{P}", PREFIX);
        return switch (dialect) {
            case SQLITE -> withPrefix.replace("{ID}", "INTEGER PRIMARY KEY AUTOINCREMENT").replace("{BLOB}", "BLOB")
                    .replace("{OPTS}", "");
            case MARIADB -> withPrefix.replace("{ID}", "BIGINT PRIMARY KEY AUTO_INCREMENT").replace("{BLOB}", "LONGBLOB")
                    .replace("{OPTS}", " DEFAULT CHARSET=utf8mb4");
        };
    }
}
