package me.skerik.aifriend.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Staff, party and local channels reach only some players and must never be sent to the AI. */
class ChatListenerTest {

    @Test
    void everybodyOnlineIsPublic() {
        assertTrue(ChatListener.isPublic(1, 1));
        assertTrue(ChatListener.isPublic(30, 30));
    }

    @Test
    void aNarrowedAudienceIsPrivate() {
        assertFalse(ChatListener.isPublic(2, 30));   // staff chat
        assertFalse(ChatListener.isPublic(5, 40));   // party
        assertFalse(ChatListener.isPublic(1, 2));    // tiny server: every online player must see it
        assertFalse(ChatListener.isPublic(0, 3));
    }

    @Test
    void aFewIgnoringPlayersDoNotMakeItPrivate() {
        assertTrue(ChatListener.isPublic(9, 10));    // one ignores the sender
        assertFalse(ChatListener.isPublic(7, 10));
    }
}
