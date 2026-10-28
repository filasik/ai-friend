package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    @Test
    void inactiveByDefault() {
        var m = new ConversationMemory(ConversationMemory.Config.defaults());
        m.add("hi", "hello");
        assertFalse(m.isActive());
        assertTrue(m.history().isEmpty());
    }

    @Test
    void zeroExchangesMeansOff() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 0, 1000));
        m.add("hi", "hello");
        assertTrue(m.history().isEmpty());
    }

    @Test
    void keepsLastNInOrder() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 2, 1000));
        m.add("q1", "a1");
        m.add("q2", "a2");
        m.add("q3", "a3");
        List<ConversationMemory.Turn> h = m.history();
        assertEquals(4, h.size());
        assertEquals(new ConversationMemory.Turn(ConversationMemory.Role.USER, "q2"), h.get(0));
        assertEquals(new ConversationMemory.Turn(ConversationMemory.Role.ASSISTANT, "a2"), h.get(1));
        assertEquals("q3", h.get(2).text());
        assertEquals(ConversationMemory.Role.ASSISTANT, h.get(3).role());
    }

    @Test
    void charBudgetDropsOldestWholeExchanges() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 10, 20));
        m.add("12345", "12345"); // 10
        m.add("abcde", "abcde"); // 20, fits exactly
        assertEquals(2, m.size());
        m.add("x", "y"); // 22 -> drop the oldest
        assertEquals(2, m.size());
        assertEquals("abcde", m.history().get(0).text());
    }

    @Test
    void exchangeBiggerThanBudgetIsNotKept() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 5, 10));
        m.add("short", "ok");
        m.add("a".repeat(50), "b");
        assertEquals(0, m.size());
    }

    @Test
    void blankAndColourCodesAreHandled() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 5, 1000));
        m.add("   ", "answer");
        m.add("question", "");
        assertEquals(0, m.size());
        m.add("§aHi\nthere", "Hello §cworld");
        assertEquals("Hi there", m.history().get(0).text());
        assertEquals("Hello world", m.history().get(1).text());
    }

    @Test
    void clearEmptiesAndResetsBudget() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 5, 10));
        m.add("aaaa", "bbbb");
        m.clear();
        assertEquals(0, m.size());
        m.add("cccc", "dddd");
        assertEquals(1, m.size());
    }

    @Test
    void historyIsASnapshot() {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 5, 1000));
        m.add("q", "a");
        var h = m.history();
        m.add("q2", "a2");
        assertEquals(2, h.size());
    }

    @Test
    void concurrentAddsStayBounded() throws Exception {
        var m = new ConversationMemory(new ConversationMemory.Config(true, 7, 100000));
        ExecutorService ex = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            ex.submit(() -> {
                for (int i = 0; i < 500; i++) {
                    m.add("q" + i, "a" + i);
                    m.history();
                }
            });
        }
        ex.shutdown();
        assertTrue(ex.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(7, m.size());
        assertEquals(14, m.history().size());
    }

    @Test
    void loadValidatesWithFallback() throws Exception {
        var y = new YamlConfiguration();
        y.loadFromString("enabled: true\nexchanges: 500\nmax-chars: 50\n");
        var c = ConversationMemory.Config.load(y, Logger.getAnonymousLogger());
        assertTrue(c.enabled());
        assertEquals(5, c.exchanges());
        assertEquals(2000, c.maxChars());
        assertFalse(ConversationMemory.Config.load(null, Logger.getAnonymousLogger()).enabled());
    }
}
