package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.skerik.aifriend.chat.SimulationResult.Blocker;
import org.junit.jupiter.api.Test;

class SimulationResultTest {

    @Test
    void answersWhenNothingBlocks() {
        var r = new SimulationResult("Pirate", "<Steve> hi", 2, 1, "Arr!", "Arr!", List.of());
        assertTrue(r.wouldSend());
        assertTrue(r.wouldAnswer());
    }

    @Test
    void blockersPreventSending() {
        var r = new SimulationResult(null, "", 0, 0, null, null, List.of(Blocker.NO_KEY, Blocker.NO_CHAT));
        assertFalse(r.wouldSend());
        assertFalse(r.wouldAnswer());
    }

    @Test
    void filteredOutReplyIsNotAnAnswer() {
        var r = new SimulationResult(null, "x", 0, 1, "bad", null, List.of());
        assertTrue(r.wouldSend());
        assertFalse(r.wouldAnswer());
    }

    @Test
    void blockersAreCopied() {
        var list = new ArrayList<>(List.of(Blocker.SLEEPING));
        var r = new SimulationResult(null, "x", 0, 1, null, null, list);
        list.clear();
        assertEquals(1, r.blockers().size());
    }

    @Test
    void tokenEstimate() {
        assertEquals(0, SimulationResult.estimateTokens(""));
        assertEquals(1, SimulationResult.estimateTokens("abcd"));
        assertEquals(2, SimulationResult.estimateTokens("abcde"));
        assertEquals(3, new SimulationResult(null, "a".repeat(10), 0, 1, null, null, List.of()).estimatedInputTokens());
    }
}
