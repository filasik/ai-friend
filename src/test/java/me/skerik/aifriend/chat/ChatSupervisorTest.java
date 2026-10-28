package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatSupervisorTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final long T0 = 1_000_000L;

    private static ChatSupervisor on(String... bad) {
        return new ChatSupervisor(new ChatSupervisor.Config(true, true, true, true, List.of(bad), 12, 70, 60));
    }

    private static Optional<ChatSupervisor.Issue> look(ChatSupervisor s, UUID p, String text, long at) {
        return s.inspect(p, text, at);
    }

    @Test
    void offByDefaultAndNormalChatIsNeverFlagged() {
        ChatSupervisor off = new ChatSupervisor(ChatSupervisor.Config.defaults());
        assertTrue(look(off, A, "AAAAAAAAAAAAAAAAAAAAAA", T0).isEmpty());
        ChatSupervisor s = on("idiot");
        assertTrue(look(s, A, "Hello everybody, I found diamonds near spawn!", T0).isEmpty());
        assertTrue(look(s, A, "OK", T0 + 1000).isEmpty()); // short shouting is fine
        assertTrue(look(s, A, "I love TNT and Minecraft so much", T0 + 2000).isEmpty());
    }

    @Test
    void shoutingNeedsEnoughLettersAndEnoughCapitals() {
        ChatSupervisor s = on();
        assertEquals(ChatSupervisor.Issue.CAPS, look(s, A, "WHO TOOK MY DIAMONDS FROM THE CHEST", T0).orElseThrow());
        assertTrue(look(s, B, "WHO took my diamonds from the chest", T0).isEmpty());
        assertTrue(look(s, B, "SHORT", T0 + 1000).isEmpty());
        assertTrue(look(s, B, "12345678901234567890 !!!!!!!!", T0 + 2000).isEmpty()); // no letters
    }

    @Test
    void swearingIsMatchedAsWholeWordsIgnoringAccentsCaseLookalikesAndStretching() {
        ChatSupervisor s = on("idiot", "kretén");
        assertEquals(ChatSupervisor.Issue.LANGUAGE, look(s, A, "you are an idiot", T0).orElseThrow());
        assertEquals(ChatSupervisor.Issue.LANGUAGE, look(s, B, "IDIOT!", T0).orElseThrow());
        assertEquals(ChatSupervisor.Issue.LANGUAGE, look(s, UUID.randomUUID(), "what an 1d10t", T0).orElseThrow());
        assertEquals(ChatSupervisor.Issue.LANGUAGE, look(s, UUID.randomUUID(), "ty kreten jeden", T0).orElseThrow());
        assertEquals(ChatSupervisor.Issue.LANGUAGE, look(s, UUID.randomUUID(), "iiiidiiiot", T0).orElseThrow());
        // a bad word inside a longer word is not a bad word
        assertTrue(look(s, UUID.randomUUID(), "idiotproof design", T0).isEmpty());
    }

    @Test
    void anEmptyWordListNeverFlagsLanguage() {
        assertTrue(look(on(), A, "anything at all you idiot", T0).isEmpty());
    }

    @Test
    void repeatingTheSameLineIsSpam() {
        ChatSupervisor s = on();
        assertTrue(look(s, A, "buy my stuff", T0).isEmpty());
        assertTrue(look(s, A, "buy my stuff", T0 + 1000).isEmpty());
        assertEquals(ChatSupervisor.Issue.SPAM, look(s, A, "Buy  my stuff!", T0 + 2000).orElseThrow()); // same after folding
    }

    @Test
    void writingVeryFastIsSpamButSlowlyIsNot() {
        ChatSupervisor s = on();
        Optional<ChatSupervisor.Issue> last = Optional.empty();
        for (int i = 0; i < 6; i++) last = look(s, A, "message number " + "abcdef".charAt(i), T0 + i * 500L);
        assertEquals(ChatSupervisor.Issue.SPAM, last.orElseThrow());
        for (int i = 0; i < 12; i++) assertTrue(look(s, B, "line " + "abcdefghijkl".charAt(i), T0 + i * 11_000L).isEmpty());
    }

    @Test
    void aPlayerIsToldOnceAndThenLeftAloneUntilTheCooldownIsOver() {
        ChatSupervisor s = on();
        assertTrue(look(s, A, "AAAAAAAAAAAAAAAAAAAAAAAA", T0).isPresent());
        assertTrue(look(s, A, "BBBBBBBBBBBBBBBBBBBBBBBB", T0 + 30_000).isEmpty());
        assertTrue(look(s, A, "CCCCCCCCCCCCCCCCCCCCCCCC", T0 + 61_000).isPresent());
        // another player and another kind are independent
        assertTrue(look(s, B, "DDDDDDDDDDDDDDDDDDDDDDDD", T0 + 62_000).isPresent());
    }

    @Test
    void switchesAreIndependent() {
        ChatSupervisor onlyCaps = new ChatSupervisor(new ChatSupervisor.Config(true, true, false, false, List.of("idiot"), 12, 70, 0));
        assertTrue(look(onlyCaps, A, "you idiot", T0).isEmpty());
        for (int i = 0; i < 8; i++) assertTrue(look(onlyCaps, A, "same", T0 + i).isEmpty());
        assertTrue(look(onlyCaps, A, "SHOUTING LIKE CRAZY HERE", T0 + 100).isPresent());
    }

    @Test
    void textIsNotKeptOnlyHashes() throws Exception {
        // the supervisor has no field that could hold a chat line: spam detection stores ints
        for (var f : ChatSupervisor.class.getDeclaredFields()) {
            assertTrue(!f.getType().equals(String.class) || java.lang.reflect.Modifier.isStatic(f.getModifiers()), f.getName());
        }
    }

    @Test
    void configLoadsWithBounds() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("supervision.enabled", true);
        y.set("supervision.bad-words", List.of("foo", " ", "bar"));
        y.set("supervision.caps-percent", 20);
        var log = java.util.logging.Logger.getLogger("SupervisorTest");
        log.setLevel(java.util.logging.Level.OFF);
        ChatSupervisor.Config c = ChatSupervisor.Config.load(y.getConfigurationSection("supervision"), log);
        assertTrue(c.enabled());
        assertEquals(List.of("foo", "bar"), c.badWords());
        assertEquals(70, c.capsPercent()); // 20 is below 50 -> default
        assertEquals(ChatSupervisor.Config.defaults(), ChatSupervisor.Config.load(null, log));
    }
}
