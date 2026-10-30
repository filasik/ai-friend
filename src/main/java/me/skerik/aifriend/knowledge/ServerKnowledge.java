package me.skerik.aifriend.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import me.skerik.aifriend.chat.Cfg;
import me.skerik.aifriend.config.ChatFeatures;
import me.skerik.aifriend.config.Settings;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * What the bot knows about this server and about itself. Three sources are searched together for every request: the
 * admin's {@code knowledge.txt} (hand-written), {@code knowledge-taught.txt} (facts added with {@code /aifriend teach},
 * owned by the plugin so the hand-written file is never rewritten) and the plugin's own self-description (what it sees,
 * what it stores, its commands, which features are on right now). File access is blocking: call from an async task.
 * Only the admin can add knowledge; players cannot write to it.
 */
public final class ServerKnowledge {

    /** Config section {@code knowledge}. Default: off. */
    public record Config(boolean enabled, String file, int maxEntries, int maxChars, boolean selfKnowledge,
                         LearnedFacts.Config learning) {

        public static final String DEFAULT_FILE = "knowledge.txt";
        public static final String TAUGHT_FILE = "knowledge-taught.txt";

        public Config(boolean enabled, String file, int maxEntries, int maxChars, boolean selfKnowledge) {
            this(enabled, file, maxEntries, maxChars, selfKnowledge, LearnedFacts.Config.defaults());
        }

        public static Config defaults() {
            return new Config(false, DEFAULT_FILE, 3, 1200, true);
        }

        /** Learning from the chat needs the knowledge it feeds. */
        public boolean learningOn() {
            return enabled && learning.enabled();
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            String file = Cfg.string(s, "file", DEFAULT_FILE);
            if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..") || file.equals(TAUGHT_FILE)) {
                log.warning("knowledge.file must be a plain file name inside the plugin folder (not " + TAUGHT_FILE
                        + "); using " + DEFAULT_FILE);
                file = DEFAULT_FILE;
            }
            return new Config(Cfg.bool(s, "enabled", false), file,
                    Cfg.bounded(s, "max-entries-per-request", 1, 10, 3, log),
                    Cfg.bounded(s, "max-chars-per-request", 200, 6000, 1200, log),
                    Cfg.bool(s, "self-knowledge", true),
                    LearnedFacts.Config.load(s == null ? null : s.getConfigurationSection("learning"), log));
        }
    }

    public static final int MAX_TAUGHT = 500;
    public static final int MAX_TAUGHT_CHARS = 400;

    private final Path folder;
    private final Logger log;
    private volatile KnowledgeBase base = KnowledgeBase.empty();
    /** knowledge.txt + taught facts: what the admin wrote, so learning does not propose it again. */
    private volatile KnowledgeBase adminBase = KnowledgeBase.empty();
    private final LearnedFacts learned;
    private volatile int learnedEntries;
    private volatile @Nullable Settings lastSettings;
    private volatile int fileEntries;
    private volatile int selfEntries;
    private final Object taughtLock = new Object();

    public ServerKnowledge(Path folder, Logger log) {
        this(folder, log, System::currentTimeMillis);
    }

    public ServerKnowledge(Path folder, Logger log, java.util.function.LongSupplier clock) {
        this.folder = folder;
        this.log = log;
        this.learned = new LearnedFacts(folder, log, clock);
    }

    /** (Re)reads every source. Creates the sample file when the knowledge file does not exist yet. Blocking. */
    public synchronized void load(Settings settings) {
        lastSettings = settings;
        ChatFeatures f = settings.features();
        Config c = f.knowledge();
        if (!c.enabled()) {
            base = KnowledgeBase.empty();
            adminBase = KnowledgeBase.empty();
            fileEntries = 0;
            selfEntries = 0;
            learnedEntries = 0;
            return;
        }
        if (c.learningOn()) learned.load();
        List<KnowledgeBase> parts = new ArrayList<>();
        Path file = folder.resolve(c.file());
        if (!Files.exists(file)) copySample(file);
        KnowledgeBase fromFile = parseFile(file, c.file());
        fileEntries = fromFile.size();
        parts.add(fromFile);
        KnowledgeBase taught = parseFile(folder.resolve(Config.TAUGHT_FILE), Config.TAUGHT_FILE);
        parts.add(taught);
        fileEntries += taught.size();
        adminBase = KnowledgeBase.of(List.copyOf(parts));
        KnowledgeBase approved = learnedBase(c);
        learnedEntries = approved.size();
        parts.add(approved);
        KnowledgeBase self = KnowledgeBase.empty();
        if (c.selfKnowledge()) {
            self = KnowledgeBase.of(List.of(
                    KnowledgeBase.parse(resource("/self-knowledge.txt"), "self-knowledge", log),
                    KnowledgeBase.parse(SelfKnowledge.status(settings), "self-knowledge", log)));
            parts.add(self);
        }
        selfEntries = self.size();
        base = KnowledgeBase.of(parts);
    }

    /** The approved learned facts as one entry each (learning on), otherwise nothing. */
    private KnowledgeBase learnedBase(Config c) {
        if (!c.learningOn()) return KnowledgeBase.empty();
        StringBuilder text = new StringBuilder();
        for (LearnedFacts.Fact fact : learned.list(LearnedFacts.Status.APPROVED)) text.append(fact.text()).append("\n\n");
        return KnowledgeBase.parse(text.toString(), LearnedFacts.FILE, log);
    }

    /** Rebuilds the search index after a learned fact was approved, disabled, edited... (no file is re-read). Blocking. */
    public synchronized void refreshLearned() {
        Settings s = lastSettings;
        if (s == null || !s.features().knowledge().enabled()) return;
        List<KnowledgeBase> parts = new ArrayList<>();
        parts.add(adminBase);
        KnowledgeBase approved = learnedBase(s.features().knowledge());
        learnedEntries = approved.size();
        parts.add(approved);
        if (s.features().knowledge().selfKnowledge()) {
            parts.add(KnowledgeBase.parse(resource("/self-knowledge.txt"), "self-knowledge", log));
            parts.add(KnowledgeBase.parse(SelfKnowledge.status(s), "self-knowledge", log));
        }
        base = KnowledgeBase.of(parts);
    }

    /** The learned facts (proposals, review state). Usable only while learning is on; see {@link Config#learningOn}. */
    public LearnedFacts learned() {
        return learned;
    }

    public int learnedEntries() {
        return learnedEntries;
    }

    /** The admin's entries (knowledge.txt, taught) that fit the text, as "title: text" lines. Never blocks. */
    public List<String> adminRelated(String query, Config config) {
        List<String> out = new ArrayList<>();
        for (KnowledgeBase.Entry e : adminBase.retrieve(query, config.maxEntries(), config.maxChars())) {
            out.add(e.title().isEmpty() ? e.text() : e.title() + ": " + e.text());
        }
        return out;
    }

    /** True when knowledge.txt or the taught facts already say this (learning should not propose it). */
    public boolean knownByAdmin(String text) {
        for (KnowledgeBase.Entry e : adminBase.entries()) {
            if (LearnedFacts.same(e.text(), text) || !e.title().isEmpty() && LearnedFacts.same(e.title() + " " + e.text(), text)) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeBase parseFile(Path file, String name) {
        try {
            if (!Files.exists(file)) return KnowledgeBase.empty();
            if (Files.size(file) > 512 * 1024) {
                log.warning("Knowledge file " + name + " is larger than 512 KB and was ignored");
                return KnowledgeBase.empty();
            }
            return KnowledgeBase.parse(Files.readString(file, StandardCharsets.UTF_8), name, log);
        } catch (IOException e) {
            log.warning("Cannot read " + name + " (" + e.getClass().getSimpleName() + ")");
            return KnowledgeBase.empty();
        }
    }

    private void copySample(Path target) {
        try {
            Files.createDirectories(folder);
            Files.writeString(target, resource("/knowledge-sample.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.warning("Cannot create the sample knowledge file (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String resource(String path) {
        try (InputStream in = ServerKnowledge.class.getResourceAsStream(path)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** The facts that fit the question (empty when the feature is off or nothing matches). Never blocks. */
    public List<KnowledgeBase.Entry> retrieve(String query, Config config) {
        if (!config.enabled()) return List.of();
        return base.retrieve(query, config.maxEntries(), config.maxChars());
    }

    public int fileEntries() {
        return fileEntries;
    }

    public int selfEntries() {
        return selfEntries;
    }

    // ---- /aifriend teach ----

    public enum TeachResult { ADDED, EMPTY, TOO_LONG, FULL, ERROR }

    /** Appends a fact to the taught file and reloads the index. Blocking. */
    public TeachResult teach(String raw, Settings settings) {
        String text = raw.replaceAll("[\\r\\n#]+", " ").replaceAll("\\s+", " ").strip();
        if (text.startsWith("//")) text = text.substring(2).strip();
        if (text.isEmpty()) return TeachResult.EMPTY;
        if (text.length() > MAX_TAUGHT_CHARS) return TeachResult.TOO_LONG;
        synchronized (taughtLock) {
            List<String> all = taught();
            if (all.size() >= MAX_TAUGHT) return TeachResult.FULL;
            all.add(text);
            if (!write(all)) return TeachResult.ERROR;
        }
        load(settings);
        return TeachResult.ADDED;
    }

    /** The taught facts, oldest first. */
    public List<String> taught() {
        synchronized (taughtLock) {
            Path file = folder.resolve(Config.TAUGHT_FILE);
            List<String> out = new ArrayList<>();
            try {
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        String t = line.strip();
                        if (!t.isEmpty() && !t.startsWith("//")) out.add(t);
                    }
                }
            } catch (IOException e) {
                log.warning("Cannot read " + Config.TAUGHT_FILE + " (" + e.getClass().getSimpleName() + ")");
            }
            return out;
        }
    }

    /** Removes the taught fact with this number (1 = first). Returns the removed text, or null when there is none. */
    public @Nullable String forgetTaught(int number, Settings settings) {
        String removed;
        synchronized (taughtLock) {
            List<String> all = taught();
            if (number < 1 || number > all.size()) return null;
            removed = all.remove(number - 1);
            if (!write(all)) return null;
        }
        load(settings);
        return removed;
    }

    /** One fact per paragraph, written through a temporary file so a crash cannot leave half a file. */
    private boolean write(List<String> facts) {
        Path file = folder.resolve(Config.TAUGHT_FILE);
        Path tmp = folder.resolve(Config.TAUGHT_FILE + ".tmp");
        try {
            Files.createDirectories(folder);
            Files.writeString(tmp, "// Facts added with /aifriend teach. One per paragraph; you can edit this file.\n\n"
                    + String.join("\n\n", facts) + (facts.isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warning("Cannot write " + Config.TAUGHT_FILE + " (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }
}
