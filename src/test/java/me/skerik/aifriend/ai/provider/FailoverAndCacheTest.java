package me.skerik.aifriend.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.skerik.aifriend.ai.ApiException;
import me.skerik.aifriend.ai.provider.ChatMessage.Role;
import org.junit.jupiter.api.Test;

class FailoverAndCacheTest {

    /** Mutable clock. */
    static final class TestClock extends Clock {
        private volatile long millis = 1_000_000;

        void advance(Duration d) {
            millis += d.toMillis();
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    static final class Fake implements AiProvider {
        final String id;
        final List<String> log;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean fail;

        Fake(String id, List<String> log) {
            this.id = id;
            this.log = log;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ProviderResult complete(ProviderRequest request) throws IOException {
            calls.incrementAndGet();
            log.add(id);
            if (fail) throw new ApiException(500, "HTTP 500 from " + id);
            return new ProviderResult("from " + id + ":" + request.messages().getLast().content(), 10, 5, id);
        }
    }

    private static ProviderRequest req(String text) {
        return new ProviderRequest(List.of(new ChatMessage(Role.USER, text)), 50, false);
    }

    // --- failover ---

    @Test
    void usesFirstProviderWhenHealthy() throws Exception {
        List<String> log = new ArrayList<>();
        var f = new FailoverProvider(List.of(new Fake("a", log), new Fake("b", log)));
        assertEquals("from a:x", f.complete(req("x")).text());
        assertEquals(List.of("a"), log);
    }

    @Test
    void fallsThroughInOrderAndRethrowsLastWhenAllFail() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("a", log), b = new Fake("b", log), c = new Fake("c", log);
        a.fail = b.fail = c.fail = true;
        var f = new FailoverProvider(List.of(a, b, c));
        ApiException e = assertThrows(ApiException.class, () -> f.complete(req("x")));
        assertEquals(List.of("a", "b", "c"), log);
        assertEquals("HTTP 500 from c", e.getMessage());
        assertEquals(1, e.getSuppressed().length, "earlier failures are attached (chained)");
        assertEquals(1, e.getSuppressed()[0].getSuppressed().length);
    }

    @Test
    void failedProviderCoolsDownThenIsRetried() throws Exception {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("a", log), b = new Fake("b", log);
        a.fail = true;
        TestClock clock = new TestClock();
        var f = new FailoverProvider(List.of(a, b), Duration.ofSeconds(60), clock);

        assertEquals("from b:1", f.complete(req("1")).text()); // a fails, b answers
        assertEquals("from b:2", f.complete(req("2")).text()); // a skipped
        assertEquals("from b:3", f.complete(req("3")).text());
        assertEquals(1, a.calls.get(), "dead provider is not retried during cooldown");

        clock.advance(Duration.ofSeconds(61));
        a.fail = false;
        assertEquals("from a:4", f.complete(req("4")).text()); // retried and recovered
        assertEquals("from a:5", f.complete(req("5")).text());
        assertEquals(List.of("a", "b", "b", "b", "a", "a"), log);
    }

    @Test
    void stillFailingAfterCooldownGoesBackOnCooldown() throws Exception {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("a", log), b = new Fake("b", log);
        a.fail = true;
        TestClock clock = new TestClock();
        var f = new FailoverProvider(List.of(a, b), Duration.ofSeconds(10), clock);
        f.complete(req("1"));
        clock.advance(Duration.ofSeconds(11));
        f.complete(req("2"));
        f.complete(req("3"));
        assertEquals(2, a.calls.get());
    }

    @Test
    void allCoolingDownStillTriesEverything() throws Exception {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("a", log), b = new Fake("b", log);
        a.fail = b.fail = true;
        var f = new FailoverProvider(List.of(a, b), Duration.ofSeconds(60), new TestClock());
        assertThrows(IOException.class, () -> f.complete(req("1")));
        a.fail = false;
        assertEquals("from a:2", f.complete(req("2")).text());
    }

    @Test
    void interruptionIsNotSwallowed() {
        AiProvider interrupted = new AiProvider() {
            @Override
            public String id() {
                return "i";
            }

            @Override
            public ProviderResult complete(ProviderRequest r) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        var f = new FailoverProvider(List.of(interrupted, new Fake("b", new ArrayList<>())));
        assertThrows(InterruptedException.class, () -> f.complete(req("x")));
    }

    // --- cache ---

    @Test
    void hitReturnsStoredTextWithZeroTokens() throws Exception {
        Fake d = new Fake("d", new ArrayList<>());
        var c = new CachingProvider(d, 10, Duration.ofMinutes(5), new TestClock());
        ProviderResult first = c.complete(req("x"));
        ProviderResult second = c.complete(req("x"));
        assertEquals(1, d.calls.get());
        assertEquals(10, first.inputTokens());
        assertEquals(first.text(), second.text());
        assertEquals(0, second.inputTokens());
        assertEquals(0, second.outputTokens());
        assertEquals("d", second.model());
        c.complete(req("y"));
        assertEquals(2, d.calls.get());
    }

    @Test
    void anyRequestDifferenceIsAMiss() throws Exception {
        Fake d = new Fake("d", new ArrayList<>());
        var c = new CachingProvider(d, 10, Duration.ofMinutes(5));
        c.complete(new ProviderRequest(List.of(new ChatMessage(Role.USER, "x")), 50, false));
        c.complete(new ProviderRequest(List.of(new ChatMessage(Role.USER, "x")), 51, false));
        c.complete(new ProviderRequest(List.of(new ChatMessage(Role.USER, "x")), 50, true));
        c.complete(new ProviderRequest(List.of(new ChatMessage(Role.SYSTEM, "x")), 50, false));
        c.complete(new ProviderRequest(List.of(new ChatMessage(Role.USER, "x"), new ChatMessage(Role.USER, "")), 50, false));
        assertEquals(5, d.calls.get());
    }

    @Test
    void entriesExpireAfterTtl() throws Exception {
        Fake d = new Fake("d", new ArrayList<>());
        TestClock clock = new TestClock();
        var c = new CachingProvider(d, 10, Duration.ofMinutes(10), clock);
        c.complete(req("x"));
        clock.advance(Duration.ofMinutes(9));
        c.complete(req("x"));
        assertEquals(1, d.calls.get());
        clock.advance(Duration.ofMinutes(2));
        c.complete(req("x"));
        assertEquals(2, d.calls.get());
    }

    @Test
    void lruEvictsLeastRecentlyUsed() throws Exception {
        Fake d = new Fake("d", new ArrayList<>());
        var c = new CachingProvider(d, 2, Duration.ofMinutes(10), new TestClock());
        c.complete(req("a"));
        c.complete(req("b"));
        c.complete(req("a")); // refresh a, b is now eldest
        c.complete(req("c")); // evicts b
        assertEquals(2, c.size());
        assertEquals(3, d.calls.get());
        c.complete(req("a"));
        assertEquals(3, d.calls.get(), "a survived");
        c.complete(req("b"));
        assertEquals(4, d.calls.get(), "b was evicted");
    }

    @Test
    void failuresAreNotCached() throws Exception {
        Fake d = new Fake("d", new ArrayList<>());
        d.fail = true;
        var c = new CachingProvider(d, 10, Duration.ofMinutes(10));
        assertThrows(IOException.class, () -> c.complete(req("x")));
        d.fail = false;
        assertEquals("from d:x", c.complete(req("x")).text());
        assertEquals(2, d.calls.get());
    }

    @Test
    void threadSafeAndBounded() throws Exception {
        Fake safe = new Fake("d", Collections.synchronizedList(new ArrayList<>()));
        var c = new CachingProvider(safe, 50, Duration.ofMinutes(10));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                fs.add(pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        ProviderResult r = c.complete(req("k" + (i % 100)));
                        assertEquals("from d:k" + (i % 100), r.text());
                    }
                    return null;
                }));
            }
            for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(c.size() <= 50);
    }
}
