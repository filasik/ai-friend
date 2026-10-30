package me.skerik.aifriend.storage;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Long-term memory rows ({@code player_notes}). Every method returns a future that completes on the DB executor, SQL is
 * parameterized and works on SQLite and MariaDB alike (no upsert, no DELETE from a sub-select of the same table, no
 * case-sensitivity assumptions: duplicates and name matches are decided in Java or with LOWER()). A player has only a few
 * notes (max-notes-per-player), so ranking by cosine similarity or keyword overlap happens in Java.
 */
public final class PlayerNotesStore {

    private static final String TABLE = Migrations.table("player_notes");
    private static final String COLUMNS = "id, player_uuid, player_name, note, embedding, embedding_model, created_at, last_used_at, use_count";
    private static final int CHUNK = 400;

    /** A remembered fact. The embedding itself stays in the database; {@code embedded} tells whether there is one. */
    public record Note(long id, UUID player, String playerName, String text, long createdAt, long lastUsedAt,
                       int useCount, boolean embedded) {
    }

    private record Row(Note note, float @Nullable [] embedding, @Nullable String model) {
    }

    private final Database db;

    public PlayerNotesStore(Database db) {
        this.db = db;
    }

    /**
     * Stores a note. An identical note of the same player (ignoring case) is not stored twice: its last_used_at is
     * refreshed (and a missing embedding filled in) and the existing id is returned.
     */
    public CompletableFuture<Long> add(UUID player, String playerName, String note, float @Nullable [] embedding,
                                       @Nullable String embeddingModel, long now) {
        return db.supply(c -> {
            for (Row r : load(c, "WHERE player_uuid = ?", ps -> ps.setString(1, player.toString()))) {
                if (r.note.text().equalsIgnoreCase(note)) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE " + TABLE + " SET last_used_at = ?, player_name = ? WHERE id = ?")) {
                        ps.setLong(1, now);
                        ps.setString(2, playerName);
                        ps.setLong(3, r.note.id());
                        ps.executeUpdate();
                    }
                    if (embedding != null && r.embedding == null) {
                        try (PreparedStatement ps = c.prepareStatement("UPDATE " + TABLE + " SET embedding = ?, embedding_model = ? WHERE id = ?")) {
                            ps.setBytes(1, encode(embedding));
                            ps.setString(2, embeddingModel);
                            ps.setLong(3, r.note.id());
                            ps.executeUpdate();
                        }
                    }
                    return r.note.id();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE
                    + " (player_uuid, player_name, note, embedding, embedding_model, created_at, last_used_at, use_count) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 0)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, player.toString());
                ps.setString(2, playerName);
                ps.setString(3, note);
                if (embedding == null) {
                    ps.setNull(4, java.sql.Types.BLOB);
                    ps.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    ps.setBytes(4, encode(embedding));
                    ps.setString(5, embeddingModel);
                }
                ps.setLong(6, now);
                ps.setLong(7, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    /**
     * The {@code k} best notes of the given players for the query, best first. Notes with a compatible embedding (same
     * model and size as the query embedding) are scored by cosine similarity, all others by keyword overlap with
     * {@code queryText}. Ties go to the more recently used note. Does not change any row; see {@link #markUsed}.
     */
    public CompletableFuture<List<Note>> topNotesFor(Collection<UUID> players, float @Nullable [] queryEmbedding,
                                                     @Nullable String embeddingModel, String queryText, int k) {
        if (players.isEmpty() || k <= 0) return CompletableFuture.completedFuture(List.of());
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(players));
        return db.supply(c -> {
            List<Row> rows = new ArrayList<>();
            for (int from = 0; from < ids.size(); from += CHUNK) {
                List<UUID> part = ids.subList(from, Math.min(ids.size(), from + CHUNK));
                rows.addAll(load(c, "WHERE player_uuid IN (" + placeholders(part.size()) + ")", ps -> {
                    for (int i = 0; i < part.size(); i++) ps.setString(i + 1, part.get(i).toString());
                }));
            }
            return rank(rows, queryEmbedding, embeddingModel, queryText, k);
        });
    }

    /** Counts a retrieval: sets last_used_at and raises use_count of the notes. */
    public CompletableFuture<Void> markUsed(Collection<Long> noteIds, long now) {
        if (noteIds.isEmpty()) return CompletableFuture.completedFuture(null);
        List<Long> ids = List.copyOf(noteIds);
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + TABLE + " SET last_used_at = ?, use_count = use_count + 1 WHERE id = ?")) {
                for (long id : ids) {
                    ps.setLong(1, now);
                    ps.setLong(2, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    /** The newest notes of everybody (admin overview). */
    public CompletableFuture<List<Note>> recent(int limit) {
        return db.supply(c -> load(c, "ORDER BY created_at DESC, id DESC LIMIT " + Math.max(1, limit), ps -> { })
                .stream().map(Row::note).toList());
    }

    public CompletableFuture<Long> count() {
        return db.supply(c -> scalar(c, "SELECT COUNT(*) FROM " + TABLE, ps -> { }));
    }

    public CompletableFuture<Long> countFor(UUID player) {
        return db.supply(c -> scalar(c, "SELECT COUNT(*) FROM " + TABLE + " WHERE player_uuid = ?",
                ps -> ps.setString(1, player.toString())));
    }

    /** All notes of one player, newest first. */
    public CompletableFuture<List<Note>> listFor(UUID player) {
        return db.supply(c -> load(c, "WHERE player_uuid = ? ORDER BY created_at DESC, id DESC",
                ps -> ps.setString(1, player.toString())).stream().map(Row::note).toList());
    }

    /** Players that have notes under this name (case-insensitive); the name is the one stored with the notes. */
    public CompletableFuture<List<UUID>> findPlayersByName(String name) {
        return db.supply(c -> {
            Set<UUID> out = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT player_uuid FROM " + TABLE + " WHERE LOWER(player_name) = ?")) {
                ps.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
                }
            }
            return List.copyOf(out);
        });
    }

    /** @return the number of deleted notes */
    public CompletableFuture<Integer> deleteFor(UUID player) {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                return ps.executeUpdate();
            }
        });
    }

