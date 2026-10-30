package me.skerik.aifriend.knowledge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import me.skerik.aifriend.chat.Cfg;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * General facts about the server that the bot picked up from the chat ("chests and keys are opened at /truhly"), kept
 * in {@code knowledge-learned.json}. Nothing reaches the prompts before an admin approved it: a fact the model proposes
 * is a {@link Status#CANDIDATE} until it was mentioned in {@code min-mentions} separate extraction rounds, then it is
 * {@link Status#PENDING} (admins are told) until an admin approves, disables, rejects or deletes it. A rejected fact
 * stays in the file so that it (and anything similar) is never proposed again; a deleted one is gone and may be learned
 * again. Only {@link Status#APPROVED} facts are searched for answers (see {@link ServerKnowledge}).
 *
 * <p>State changes are synchronized and quick; the methods that change something then write the file outside that
 * lock (blocking: call them from an async task), so reads ({@link #list}, {@link #count}, also on the main thread) never
 * wait for disk I/O. Writes are ordered by a version number, an older snapshot never overwrites a newer one.
 */
public final class LearnedFacts {

    public static final String FILE = "knowledge-learned.json";
    public static final int MAX_FACT_LENGTH = 200;
    public static final int MIN_FACT_LENGTH = 8;
    static final int MAX_FACTS = 1000;
    static final int MAX_PLAYERS_KEPT = 5;
    /** Share of shared words (Jaccard) from which two facts count as the same. */
    static final double SAME_JACCARD = 0.6;
    /** Share of the shorter fact's words found in the longer one from which two facts count as the same. */
    static final double SAME_CONTAINED = 0.85;

    /** Config section {@code knowledge.learning}. Default: off. */
    public record Config(boolean enabled, int extractEveryExchanges, int minMentions, boolean autoApprove,
                         int candidateDays, boolean notifyAdmins) {

        public static Config defaults() {
            return new Config(false, 6, 2, false, 30, true);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "extract-every-exchanges", 1, 1000, 6, log),
                    Cfg.bounded(s, "min-mentions", 1, 50, 2, log),
                    Cfg.bool(s, "auto-approve", false),
                    Cfg.bounded(s, "forget-candidates-after-days", 1, 3650, 30, log),
                    Cfg.bool(s, "notify-admins", true));
        }
    }

    public enum Status { CANDIDATE, PENDING, APPROVED, DISABLED, REJECTED }

    /** One fact. {@code players} are the last few chat names that mentioned it (for the admin's review). */
    public record Fact(int id, String text, Status status, int mentions, List<String> players, long firstSeen,
                       long lastSeen, String decidedBy, long decidedAt) {

        Fact with(Status s, String by, long at) {
            return new Fact(id, text, s, mentions, players, firstSeen, lastSeen, by, at);
        }
    }

    /**
     * What the model proposed in one extraction round: a known fact by id (it was shown to the model) or a new text.
     * {@code player} is the chat name that said it.
     */
    public record Proposal(int id, String text, String player) {

        public static Proposal known(int id, String player) {
            return new Proposal(id, "", player);
        }

        public static Proposal fresh(String text, String player) {
            return new Proposal(-1, text, player);
        }
    }

    public enum Action { APPROVE, DISABLE, REJECT, DELETE }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Logger log;
    private final LongSupplier clock;
    private final Map<Integer, Fact> facts = new LinkedHashMap<>();
    private int nextId = 1;
    /** Bumped by every change (under this object's lock); the file holds {@link #written}. */
    private long version;
    private final Object fileLock = new Object();
    private long written;

    /** The serialized state of one version, written after the state lock was released. */
    private record Snapshot(long version, String json) {
    }

    public LearnedFacts(Path folder, Logger log, LongSupplier clock) {
        this.file = folder.resolve(FILE);
        this.log = log;
        this.clock = clock;
    }

    /** (Re)reads the file; a missing file is an empty collection, a broken one is kept aside and logged. Blocking. */
    public synchronized void load() {
        facts.clear();
        nextId = 1;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("facts")) {
                Fact f = read(e.getAsJsonObject());
                if (f != null) facts.put(f.id(), f);
            }
            int maxId = facts.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            nextId = Math.max(maxId + 1, root.has("next-id") ? root.get("next-id").getAsInt() : 1);
        } catch (IOException | JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            facts.clear();
            Path broken = file.resolveSibling(FILE + ".broken");
            try {
                Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // the warning below is enough
            }
            log.warning("Cannot read " + FILE + " (" + e.getClass().getSimpleName() + "); it was moved to " + broken.getFileName()
                    + " and learning starts empty");
        }
    }

    private static @Nullable Fact read(JsonObject o) {
        String text = o.get("text").getAsString();
        Status status;
        try {
            status = Status.valueOf(o.get("status").getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<String> players = new ArrayList<>();
        if (o.has("players")) for (JsonElement p : o.getAsJsonArray("players")) players.add(p.getAsString());
        return new Fact(o.get("id").getAsInt(), clean(text), status, o.has("mentions") ? o.get("mentions").getAsInt() : 1,
                List.copyOf(players), long0(o, "first-seen"), long0(o, "last-seen"),
                o.has("decided-by") ? o.get("decided-by").getAsString() : "", long0(o, "decided-at"));
    }

    private static long long0(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsLong() : 0;
    }

    /**
     * Counts one extraction round. A proposal for a known fact (by id or by a similar text) adds a mention; a new text
     * becomes a candidate unless {@code knownElsewhere} says the admin's knowledge already has it. Every fact is counted
     * at most once per round. Rejected and disabled facts swallow the proposal (never proposed again).
     *
     * @return the facts that changed into {@link Status#PENDING} or (with auto-approve) {@link Status#APPROVED} now
     */
    public List<Fact> observe(List<Proposal> proposals, Config config, Predicate<String> knownElsewhere) {
        if (proposals.isEmpty()) return List.of();
        List<Fact> promoted = new ArrayList<>();
        Snapshot snap;
        synchronized (this) {
            snap = observeLocked(proposals, config, knownElsewhere, promoted);
        }
        write(snap);
        return promoted;
    }

    private @Nullable Snapshot observeLocked(List<Proposal> proposals, Config config, Predicate<String> knownElsewhere,
                                             List<Fact> promoted) {
        long now = clock.getAsLong();
        Set<Integer> counted = new HashSet<>();
        boolean changed = false;
        for (Proposal p : proposals) {
            Fact f = p.id() >= 0 ? facts.get(p.id()) : null;
            String text = clean(p.text());
            if (f == null) {
                if (text.length() < MIN_FACT_LENGTH || text.length() > MAX_FACT_LENGTH) continue;
                f = similar(text);
            }
            if (f == null) {
                if (knownElsewhere.test(text) || facts.size() >= MAX_FACTS && !dropOldestCandidate()) continue;
                f = new Fact(nextId++, text, Status.CANDIDATE, 0, List.of(), now, now, "", 0);
            }
            if (!counted.add(f.id())) continue;
            changed = true;
            if (f.status() != Status.CANDIDATE && f.status() != Status.PENDING) {
                facts.put(f.id(), new Fact(f.id(), f.text(), f.status(), f.mentions() + 1, withPlayer(f.players(), p.player()),
                        f.firstSeen(), now, f.decidedBy(), f.decidedAt()));
                continue;
            }
            Fact next = new Fact(f.id(), f.text(), f.status(), f.mentions() + 1, withPlayer(f.players(), p.player()),
                    f.firstSeen(), now, f.decidedBy(), f.decidedAt());
            if (next.status() == Status.CANDIDATE && next.mentions() >= config.minMentions()) {
                next = config.autoApprove() ? next.with(Status.APPROVED, "auto", now) : next.with(Status.PENDING, "", 0);
                promoted.add(next);
            }
            facts.put(next.id(), next);
        }
        return changed ? snapshot() : null;
    }

    /** An admin's decision. {@code null} when there is no fact with that id. */
    public @Nullable Fact decide(int id, Action action, String admin) {
        Fact result;
        Snapshot snap;
        synchronized (this) {
            Fact f = facts.get(id);
            if (f == null) return null;
            long now = clock.getAsLong();
            result = switch (action) {
                case APPROVE -> f.with(Status.APPROVED, admin, now);
                case DISABLE -> f.with(Status.DISABLED, admin, now);
                case REJECT -> f.with(Status.REJECTED, admin, now);
                case DELETE -> f;
            };
            if (action == Action.DELETE) facts.remove(id);
            else facts.put(id, result);
            snap = snapshot();
        }
        write(snap);
        return result;
    }

    /** Replaces the text (the status stays). {@code null} when there is no such fact or the text is unusable. */
    public @Nullable Fact edit(int id, String raw, String admin) {
        String text = clean(raw);
        Fact edited;
        Snapshot snap;
        synchronized (this) {
            Fact f = facts.get(id);
            if (f == null || text.length() < MIN_FACT_LENGTH || text.length() > MAX_FACT_LENGTH) return null;
            edited = new Fact(f.id(), text, f.status(), f.mentions(), f.players(), f.firstSeen(), f.lastSeen(), admin,
                    clock.getAsLong());
            facts.put(id, edited);
            snap = snapshot();
        }
        write(snap);
        return edited;
    }

    /** Removes candidates nobody mentioned for {@code candidate-days}. Returns how many. Blocking when something goes. */
    public int prune(Config config) {
        int removed;
        Snapshot snap = null;
        synchronized (this) {
            long cutoff = clock.getAsLong() - config.candidateDays() * 86_400_000L;
            int before = facts.size();
            facts.values().removeIf(f -> f.status() == Status.CANDIDATE && f.lastSeen() < cutoff);
            removed = before - facts.size();
            if (removed > 0) snap = snapshot();
        }
        write(snap);
        return removed;
    }

    public synchronized @Nullable Fact get(int id) {
        return facts.get(id);
    }

    /** Facts with this status; pending ones oldest first (review order), everything else newest first. */
    public synchronized List<Fact> list(Status status) {
        Comparator<Fact> order = status == Status.PENDING ? Comparator.comparingLong(Fact::lastSeen)
                : Comparator.comparingLong(Fact::lastSeen).reversed();
        return facts.values().stream().filter(f -> f.status() == status).sorted(order.thenComparingInt(Fact::id)).toList();
    }

    public synchronized int count(Status status) {
        return (int) facts.values().stream().filter(f -> f.status() == status).count();
    }

    public synchronized Map<Status, Integer> counts() {
        Map<Status, Integer> out = new EnumMap<>(Status.class);
        for (Status s : Status.values()) out.put(s, 0);
        for (Fact f : facts.values()) out.merge(f.status(), 1, Integer::sum);
        return out;
    }

    /** The facts that share words with the text (best first, at most {@code max}), whatever their status. */
    public synchronized List<Fact> related(String text, int max) {
        Set<String> q = KnowledgeBase.stems(text);
        if (q.isEmpty() || max <= 0) return List.of();
        record Scored(Fact fact, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Fact f : facts.values()) {
            Set<String> w = KnowledgeBase.stems(f.text());
            if (w.isEmpty()) continue;
            int hits = 0;
            for (String s : w) if (q.contains(s)) hits++;
            if (hits > 0) scored.add(new Scored(f, (double) hits / w.size()));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> -s.fact().lastSeen()));
        return scored.stream().limit(max).map(Scored::fact).toList();
    }

    /** True when the two texts say the same thing in nearly the same words (accents, case and endings ignored). */
    public static boolean same(String a, String b) {
        Set<String> x = KnowledgeBase.stems(a);
        Set<String> y = KnowledgeBase.stems(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        int common = 0;
        for (String w : x) if (y.contains(w)) common++;
        int union = x.size() + y.size() - common;
        int smaller = Math.min(x.size(), y.size());
        return (double) common / union >= SAME_JACCARD || smaller >= 3 && (double) common / smaller >= SAME_CONTAINED;
    }

    private @Nullable Fact similar(String text) {
        for (Fact f : facts.values()) if (same(f.text(), text)) return f;
        return null;
    }

    /** Makes room for a new candidate by dropping the least recently mentioned one; false when there is none. */
    private boolean dropOldestCandidate() {
        return facts.values().stream().filter(f -> f.status() == Status.CANDIDATE).min(Comparator.comparingLong(Fact::lastSeen))
                .map(f -> facts.remove(f.id()) != null).orElse(false);
    }

    private static List<String> withPlayer(List<String> players, String player) {
        List<String> out = new ArrayList<>(players);
        out.removeIf(p -> p.equalsIgnoreCase(player));
        out.add(player);
        while (out.size() > MAX_PLAYERS_KEPT) out.removeFirst();
        return List.copyOf(out);
    }

    /** One line, no knowledge-file syntax at the start (a fact must not become a heading or a comment). */
    static String clean(String raw) {
        String t = raw == null ? "" : raw.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").strip();
        while (t.startsWith("#") || t.startsWith("//")) t = t.replaceFirst("^(#+|//)", "").strip();
        if (t.regionMatches(true, 0, "keywords:", 0, 9)) t = t.substring(9).strip();
        return t;
    }

    /** Serializes the current state as a new version. Call with this object's lock held. */
    private Snapshot snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("next-id", nextId);
        JsonArray arr = new JsonArray();
        for (Fact f : facts.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("text", f.text());
            o.addProperty("status", f.status().name().toLowerCase(Locale.ROOT));
            o.addProperty("mentions", f.mentions());
            JsonArray ps = new JsonArray();
            f.players().forEach(ps::add);
            o.add("players", ps);
            o.addProperty("first-seen", f.firstSeen());
            o.addProperty("last-seen", f.lastSeen());
            if (!f.decidedBy().isEmpty()) {
                o.addProperty("decided-by", f.decidedBy());
                o.addProperty("decided-at", f.decidedAt());
            }
            arr.add(o);
        }
        root.add("facts", arr);
        return new Snapshot(++version, GSON.toJson(root) + "\n");
    }

    /** Writes the snapshot through a temporary file, unless a newer one is already on disk. Blocking. */
    private void write(@Nullable Snapshot snap) {
        if (snap == null) return;
        synchronized (fileLock) {
            if (snap.version() <= written) return;
            Path tmp = file.resolveSibling(FILE + ".tmp");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(tmp, snap.json(), StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                written = snap.version();
            } catch (IOException e) {
                log.warning("Cannot write " + FILE + " (" + e.getClass().getSimpleName() + ")");
            }
        }
    }
}
