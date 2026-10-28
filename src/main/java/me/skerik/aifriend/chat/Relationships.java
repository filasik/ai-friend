package me.skerik.aifriend.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import me.skerik.aifriend.storage.RelationshipStore;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * How well the bot knows each player: a counter of answered exchanges the player took part in, the first and last time,
 * and an optional nickname the player picked. Everything is kept in memory (reads happen while a prompt is built, never
 * block) and written to the database in the background by {@link #flush()} when one is attached; without a database it
 * simply starts from zero after a restart. Thread-safe.
 */
public final class Relationships {

    public enum Level { STRANGER, ACQUAINTANCE, REGULAR, FRIEND }

    /** Config section {@code relationships}. Default: off. */
    public record Config(boolean enabled, int acquaintanceAfter, int regularAfter, int friendAfter, boolean nicknames) {

        public static Config defaults() {
            return new Config(false, 5, 25, 80, true);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            int a = Cfg.bounded(s, "acquaintance-after", 1, 100_000, 5, log);
            int r = Cfg.bounded(s, "regular-after", 1, 100_000, 25, log);
            int f = Cfg.bounded(s, "friend-after", 1, 100_000, 80, log);
            if (!(a < r && r < f)) {
                log.warning("relationships: acquaintance-after < regular-after < friend-after must hold; using 5, 25, 80");
                a = 5;
                r = 25;
                f = 80;
            }
            return new Config(Cfg.bool(s, "enabled", false), a, r, f, Cfg.bool(s, "nicknames", true));
        }

        public Level levelFor(int interactions) {
            if (interactions >= friendAfter) return Level.FRIEND;
            if (interactions >= regularAfter) return Level.REGULAR;
            if (interactions >= acquaintanceAfter) return Level.ACQUAINTANCE;
            return Level.STRANGER;
        }
    }

    /** One player's record (immutable; changes replace it). */
    public record Entry(UUID player, String name, int interactions, long firstSeen, long lastSeen, @Nullable String nickname) {
    }

    /** What goes into the prompt. */
    public record View(Level level, long knownDays, @Nullable String nickname, int interactions) {
    }

    static final int MAX_NICKNAME = 24;
    static final int MAX_ENTRIES = 50_000;
    static final long DAY_MS = 86_400_000L;

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> dirty = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> deleted = new ConcurrentHashMap<>();
    private volatile boolean wipe;
    private volatile boolean loaded;
    private volatile @Nullable RelationshipStore store;

    /** Attaches (or detaches, with null) the database. {@link #load()} reads what it holds. */
    public void attach(@Nullable RelationshipStore store) {
        this.store = store;
        // records known before this database existed (first start, switch to MariaDB) are written to it
        if (store != null && loaded) entries.keySet().forEach(id -> dirty.put(id, Boolean.TRUE));
    }

    public boolean persistent() {
        return store != null;
    }

    /**
     * Reads the stored records into memory once (blocking, async thread). A record that was created in the meantime keeps
     * its newer data and gains the stored counter.
     */
    public void load() throws java.sql.SQLException {
        RelationshipStore s = store;
        if (s == null || loaded) return;
        List<Entry> rows = s.loadAll();
        loaded = true;
        for (Entry stored : rows) {
            if (deleted.containsKey(stored.player())) continue;
            entries.merge(stored.player(), stored, (mine, old) -> new Entry(mine.player(), mine.name(),
                    mine.interactions() + old.interactions(), Math.min(mine.firstSeen(), old.firstSeen()),
                    Math.max(mine.lastSeen(), old.lastSeen()), mine.nickname() != null ? mine.nickname() : old.nickname()));
        }
    }

    /** Counts one answered exchange the player took part in. */
    public void interaction(UUID player, String name, long nowMs) {
        if (entries.size() >= MAX_ENTRIES && !entries.containsKey(player)) return;
        entries.compute(player, (id, e) -> e == null ? new Entry(id, name, 1, nowMs, nowMs, null)
                : new Entry(id, name, e.interactions() == Integer.MAX_VALUE ? e.interactions() : e.interactions() + 1,
                e.firstSeen(), nowMs, e.nickname()));
        dirty.put(player, Boolean.TRUE);
    }

    /** The view for the prompt; a player the bot has never answered is a stranger with no history. */
    public View view(UUID player, long nowMs, Config config) {
        Entry e = entries.get(player);
        if (e == null) return new View(Level.STRANGER, 0, null, 0);
        return new View(config.levelFor(e.interactions()), Math.max(0, (nowMs - e.firstSeen()) / DAY_MS),
                e.nickname(), e.interactions());
    }

    public @Nullable String nickname(UUID player) {
        Entry e = entries.get(player);
        return e == null ? null : e.nickname();
    }

    /** Sets (or, with null, clears) the nickname. */
    public void setNickname(UUID player, String name, @Nullable String nickname, long nowMs) {
        entries.compute(player, (id, e) -> e == null ? new Entry(id, name, 0, nowMs, nowMs, nickname)
                : new Entry(id, name, e.interactions(), e.firstSeen(), e.lastSeen(), nickname));
        dirty.put(player, Boolean.TRUE);
    }

    /** Nickname text that is safe to hand to the model, or null when nothing usable is left. */
    public static @Nullable String sanitizeNickname(String raw) {
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length() && out.length() < MAX_NICKNAME; i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '\'') {
                if (space && !out.isEmpty()) out.append(' ');
                space = false;
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                space = true;
            }
        }
        String s = out.toString().strip();
        return s.isEmpty() ? null : s;
    }

    /** Players whose last known name is {@code name} (case-insensitive). */
    public List<UUID> findByName(String name) {
        List<UUID> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.name().equalsIgnoreCase(name)) out.add(e.player());
        return out;
    }

    public int size() {
        return entries.size();
    }

    /** Forgets one player (memory now, the database row at the next {@link #flush()}). Returns true when there was something. */
    public boolean forget(UUID player) {
        boolean had = entries.remove(player) != null;
        dirty.remove(player);
        deleted.put(player, Boolean.TRUE);
        return had;
    }

    public int forgetAll() {
        int n = entries.size();
        entries.clear();
        dirty.clear();
        deleted.clear();
        wipe = true;
        return n;
    }

    /**
     * Writes changes to the database (blocking, async thread or on disable). Safe to call when there is no database or
     * nothing changed. Failed writes stay pending for the next round.
     */
    public void flush() throws java.sql.SQLException {
        RelationshipStore s = store;
        if (s == null) return;
        if (wipe) {
            s.deleteAll();
            wipe = false;
        }
        List<UUID> gone = new ArrayList<>(deleted.keySet());
        if (!gone.isEmpty()) {
            s.delete(gone);
            gone.forEach(deleted::remove);
        }
        List<Entry> changed = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirty.keySet())) {
            Entry e = entries.get(id);
            if (dirty.remove(id) != null && e != null) changed.add(e);
        }
        if (changed.isEmpty()) return;
        try {
            s.save(changed);
        } catch (java.sql.SQLException | RuntimeException e) {
            for (Entry c : changed) if (entries.containsKey(c.player())) dirty.put(c.player(), Boolean.TRUE);
            throw e;
        }
    }

    /** Everything held in memory (tests and the admin overview). */
    public Collection<Entry> all() {
        return List.copyOf(entries.values());
    }
}
