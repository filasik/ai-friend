package me.skerik.aifriend.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.skerik.aifriend.storage.PlayerNotesStore.Note;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerNotesStoreTest {

    private static final long DAY = 86_400_000L;

    @TempDir Path dir;
    private Database db;
    private PlayerNotesStore store;
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @BeforeEach
    void open() throws Exception {
        db = new Database(DbConfig.sqlite(dir.resolve("notes.db"), 2));
        Migrations.migrate(db);
        store = new PlayerNotesStore(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    private static List<String> texts(List<Note> notes) {
        return notes.stream().map(Note::text).toList();
    }

    @Test
    void addListCountAndDedupe() {
        long id = store.add(steve, "Steve", "Builds a medieval castle", null, null, 1000).join();
        long again = store.add(steve, "Steve", "builds a MEDIEVAL castle", null, null, 5000).join();
        assertEquals(id, again);
        store.add(steve, "Steve", "Prefers creative mode", new float[]{1, 0}, "m", 2000).join();
        store.add(alex, "Alex", "Likes redstone", null, null, 3000).join();

        assertEquals(3L, store.count().join());
        assertEquals(2L, store.countFor(steve).join());
        List<Note> mine = store.listFor(steve).join();
        assertEquals(List.of("Prefers creative mode", "Builds a medieval castle"), texts(mine)); // newest first
        assertTrue(mine.getFirst().embedded());
        assertFalse(mine.getLast().embedded());
        assertEquals(5000, mine.getLast().lastUsedAt()); // the duplicate refreshed it
        assertEquals(List.of("Likes redstone", "Prefers creative mode"), texts(store.recent(2).join()));
    }

    @Test
    void embeddingRankingUsesCosineSimilarity() {
        store.add(steve, "Steve", "castle", new float[]{1, 0, 0}, "m", 1000).join();
        store.add(steve, "Steve", "farm", new float[]{0, 1, 0}, "m", 2000).join();
        store.add(alex, "Alex", "nether", new float[]{0.9f, 0.1f, 0}, "m", 3000).join();

        List<Note> top = store.topNotesFor(Set.of(steve, alex), new float[]{1, 0.05f, 0}, "m", "irrelevant words", 2).join();
        assertEquals(List.of("castle", "nether"), texts(top));
        // the filter is by player
        assertEquals(List.of("farm"), texts(store.topNotesFor(Set.of(steve), new float[]{0, 1, 0}, "m", "", 1).join()));
    }

    @Test
    void embeddingsOfAnotherModelOrSizeAreIgnoredAndKeywordsTakeOver() {
        store.add(steve, "Steve", "loves building bridges", new float[]{1, 0}, "old-model", 1000).join();
        store.add(steve, "Steve", "collects diamonds", new float[]{0, 1}, "new-model", 2000).join();
        List<Note> top = store.topNotesFor(Set.of(steve), new float[]{1, 0}, "new-model", "bridges everywhere", 2).join();
        // "old-model" note cannot be compared, but its words match; the new-model note scores cosine 0
        assertEquals("loves building bridges", top.getFirst().text());
        List<Note> sizes = store.topNotesFor(Set.of(steve), new float[]{1, 0, 0}, "new-model", "diamonds", 1).join();
        assertEquals("collects diamonds", sizes.getFirst().text());
    }

    @Test
    void keywordFallbackWorksWithoutAnyEmbeddingAndIgnoresAccents() {
        store.add(steve, "Steve", "Staví velký hrad", null, null, 1000).join();
        store.add(steve, "Steve", "Miluje redstone farmy", null, null, 2000).join();
        store.add(steve, "Steve", "Hraje na hardcore", null, null, 3000).join();

        assertEquals("Staví velký hrad", store.topNotesFor(Set.of(steve), null, null, "stavi hrad", 1).join().getFirst().text());
        assertEquals("Miluje redstone farmy", store.topNotesFor(Set.of(steve), null, null, "REDSTONE", 1).join().getFirst().text());
        // nothing matches: recency decides
        assertEquals("Hraje na hardcore", store.topNotesFor(Set.of(steve), null, null, "zzz", 1).join().getFirst().text());
        assertTrue(store.topNotesFor(Set.of(), null, null, "x", 3).join().isEmpty());
    }

    @Test
    void markUsedRaisesTheCounterAndMakesNotesRecent() {
        long id = store.add(steve, "Steve", "old fact", null, null, 1000).join();
        store.add(steve, "Steve", "newer fact", null, null, 2000).join();
        store.markUsed(List.of(id), 9000).join();
        Note n = store.listFor(steve).join().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
        assertEquals(1, n.useCount());
        assertEquals(9000, n.lastUsedAt());
    }

    @Test
    void pruneKeepsTheMostRecentlyUsedNotesPerPlayer() {
        for (int i = 1; i <= 5; i++) store.add(steve, "Steve", "fact " + i, null, null, i * 1000L).join();
        store.add(alex, "Alex", "only fact", null, null, 500).join();
        assertEquals(2, store.prune(3, 0, 10_000).join());
        assertEquals(List.of("fact 5", "fact 4", "fact 3"), texts(store.listFor(steve).join()));
        assertEquals(1L, store.countFor(alex).join());
    }

    @Test
    void pruneDropsNotesNotUsedWithinTheRetention() {
        long now = 100 * DAY;
        store.add(steve, "Steve", "ancient", null, null, now - 40 * DAY).join();
        store.add(steve, "Steve", "fresh", null, null, now - 2 * DAY).join();
        assertEquals(0, store.prune(20, 0, now).join()); // 0 = keep forever
        assertEquals(1, store.prune(20, 30, now).join());
        assertEquals(List.of("fresh"), texts(store.listFor(steve).join()));
    }

    @Test
    void deleteForAndDeleteAll() {
        store.add(steve, "Steve", "a", null, null, 1).join();
        store.add(steve, "Steve", "b", null, null, 2).join();
        store.add(alex, "Alex", "c", null, null, 3).join();
        assertEquals(2, store.deleteFor(steve).join());
        assertEquals(0, store.deleteFor(steve).join());
        assertEquals(List.of("c"), texts(store.recent(10).join()));
        assertEquals(1, store.deleteAll().join());
        assertEquals(0L, store.count().join());
    }

    @Test
    void findPlayersByNameIgnoresCase() {
        store.add(steve, "Steve", "a", null, null, 1).join();
        assertEquals(List.of(steve), store.findPlayersByName("sTEVE").join());
        assertTrue(store.findPlayersByName("nobody").join().isEmpty());
    }

    @Test
    void vectorsSurviveTheDatabase() {
        float[] v = {0.25f, -1.5f, 3f};
        assertTrue(java.util.Arrays.equals(v, PlayerNotesStore.decode(PlayerNotesStore.encode(v))));
    }

    @Test
    void notesAreStoredAsPlainParametersSoQuotesAreHarmless() {
        String evil = "x'); DROP TABLE aifriend_player_notes; --";
        store.add(steve, "Steve", evil, null, null, 1).join();
        assertEquals(List.of(evil), texts(store.listFor(steve).join()));
    }

    @Test
    void deleteMatchingRemovesOnlyTheTopicOfThatPlayer() {
        store.add(steve, "Steve", "Staví velký hrad", null, null, 1000).join();
        store.add(steve, "Steve", "Miluje redstone farmy", null, null, 2000).join();
        store.add(alex, "Alex", "Staví velký hrad", null, null, 3000).join();

        assertEquals(1, store.deleteMatching(steve, null, null, "staví hrad", 0.6, 0.4, 3).join());
        assertEquals(List.of("Miluje redstone farmy"), texts(store.listFor(steve).join()));
        assertEquals(1, store.listFor(alex).join().size());
        // an unrelated topic deletes nothing
        assertEquals(0, store.deleteMatching(steve, null, null, "zzz qqq", 0.6, 0.4, 3).join());
    }
}