    /**
     * Deletes these notes, but only those that belong to {@code player} (an id from the model is never trusted alone).
     *
     * @return the number of deleted notes
     */
    public CompletableFuture<Integer> deleteIds(UUID player, Collection<Long> noteIds) {
        if (noteIds.isEmpty()) return CompletableFuture.completedFuture(0);
        List<Long> ids = List.copyOf(new LinkedHashSet<>(noteIds));
        return db.supply(c -> {
            int removed = 0;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE id = ? AND player_uuid = ?")) {
                for (long id : ids) {
                    ps.setLong(1, id);
                    ps.setString(2, player.toString());
                    removed += ps.executeUpdate();
                }
            }
            return removed;
        });
    }

    /**
     * Deletes the notes of one player that are about the given topic (at most {@code max}, best match first): cosine
     * similarity of at least {@code minCosine} for notes with a compatible embedding, otherwise a keyword overlap of
     * at least {@code minKeywords}.
     *
     * @return the number of deleted notes
     */
    public CompletableFuture<Integer> deleteMatching(UUID player, float @Nullable [] queryEmbedding, @Nullable String embeddingModel,
                                                     String topic, double minCosine, double minKeywords, int max) {
        return db.supply(c -> {
            Set<String> queryWords = words(topic);
            record Hit(long id, double score) {
            }
            List<Hit> hits = new ArrayList<>();
            for (Row r : load(c, "WHERE player_uuid = ?", ps -> ps.setString(1, player.toString()))) {
                double score = score(r, queryEmbedding, embeddingModel, queryWords);
                if (score >= (comparable(r, queryEmbedding, embeddingModel) ? minCosine : minKeywords)) hits.add(new Hit(r.note.id(), score));
            }
            hits.sort(Comparator.comparingDouble(Hit::score).reversed());
            int removed = 0;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE id = ?")) {
                for (Hit h : hits.subList(0, Math.min(max, hits.size()))) {
                    ps.setLong(1, h.id());
                    removed += ps.executeUpdate();
                }
            }
            return removed;
        });
    }

    /** @return the number of deleted notes */
    public CompletableFuture<Integer> deleteAll() {
        return db.supply(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE)) {
                return ps.executeUpdate();
            }
        });
    }

    /**
     * Housekeeping. Deletes notes not used for {@code retentionDays} days (0 = keep) and, per player, everything beyond
     * the {@code maxPerPlayer} most recently used notes.
     *
     * @return the number of deleted notes
     */
    public CompletableFuture<Integer> prune(int maxPerPlayer, int retentionDays, long now) {
        return db.supply(c -> {
            int removed = 0;
            if (retentionDays > 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE last_used_at < ?")) {
                    ps.setLong(1, now - retentionDays * 86_400_000L);
                    removed += ps.executeUpdate();
                }
            }
            if (maxPerPlayer > 0) {
                // newest first per player; everything after the first maxPerPlayer goes. Decided in Java (MySQL cannot
                // delete from a sub-select of the same table).
                Map<String, Integer> seen = new HashMap<>();
                List<Long> doomed = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id, player_uuid FROM " + TABLE
                        + " ORDER BY last_used_at DESC, id DESC");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (seen.merge(rs.getString(2), 1, Integer::sum) > maxPerPlayer) doomed.add(rs.getLong(1));
                    }
                }
                for (int from = 0; from < doomed.size(); from += CHUNK) {
                    List<Long> part = doomed.subList(from, Math.min(doomed.size(), from + CHUNK));
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE id IN (" + placeholders(part.size()) + ")")) {
                        for (int i = 0; i < part.size(); i++) ps.setLong(i + 1, part.get(i));
                        removed += ps.executeUpdate();
                    }
                }
            }
            return removed;
        });
    }

    // ---- ranking (pure, package-private for tests) ----

    private static List<Note> rank(List<Row> rows, float @Nullable [] query, @Nullable String model, String queryText, int k) {
        Set<String> queryWords = words(queryText);
        record Scored(Note note, double score) {
        }
        List<Scored> scored = new ArrayList<>(rows.size());
        for (Row r : rows) {
            double score = score(r, query, model, queryWords);
            scored.add(new Scored(r.note, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Comparator.comparingLong((Scored s) -> s.note.lastUsedAt()).reversed())
                .thenComparing(Comparator.comparingLong((Scored s) -> s.note.id()).reversed()));
        return scored.stream().limit(k).map(Scored::note).toList();
    }

    /** Cosine similarity when the note has a compatible embedding, keyword overlap otherwise. */
    private static double score(Row r, float @Nullable [] query, @Nullable String model, Set<String> queryWords) {
        return query != null && r.embedding != null && comparable(r, query, model)
                ? Math.max(0, cosine(query, r.embedding))
                : keywordScore(words(r.note.text()), queryWords);
    }

    /** The note's embedding was made by the same model with the same size as the query embedding. */
    private static boolean comparable(Row r, float @Nullable [] query, @Nullable String model) {
        return query != null && r.embedding != null && model != null && model.equals(r.model) && r.embedding.length == query.length;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Share of the note's words that also occur in the query (0..1). */
    static double keywordScore(Set<String> noteWords, Set<String> queryWords) {
        if (noteWords.isEmpty() || queryWords.isEmpty()) return 0;
        int hits = 0;
        for (String w : noteWords) if (queryWords.contains(w)) hits++;
        return (double) hits / noteWords.size();
    }

    /** Lower-case words of 3+ letters/digits without accents (Czech text matches without diacritics, too). */
    static Set<String> words(String text) {
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        Set<String> out = new HashSet<>();
        StringBuilder w = new StringBuilder();
        for (int i = 0; i <= plain.length(); i++) {
            if (i < plain.length() && Character.isLetterOrDigit(plain.charAt(i))) {
                w.append(plain.charAt(i));
            } else {
                if (w.length() >= 3) out.add(w.toString());
                w.setLength(0);
            }
        }
        return out;
    }

    // ---- JDBC helpers ----

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private static List<Row> load(Connection c, String tail, Binder binder) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM " + TABLE + " " + tail)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] blob = rs.getBytes(5);
                    float[] emb = blob == null || blob.length == 0 || blob.length % 4 != 0 ? null : decode(blob);
                    out.add(new Row(new Note(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4),
                            rs.getLong(7), rs.getLong(8), rs.getInt(9), emb != null), emb, rs.getString(6)));
                }
            }
        }
        return out;
    }

    private static long scalar(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static String placeholders(int n) {
        return "?" + ", ?".repeat(Math.max(0, n - 1));
    }

    static byte[] encode(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4);
        for (float f : v) b.putFloat(f);
        return b.array();
    }

    static float[] decode(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }
}
