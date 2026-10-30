package me.skerik.aifriend.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.TestSettings;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.knowledge.LearnedFacts.Action;
import me.skerik.aifriend.knowledge.LearnedFacts.Fact;
import me.skerik.aifriend.knowledge.LearnedFacts.Proposal;
import me.skerik.aifriend.knowledge.LearnedFacts.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearnedFactsTest {

    private static final Logger LOG = Logger.getLogger("LearnedFactsTest");
    private static final LearnedFacts.Config TWO = new LearnedFacts.Config(true, 6, 2, false, 30, true);
    private static final String CHESTS = "Klíče a truhly se otevírají příkazem /truhly";

    static {
        LOG.setLevel(Level.OFF);
    }

    @TempDir Path tmp;
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private LearnedFacts facts;

    @BeforeEach
    void setUp() {
        facts = new LearnedFacts(tmp, LOG, clock::get);
        facts.load();
    }

    private List<Fact> observe(LearnedFacts.Config c, Proposal... p) {
        clock.addAndGet(1000);
        return facts.observe(List.of(p), c, t -> false);
    }

    @Test
    void aFactBecomesPendingOnlyAfterMinMentionsRounds() {
        assertTrue(observe(TWO, Proposal.fresh(CHESTS, "Steve")).isEmpty());
        Fact candidate = facts.list(Status.CANDIDATE).getFirst();
        assertEquals(1, candidate.mentions());
        // the same round cannot count it twice
        assertTrue(observe(TWO, Proposal.known(candidate.id(), "Alex"), Proposal.known(candidate.id(), "Bob")).size() == 1);
        Fact pending = facts.list(Status.PENDING).getFirst();
        assertEquals(2, pending.mentions());
        assertEquals(List.of("Steve", "Alex"), pending.players());
        assertTrue(facts.list(Status.CANDIDATE).isEmpty());
    }

    @Test
    void aSimilarTextIsTheSameFactNotANewOne() {
        observe(TWO, Proposal.fresh(CHESTS, "Steve"));
        List<Fact> promoted = observe(TWO, Proposal.fresh("Truhly a klíče se otevírají přes /truhly", "Alex"));
        assertEquals(1, promoted.size());
        assertEquals(CHESTS, promoted.getFirst().text());
        assertEquals(1, facts.counts().values().stream().mapToInt(Integer::intValue).sum());
        // something else is a new candidate
        observe(TWO, Proposal.fresh("Obchody jsou na /warp shop u spawnu", "Alex"));
        assertEquals(1, facts.count(Status.CANDIDATE));
    }

    @Test
    void aRejectedFactIsNeverProposedAgainButADeletedOneMayReturn() {
        observe(TWO, Proposal.fresh(CHESTS, "Steve"));
        int id = facts.list(Status.CANDIDATE).getFirst().id();
        assertEquals(Status.REJECTED, facts.decide(id, Action.REJECT, "Admin").status());
        assertTrue(observe(TWO, Proposal.fresh(CHESTS, "Alex")).isEmpty());
        assertTrue(observe(TWO, Proposal.fresh("Truhly a klíče: příkaz /truhly", "Bob")).isEmpty());
        assertEquals(1, facts.count(Status.REJECTED));
        assertEquals(0, facts.count(Status.PENDING) + facts.count(Status.CANDIDATE));

        assertEquals(CHESTS, facts.decide(id, Action.DELETE, "Admin").text());
        assertNull(facts.get(id));
        observe(TWO, Proposal.fresh(CHESTS, "Alex"));
        assertEquals(1, facts.count(Status.CANDIDATE));
        assertTrue(facts.list(Status.CANDIDATE).getFirst().id() > id, "ids are never reused");
    }

    @Test
    void autoApproveSkipsTheReviewAndMinMentionsOneSkipsTheCandidateStep() {
        LearnedFacts.Config auto = new LearnedFacts.Config(true, 6, 1, true, 30, true);
        List<Fact> promoted = observe(auto, Proposal.fresh(CHESTS, "Steve"));
        assertEquals(Status.APPROVED, promoted.getFirst().status());
        assertEquals("auto", promoted.getFirst().decidedBy());
        LearnedFacts.Config one = new LearnedFacts.Config(true, 6, 1, false, 30, true);
        assertEquals(Status.PENDING, observe(one, Proposal.fresh("Obchody jsou na /warp shop u spawnu", "Alex")).getFirst().status());
    }

    @Test
    void whatTheAdminAlreadyWroteIsNotProposed() {
        assertTrue(facts.observe(List.of(Proposal.fresh(CHESTS, "Steve")), TWO, t -> true).isEmpty());
        assertEquals(0, facts.count(Status.CANDIDATE));
    }

    @Test
    void unknownIdsAndBadTextsAreIgnored() {
        assertTrue(observe(TWO, Proposal.known(99, "Steve"), Proposal.fresh("short", "Steve"),
                Proposal.fresh("x".repeat(LearnedFacts.MAX_FACT_LENGTH + 1), "Steve")).isEmpty());
        assertEquals(0, facts.counts().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void decisionsEditsAndCountsSurviveARestart() {
        observe(TWO, Proposal.fresh(CHESTS, "Steve"));
        int id = facts.list(Status.CANDIDATE).getFirst().id();
        facts.decide(id, Action.APPROVE, "Admin");
        facts.edit(id, "# Keys and chests open at /truhly", "Admin");
        observe(TWO, Proposal.fresh("Obchody jsou na /warp shop u spawnu", "Alex"));

        LearnedFacts again = new LearnedFacts(tmp, LOG, clock::get);
        again.load();
        Fact f = again.get(id);
        assertEquals("Keys and chests open at /truhly", f.text(), "heading syntax is stripped");
        assertEquals(Status.APPROVED, f.status());
        assertEquals("Admin", f.decidedBy());
        assertEquals(1, again.count(Status.CANDIDATE));
        again.observe(List.of(Proposal.fresh("Arena je na /warp pvp kazdy patek", "Bob")), TWO, t -> false);
        assertTrue(again.list(Status.CANDIDATE).stream().allMatch(c -> c.id() > id));
    }

    @Test
    void aBrokenFileIsMovedAsideAndLearningStartsEmpty() throws Exception {
        Files.writeString(tmp.resolve(LearnedFacts.FILE), "{ not json");
        facts.load();
        assertEquals(0, facts.counts().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(Files.exists(tmp.resolve(LearnedFacts.FILE + ".broken")));
    }

    @Test
    void oldCandidatesArePrunedButReviewedFactsStay() {
        observe(TWO, Proposal.fresh(CHESTS, "Steve"));
        observe(TWO, Proposal.fresh("Obchody jsou na /warp shop u spawnu", "Alex"));
        int shop = facts.list(Status.CANDIDATE).getFirst().id();
        facts.decide(shop, Action.REJECT, "Admin");
        clock.addAndGet(31L * 86_400_000L);
        assertEquals(1, facts.prune(TWO));
        assertEquals(0, facts.count(Status.CANDIDATE));
        assertEquals(1, facts.count(Status.REJECTED));
    }

    @Test
    void relatedFindsFactsByWordsWhateverTheirStatus() {
        observe(TWO, Proposal.fresh(CHESTS, "Steve"));
        observe(TWO, Proposal.fresh("Obchody jsou na /warp shop u spawnu", "Alex"));
        facts.decide(facts.list(Status.CANDIDATE).getLast().id(), Action.REJECT, "Admin");
        assertEquals(CHESTS, facts.related("kde otevru truhly?", 5).getFirst().text());
        assertEquals(1, facts.related("kde jsou obchody", 5).size());
        assertTrue(facts.related("", 5).isEmpty());
    }

    @Test
    void onlyApprovedFactsReachTheAnswersAndAdminKnowledgeIsRecognised() throws Exception {
        Files.writeString(tmp.resolve("knowledge.txt"), "# Shops\nThe shops are at /warp shop near the spawn.\n");
        Settings s = TestSettings.defaults().knowledge(new ServerKnowledge.Config(true, "knowledge.txt", 3, 1200, false, TWO)).build();
        ServerKnowledge k = new ServerKnowledge(tmp, LOG, clock::get);
        k.load(s);
        assertTrue(k.knownByAdmin("The shops are at /warp shop near spawn"));
        assertFalse(k.knownByAdmin(CHESTS));

        k.learned().observe(List.of(Proposal.fresh(CHESTS, "Steve")), TWO, k::knownByAdmin);
        int id = k.learned().list(Status.CANDIDATE).getFirst().id();
        k.refreshLearned();
        assertTrue(k.retrieve("kde otevru truhly", s.features().knowledge()).isEmpty(), "a candidate is never used");

        k.learned().decide(id, Action.APPROVE, "Admin");
        k.refreshLearned();
        assertEquals(CHESTS, k.retrieve("kde otevru truhly", s.features().knowledge()).getFirst().text());
        assertEquals(1, k.learnedEntries());

        k.learned().decide(id, Action.DISABLE, "Admin");
        k.refreshLearned();
        assertTrue(k.retrieve("kde otevru truhly", s.features().knowledge()).isEmpty());
        // the admin's own knowledge is still there
        assertFalse(k.retrieve("shops", s.features().knowledge()).isEmpty());
    }
}
