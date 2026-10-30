package me.skerik.aifriend.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.TestSettings;
import me.skerik.aifriend.config.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeTest {

    private static final Logger LOG = Logger.getLogger("KnowledgeTest");

    static {
        LOG.setLevel(Level.OFF);
    }

    @TempDir Path tmp;

    private static KnowledgeBase parse(String text) {
        return KnowledgeBase.parse(text, "test", LOG);
    }

    private static final String FILE = """
            // comment line, ignored
            # Server address
            keywords: ip connect discord
            The server address is example.com.

            # Pravidla serveru
            Žádný griefing, žádné hacky. Porušení pravidel vede k banu.

            # Claiming land
            Use a golden shovel.
            """;

    @Test
    void headingsKeywordsAndCommentsAreParsed() {
        KnowledgeBase kb = parse(FILE);
        assertEquals(3, kb.size());
        assertEquals("Server address", kb.entries().getFirst().title());
        assertEquals("The server address is example.com.", kb.entries().getFirst().text());
    }

    @Test
    void withoutHeadingsEveryParagraphIsAnEntry() {
        KnowledgeBase kb = parse("First fact about shops.\nSecond line of it.\n\nSecond fact about the arena.\n");
        assertEquals(2, kb.size());
        assertEquals("First fact about shops. Second line of it.", kb.entries().getFirst().text());
        assertEquals("", kb.entries().getFirst().title());
    }

    @Test
    void retrievalFindsTheFittingEntryAcrossInflectionAndAccents() {
        KnowledgeBase kb = parse(FILE);
        assertEquals("Server address", kb.retrieve("what is the server address?", 3, 1000).getFirst().title());
        assertEquals("Server address", kb.retrieve("how do I connect, which ip", 3, 1000).getFirst().title()); // keyword
        assertEquals("Pravidla serveru", kb.retrieve("jaká jsou pravidla", 3, 1000).getFirst().title());
        assertEquals("Pravidla serveru", kb.retrieve("co když porusim pravidlech?", 3, 1000).getFirst().title()); // inflected
    }

    @Test
    void nothingMatchesNothingIsReturned() {
        KnowledgeBase kb = parse(FILE);
        assertTrue(kb.retrieve("hello how are you doing today", 3, 1000).isEmpty());
        assertTrue(kb.retrieve("", 3, 1000).isEmpty());
        assertTrue(KnowledgeBase.empty().retrieve("server address", 3, 1000).isEmpty());
    }

    @Test
    void limitsOnEntriesAndCharactersAreRespected() {
        KnowledgeBase kb = parse("# Shop one\nShop alpha sells bread.\n# Shop two\nShop beta sells bread.\n# Shop three\nShop gamma sells bread.\n");
        assertEquals(2, kb.retrieve("shop bread", 2, 1000).size());
        assertEquals(1, kb.retrieve("shop bread", 3, 40).size(), "the second entry no longer fits the character budget");
    }

    @Test
    void anOverlongEntryIsCut() {
        KnowledgeBase kb = parse("# Long\n" + "word ".repeat(300));
        assertEquals(KnowledgeBase.MAX_ENTRY_CHARS, kb.entries().getFirst().text().length());
    }

    // ---- files, self-knowledge, teach ----

    private static Settings enabled() {
        return TestSettings.defaults().knowledge(new ServerKnowledge.Config(true, "knowledge.txt", 3, 1200, true)).build();
    }

    @Test
    void loadCreatesTheSampleAndIncludesSelfKnowledge() throws Exception {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        Settings s = enabled();
        k.load(s);
        assertTrue(Files.exists(tmp.resolve("knowledge.txt")), "sample file created");
        assertTrue(k.fileEntries() >= 3);
        assertTrue(k.selfEntries() >= 10);
        var cfg = s.features().knowledge();
        // the bot can explain itself, in English and Czech
        assertTrue(k.retrieve("what can you see, do you read private messages?", cfg).stream()
                .anyMatch(e -> e.title().equals("What I can see")));
        assertTrue(k.retrieve("špehuješ nás? vidíš soukromé zprávy?", cfg).stream()
                .anyMatch(e -> e.title().equals("What I can see")));
        assertTrue(k.retrieve("můžeš mi dát op nebo give diamanty", cfg).stream()
                .anyMatch(e -> e.title().equals("What I cannot do")));
    }

    @Test
    void theFeatureStatusFollowsTheConfiguration() {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        k.load(enabled());
        var found = k.retrieve("which features are on or off, what can you do", enabled().features().knowledge());
        assertTrue(found.stream().anyMatch(e -> e.title().startsWith("Which features are off")
                && e.text().startsWith("Off:") && e.text().contains("long-term memory")), found.toString());
        assertTrue(found.stream().anyMatch(e -> e.title().startsWith("Which features are on") && e.text().startsWith("On:")),
                found.toString());
        // every feature switched on still fits into an entry (no cut, no warning)
        assertTrue(SelfKnowledge.status(enabled()).length() < 2 * KnowledgeBase.MAX_ENTRY_CHARS);
    }

    @Test
    void disabledMeansNothingIsLoadedOrReturned() {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        Settings off = TestSettings.defaults().build();
        k.load(off);
        assertFalse(Files.exists(tmp.resolve("knowledge.txt")), "no files are created while the feature is off");
        assertTrue(k.retrieve("server address", off.features().knowledge()).isEmpty());
    }

    @Test
    void selfKnowledgeCanBeSwitchedOff() {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        Settings s = TestSettings.defaults().knowledge(new ServerKnowledge.Config(true, "knowledge.txt", 3, 1200, false)).build();
        k.load(s);
        assertEquals(0, k.selfEntries());
        assertTrue(k.retrieve("what can you see, private messages", s.features().knowledge()).isEmpty());
    }

    @Test
    void teachAppendsFindsAndForgets() throws Exception {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        Settings s = enabled();
        k.load(s);
        assertEquals(ServerKnowledge.TeachResult.ADDED, k.teach("The arena opens every Friday at 18:00.", s));
        assertEquals(ServerKnowledge.TeachResult.ADDED, k.teach("# sneaky heading\nsecond line", s));
        assertEquals(List.of("The arena opens every Friday at 18:00.", "sneaky heading second line"), k.taught());
        assertTrue(k.retrieve("when does the arena open", s.features().knowledge()).stream()
                .anyMatch(e -> e.text().contains("Friday")), "usable at once, without a reload");
        assertTrue(Files.readString(tmp.resolve("knowledge-taught.txt")).contains("Friday"));
        assertFalse(Files.readString(tmp.resolve("knowledge.txt")).contains("Friday"), "the admin's file is never rewritten");

        assertEquals("The arena opens every Friday at 18:00.", k.forgetTaught(1, s));
        assertNull(k.forgetTaught(5, s));
        assertEquals(List.of("sneaky heading second line"), k.taught());
        assertTrue(k.retrieve("when does the arena open", s.features().knowledge()).stream().noneMatch(e -> e.text().contains("Friday")));
    }

    @Test
    void teachRejectsEmptyAndTooLongFacts() {
        ServerKnowledge k = new ServerKnowledge(tmp, LOG);
        Settings s = enabled();
        assertEquals(ServerKnowledge.TeachResult.EMPTY, k.teach("  \n # ", s));
        assertEquals(ServerKnowledge.TeachResult.TOO_LONG, k.teach("x".repeat(ServerKnowledge.MAX_TAUGHT_CHARS + 1), s));
        assertTrue(k.taught().isEmpty());
    }

    @Test
    void configValidatesTheFileName() {
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("file", "../etc/passwd");
        assertEquals("knowledge.txt", ServerKnowledge.Config.load(y, log).file());
        y.set("file", "knowledge-taught.txt");
        assertEquals("knowledge.txt", ServerKnowledge.Config.load(y, log).file());
        assertEquals(2, warnings.size());
    }
}
