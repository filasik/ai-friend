package me.skerik.aifriend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatBufferTest {

    private static ChatLine line(int i) {
        return new ChatLine("p", "m" + i);
    }

    @Test
    void dropsOldestWhenOverCapacity() {
        ChatBuffer b = new ChatBuffer(3);
        for (int i = 1; i <= 5; i++) b.add(line(i));
        assertEquals(List.of(line(3), line(4), line(5)), b.snapshot());
        assertEquals(3, b.size());
    }

    @Test
    void removeTakesOnlyTheSnapshotAndKeepsLaterLines() {
        ChatBuffer b = new ChatBuffer(10);
        b.add(line(1));
        b.add(line(2));
        List<ChatLine> sent = b.snapshot();
        b.add(line(3));
        b.remove(sent);
        assertEquals(List.of(line(3)), b.snapshot());
        assertEquals(1, b.size());
    }

    @Test
    void removeIgnoresLinesThatWereDroppedMeanwhile() {
        ChatBuffer b = new ChatBuffer(2);
        b.add(line(1));
        b.add(line(2));
        List<ChatLine> sent = b.snapshot();
        b.add(line(3)); // drops line 1
        b.remove(sent);
        assertEquals(List.of(line(3)), b.snapshot());
        assertEquals(1, b.size());
    }

    @Test
    void shrinkingCapacityDropsOldest() {
        ChatBuffer b = new ChatBuffer(5);
        for (int i = 1; i <= 5; i++) b.add(line(i));
        b.setCapacity(2);
        assertEquals(List.of(line(4), line(5)), b.snapshot());
        assertEquals(2, b.size());
    }

    @Test
    void removeFromDropsOnlyThatPlayersLines() {
        ChatBuffer b = new ChatBuffer(10);
        b.add(new ChatLine("Steve", "a"));
        b.add(new ChatLine("Alex", "b"));
        b.add(new ChatLine("Steve", "c"));
        b.removeFrom("Steve");
        assertEquals(List.of(new ChatLine("Alex", "b")), b.snapshot());
        assertEquals(1, b.size());
        b.removeFrom("nobody");
        b.removeFrom("steve"); // names are case sensitive
        assertEquals(1, b.size());
        b.add(new ChatLine("Steve", "d"));
        assertEquals(2, b.size());
    }

    @Test
    void clearEmptiesTheBuffer() {
        ChatBuffer b = new ChatBuffer(5);
        b.add(line(1));
        b.clear();
        assertEquals(0, b.size());
        assertTrue(b.snapshot().isEmpty());
    }

    @Test
    void concurrentAddsKeepSizeConsistent() throws Exception {
        int threads = 8;
        int each = 1000;
        ChatBuffer unbounded = new ChatBuffer(threads * each);
        ChatBuffer bounded = new ChatBuffer(100);
        run(threads, each, unbounded, bounded);
        assertEquals(threads * each, unbounded.size());
        assertEquals(threads * each, unbounded.snapshot().size());
        assertEquals(bounded.snapshot().size(), bounded.size());
        assertTrue(bounded.size() <= 100);
    }

    private static void run(int threads, int each, ChatBuffer... buffers) throws InterruptedException {
        List<Thread> all = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread th = new Thread(() -> {
                for (int i = 0; i < each; i++) {
                    ChatLine l = new ChatLine("t" + id, "m" + i);
                    for (ChatBuffer b : buffers) b.add(l);
                }
            });
            all.add(th);
            th.start();
        }
        for (Thread th : all) th.join();
    }
}
