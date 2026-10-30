package me.skerik.aifriend.longmemory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import me.skerik.aifriend.chat.Cfg;
import me.skerik.aifriend.storage.PlayerNotesStore;
import me.skerik.aifriend.storage.PlayerNotesStore.Note;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Long-term memory: notes about players (kept by UUID in the database) that come back into later prompts. Retrieval and
 * storing are blocking and meant for async threads only; they never throw, a broken database or embedding service only
 * degrades the feature (keyword matching, no notes) and never the bot's answers.
 */
public final class LongTermMemory {

    /** Config section {@code long-term-memory} (the database itself is configured in {@code storage}). Default: off. */
    public record Config(boolean enabled, int extractEveryExchanges, int maxNotesPerPlayer, int retentionDays,
                         int retrievalTopK, boolean deleteOnMute, Embeddings embeddings) {

        public static Config defaults() {
            return new Config(false, 6, 20, 0, 5, true, new Embeddings("none", "", ""));
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            ConfigurationSection e = s == null ? null : s.getConfigurationSection("embeddings");
            String backend = Cfg.string(e, "backend", "none").toLowerCase(Locale.ROOT);
            String baseUrl = Cfg.string(e, "base-url", "");
            if (!baseUrl.isEmpty() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                log.warning("long-term-memory.embeddings.base-url must be an http(s) URL; using the provider's");
                baseUrl = "";
            }
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "extract-every-exchanges", 1, 1000, 6, log),
                    Cfg.bounded(s, "max-notes-per-player", 1, 500, 20, log),
                    Cfg.bounded(s, "retention-days", 0, 36500, 0, log),
                    Cfg.bounded(s, "retrieval-top-k", 1, 50, 5, log),
                    Cfg.bool(s, "delete-on-mute", true),
                    new Embeddings(backend.isEmpty() ? "none" : backend, Cfg.string(e, "model", ""), baseUrl));
        }
    }

    /** {@code backend}: "none" or the name of an entry under {@code provider.providers}; empty model/base-url = defaults. */
    public record Embeddings(String backend, String model, String baseUrl) {
    }

    private static final long DB_TIMEOUT_SECONDS = 10;
    private static final long EMBED_PAUSE_MILLIS = 60_000;
    private static final int MAX_EMBED_CHARS = 600;

    private final PlayerNotesStore store;
    private final @Nullable Embedder embedder;
    private final Config config;
    private final LongSupplier clock;
    private final Logger log;
    private volatile long embedRetryAt;

    public LongTermMemory(PlayerNotesStore store, @Nullable Embedder embedder, Config config, LongSupplier clock, Logger log) {
        this.store = store;
        this.embedder = embedder;
        this.config = config;
        this.clock = clock;
        this.log = log;
    }

    public Config config() {
        return config;
    }

    public PlayerNotesStore store() {
        return store;
    }

    /** "keywords" or the embedding model, for /aifriend status. */
    public String mode() {
        return embedder == null ? "keywords" : "embeddings: " + embedder.model();
    }

    /**
     * The best notes for these players and this chat text (at most retrieval-top-k). Blocking; empty on any problem.
     *
     * @param markUsed count the retrieval (last_used_at, use_count); false for dry runs
     */
    public List<Note> retrieve(Set<UUID> players, String queryText, boolean markUsed) {
        if (players.isEmpty()) return List.of();
        try {
            String text = queryText.length() > MAX_EMBED_CHARS ? queryText.substring(0, MAX_EMBED_CHARS) : queryText;
            float[] vector = embedOrNull(text);
            List<Note> notes = await(store.topNotesFor(players, vector, vector == null || embedder == null ? null : embedder.model(),
                    text, config.retrievalTopK()));
            if (markUsed && !notes.isEmpty()) {
                store.markUsed(notes.stream().map(Note::id).toList(), clock.getAsLong())
                        .exceptionally(e -> {
                            log.fine("Long-term memory: cannot update note usage: " + e);
                            return null;
                        });
            }
            return notes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warning("Long-term memory: cannot read notes (" + e.getClass().getSimpleName() + "); answering without them");
            return List.of();
        }
    }

    /** Embeds (when possible), stores and trims. Blocking; failures are logged, never thrown. */
    public void remember(UUID player, String name, String note) {
        try {
            float[] vector = embedOrNull(note);
            await(store.add(player, name, note, vector, vector == null || embedder == null ? null : embedder.model(), clock.getAsLong()));
            await(store.prune(config.maxNotesPerPlayer(), config.retentionDays(), clock.getAsLong()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warning("Long-term memory: cannot store a note (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Notes at least this similar (cosine) to a topic the player asked to forget are deleted. */
    static final double FORGET_MIN_COSINE = 0.6;
    /** Without embeddings: share of the note's words that occur in the topic. */
    static final double FORGET_MIN_KEYWORDS = 0.4;
    static final int FORGET_MAX_NOTES = 3;

    /**
     * Deletes the notes of the player that match what they asked to forget. Blocking; failures are logged, never thrown.
     *
     * @return the number of deleted notes
     */
    public int forgetTopic(UUID player, String topic) {
        try {
            float[] vector = embedOrNull(topic);
            return await(store.deleteMatching(player, vector, vector == null || embedder == null ? null : embedder.model(), topic,
                    FORGET_MIN_COSINE, FORGET_MIN_KEYWORDS, FORGET_MAX_NOTES));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warning("Long-term memory: cannot delete a note the player asked to forget (" + e.getClass().getSimpleName() + ")");
            return 0;
        }
    }

    /** Deletes these notes of the player (ids of other players' notes are ignored). Blocking; 0 on any problem. */
    public int forgetNotes(UUID player, Collection<Long> noteIds) {
        try {
            return await(store.deleteIds(player, noteIds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warning("Long-term memory: cannot delete a note the player asked to forget (" + e.getClass().getSimpleName() + ")");
            return 0;
        }
    }

    /** All notes of the player, newest first. Blocking; empty on any problem. */
    public List<Note> notesNow(UUID player) {
        try {
            return await(store.listFor(player));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warning("Long-term memory: cannot read notes (" + e.getClass().getSimpleName() + ")");
            return List.of();
        }
    }

    /** Daily housekeeping (retention-days and max-notes-per-player). */
    public CompletableFuture<Integer> prune() {
        return store.prune(config.maxNotesPerPlayer(), config.retentionDays(), clock.getAsLong());
    }

    public CompletableFuture<Integer> forget(UUID player) {
        return store.deleteFor(player);
    }

    public CompletableFuture<Integer> forgetAll() {
        return store.deleteAll();
    }

    public CompletableFuture<List<Note>> notesOf(UUID player) {
        return store.listFor(player);
    }

    /** Players with notes under this name (case-insensitive). */
    public CompletableFuture<List<UUID>> findByName(String name) {
        return store.findPlayersByName(name);
    }

    /** Several players at once (muting, /aifriend forget). */
    public CompletableFuture<Integer> forgetMany(Collection<UUID> players) {
        CompletableFuture<Integer> total = CompletableFuture.completedFuture(0);
        for (UUID p : players) total = total.thenCombine(store.deleteFor(p), Integer::sum);
        return total;
    }

    private float @Nullable [] embedOrNull(String text) throws InterruptedException {
        Embedder e = embedder;
        if (e == null || clock.getAsLong() < embedRetryAt) return null;
        try {
            return e.embed(text);
        } catch (IOException | RuntimeException ex) {
            embedRetryAt = clock.getAsLong() + EMBED_PAUSE_MILLIS;
            log.warning("Long-term memory: the embedding request failed (" + ex.getMessage() + "); using keyword matching for a minute");
            return null;
        }
    }

    private static <T> T await(CompletableFuture<T> f) throws InterruptedException, ExecutionException, TimeoutException {
        return f.get(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
