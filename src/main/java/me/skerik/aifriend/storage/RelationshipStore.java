package me.skerik.aifriend.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import me.skerik.aifriend.chat.Relationships;

/**
 * Rows of {@code relationships} (schema v2). Blocking calls: the caller is an async thread ({@link Relationships#load()} and
 * {@code flush()} run in the background). Plain SQL for SQLite and MariaDB: an update, and an insert when nothing was updated.
 */
public final class RelationshipStore {

    private static final String TABLE = Migrations.table("relationships");

    private final Database db;

    public RelationshipStore(Database db) {
        this.db = db;
    }

    public List<Relationships.Entry> loadAll() throws SQLException {
        return db.call(c -> {
            List<Relationships.Entry> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT player_uuid, player_name, interactions, first_seen, last_seen, nickname FROM " + TABLE)) {
                while (rs.next()) {
                    try {
                        out.add(new Relationships.Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3),
                                rs.getLong(4), rs.getLong(5), rs.getString(6)));
                    } catch (IllegalArgumentException e) {
                        // a row with a damaged uuid is skipped, not fatal
                    }
                }
            }
            return out;
        });
    }

    public void save(Collection<Relationships.Entry> entries) throws SQLException {
        db.call(c -> Database.transaction(c, tx -> {
            try (PreparedStatement update = tx.prepareStatement("UPDATE " + TABLE
                    + " SET player_name = ?, interactions = ?, first_seen = ?, last_seen = ?, nickname = ? WHERE player_uuid = ?");
                 PreparedStatement insert = tx.prepareStatement("INSERT INTO " + TABLE
                         + " (player_uuid, player_name, interactions, first_seen, last_seen, nickname) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Relationships.Entry e : entries) {
                    update.setString(1, e.name());
                    update.setInt(2, e.interactions());
                    update.setLong(3, e.firstSeen());
                    update.setLong(4, e.lastSeen());
                    update.setString(5, e.nickname());
                    update.setString(6, e.player().toString());
                    if (update.executeUpdate() == 0) {
                        insert.setString(1, e.player().toString());
                        insert.setString(2, e.name());
                        insert.setInt(3, e.interactions());
                        insert.setLong(4, e.firstSeen());
                        insert.setLong(5, e.lastSeen());
                        insert.setString(6, e.nickname());
                        insert.executeUpdate();
                    }
                }
            }
            return null;
        }));
    }

    public void delete(Collection<UUID> players) throws SQLException {
        if (players.isEmpty()) return;
        db.call(c -> Database.transaction(c, tx -> {
            try (PreparedStatement ps = tx.prepareStatement("DELETE FROM " + TABLE + " WHERE player_uuid = ?")) {
                for (UUID id : players) {
                    ps.setString(1, id.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        }));
    }

    public void deleteAll() throws SQLException {
        db.call(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM " + TABLE);
            }
            return null;
        });
    }
}
