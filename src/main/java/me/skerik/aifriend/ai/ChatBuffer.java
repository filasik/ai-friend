package me.skerik.aifriend.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, thread-safe queue of chat lines. Chat events (async) add, the bot's timer reads a snapshot and removes
 * exactly the lines that were answered, so lines that arrive during a slow request or survive a failed one are kept.
 */
public final class ChatBuffer {

    private final ConcurrentLinkedQueue<ChatLine> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private volatile int capacity;

    public ChatBuffer(int capacity) {
        this.capacity = capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        trim();
    }

    public void add(ChatLine line) {
        queue.add(line);
        size.incrementAndGet();
        trim();
    }

    private void trim() {
        while (size.get() > capacity) {
            if (queue.poll() == null) break;
            size.decrementAndGet();
        }
    }

    /** Oldest-first copy of what is queued right now; nothing is removed. */
    public List<ChatLine> snapshot() {
        return new ArrayList<>(queue);
    }

    /** Removes the lines of an earlier snapshot (lines that were dropped meanwhile are simply gone already). */
    public void remove(List<ChatLine> sent) {
        for (ChatLine line : sent) {
            if (queue.remove(line)) size.decrementAndGet();
        }
    }

    /** Drops everything one player wrote (used when the player mutes the bot). */
    public void removeFrom(String player) {
        for (var it = queue.iterator(); it.hasNext(); ) {
            if (it.next().player().equals(player)) {
                it.remove();
                size.decrementAndGet();
            }
        }
    }

    public void clear() {
        queue.clear();
        size.set(0);
    }

    public int size() {
        return Math.max(0, size.get());
    }
}
