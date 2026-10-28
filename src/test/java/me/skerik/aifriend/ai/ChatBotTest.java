package me.skerik.aifriend.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.Scheduler;
import me.skerik.aifriend.TestSettings;
import me.skerik.aifriend.config.Settings;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatBotTest {

    private static final String SAY = "{\"action\":\"say\",\"text\":\"Hello\"}";
    private static final String SLEEP = "{\"action\":\"sleep\"}";

    /** Counts timers; every started timer can be cancelled once. */
    private static final class FakeScheduler implements Scheduler {
        int started;
        int cancelled;
        long lastDelay;

        int active() {
            return started - cancelled;
        }

        @Override
        public void runGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void runForEntity(Entity entity, Runnable task, @Nullable Runnable retired) {
            task.run();
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public void runLater(Runnable task, long ticks) {
            task.run();
        }

        @Override
        public Runnable runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
            return runAsyncTimer(task, delayTicks, periodTicks);
        }

        @Override
        public Runnable runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
            started++;
            lastDelay = delayTicks;
            boolean[] done = new boolean[1];
            return () -> {
                if (!done[0]) {
                    done[0] = true;
                    cancelled++;
                }
            };
        }
    }

    private final FakeScheduler scheduler = new FakeScheduler();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final AtomicInteger online = new AtomicInteger(2);
    private final List<String> said = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();
    private Transport next = p -> SAY;
    private Settings settings;
    private ChatBot bot;
    private final Logger log = Logger.getLogger("ChatBotTest");

    @BeforeEach
    void setUp() {
        log.setLevel(Level.OFF);
        settings = TestSettings.defaults().build();
        bot = newBot(settings);
    }

    private ChatBot newBot(Settings s) {
        return new ChatBot(log, scheduler, x -> p -> {
            prompts.add(p);
            return next.send(p);
        }, (name, text) -> said.add(name + "|" + text), online::get, clock::get, s);
    }

    private void chat(String player, String text) {
        bot.onChat(player, text);
    }

    private ChatBot.State state() {
        return bot.status().state();
    }

    @Test
    void tickDoesNothingWithoutKey() {
        TestSettings t = TestSettings.defaults();
        t.apiKey = Settings.KEY_PLACEHOLDER;
        bot = newBot(t.build());
        chat("a", "hello");
        bot.tick();
        assertTrue(prompts.isEmpty());
        assertEquals(ChatBot.State.NO_KEY, state());
        assertEquals(0, bot.status().queued()); // lines are not even kept
    }

    @Test
    void tickDoesNothingWhenDisabled() {
        TestSettings t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        chat("a", "hello");
        bot.tick();
        assertTrue(prompts.isEmpty());
        assertEquals(ChatBot.State.DISABLED, state());
    }

    @Test
    void tickDoesNothingWithoutPlayersOrMessages() {
        bot.tick(); // empty buffer
        chat("a", "hello");
        online.set(0);
        bot.tick(); // nobody online
        assertTrue(prompts.isEmpty());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void successfulTickBroadcastsAndRemovesSentLines() {
        chat("a", "one");
        chat("b", "two");
        bot.tick();
        assertEquals(List.of("AI Friend|Hello"), said);
        assertEquals(0, bot.status().queued());
        assertEquals(1, prompts.size());

        chat("a", "three");
        bot.tick();
        assertTrue(prompts.get(1).contains("your_previous_message"));
    }

    @Test
    void messagesArrivingWhileInFlightAreKept() {
        chat("a", "one");
        next = p -> {
            chat("b", "late");
            return SAY;
        };
        bot.tick();
        assertEquals(1, bot.status().queued());
        next = p -> SAY;
        bot.tick();
        assertTrue(prompts.get(1).contains("late"));
        assertEquals(0, bot.status().queued());
    }

    @Test
    void failureKeepsLinesAndBacksOff() {
        chat("a", "one");
        next = p -> {
            throw new IOException("down");
        };
        bot.tick();

        ChatBot.Status st = bot.status();
        assertEquals(ChatBot.State.BACKOFF, st.state());
        assertEquals(60, st.waitSeconds());
        assertEquals(1, st.failures());
        assertEquals(1, st.queued());
        assertTrue(said.isEmpty());

        clock.addAndGet(59_999);
        bot.tick();
        assertEquals(1, prompts.size()); // still backing off: transport not called

        clock.addAndGet(1);
        bot.tick();
        assertEquals(2, prompts.size());
        assertEquals(120, bot.status().waitSeconds());
        assertEquals(2, bot.status().failures());
    }

    @Test
    void backoffGrowsAndIsCapped() {
        assertEquals(60, ChatBot.backoffSeconds(0));
        assertEquals(60, ChatBot.backoffSeconds(1));
        assertEquals(120, ChatBot.backoffSeconds(2));
        assertEquals(240, ChatBot.backoffSeconds(3));
        assertEquals(480, ChatBot.backoffSeconds(4));
        assertEquals(900, ChatBot.backoffSeconds(5));
        assertEquals(900, ChatBot.backoffSeconds(50));
        assertEquals(900, ChatBot.backoffSeconds(Integer.MAX_VALUE));
    }

    @Test
    void recoveryResetsFailures() {
        chat("a", "one");
        next = p -> {
            throw new IOException("down");
        };
        bot.tick();
        next = p -> SAY;
        clock.addAndGet(60_000);
        bot.tick();
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().failures());
        assertEquals(List.of("AI Friend|Hello"), said);
        assertEquals(0, bot.status().queued());
    }

    @Test
    void transportErrorsAreContained() {
        chat("a", "one");
        List<Transport> failing = List.of(
                p -> {
                    throw new IOException("io");
                },
                p -> {
                    throw new ApiException(401, "Unauthorized");
                },
                p -> {
                    throw new IllegalStateException("bug");
                },
                p -> "{\"error\":\"nope\"}");
        for (Transport t : failing) {
            next = t;
            assertDoesNotThrow(bot::tick);
            assertEquals(ChatBot.State.BACKOFF, state());
            clock.addAndGet(15 * 60_000);
        }
        assertEquals(4, bot.status().failures());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void sleepIsHonoredWithTwoDistinctPlayers() {
        chat("a", "go away");
        chat("b", "bot be quiet");
        next = p -> SLEEP;
        bot.tick();
        ChatBot.Status st = bot.status();
        assertEquals(ChatBot.State.SLEEPING, st.state());
        assertEquals(30 * 60, st.waitSeconds());
        assertEquals(0, st.queued());
        assertTrue(said.isEmpty());

        chat("a", "still here"); // ignored while asleep
        assertEquals(0, bot.status().queued());
        bot.tick();
        assertEquals(1, prompts.size());

        clock.addAndGet(30 * 60_000L);
        assertEquals(ChatBot.State.RUNNING, state());
        chat("a", "back");
        assertEquals(1, bot.status().queued());
    }

    @Test
    void sleepFromOnePlayerIsIgnoredWhenTwoAreOnline() {
        chat("a", "go away");
        chat("a", "really");
        next = p -> SLEEP;
        bot.tick();
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void sleepFromOnePlayerCountsWhenOnlyOneIsOnline() {
        online.set(1);
        chat("a", "go away");
        next = p -> SLEEP;
        bot.tick();
        assertEquals(ChatBot.State.SLEEPING, state());
    }

    @Test
    void sleepIsIgnoredWhenStopWhenIgnoredIsOff() {
        TestSettings t = TestSettings.defaults();
        t.stopWhenIgnored = false;
        bot = newBot(t.build());
        chat("a", "go away");
        chat("b", "quiet");
        next = p -> SLEEP;
        bot.tick();
        assertEquals(ChatBot.State.RUNNING, state());
    }

    @Test
    void onChatIgnoredWithoutKeyOrWhenDisabled() {
        TestSettings t = TestSettings.defaults();
        t.apiKey = "";
        bot = newBot(t.build());
        chat("a", "x");
        assertEquals(0, bot.status().queued());
        t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        chat("a", "x");
        assertEquals(0, bot.status().queued());
    }

    @Test
    void reloadWakesUpClearsFailuresAndRestartsTimer() {
        chat("a", "one");
        chat("b", "two");
        next = p -> SLEEP;
        bot.tick();
        assertEquals(ChatBot.State.SLEEPING, state());

        TestSettings t = TestSettings.defaults();
        t.intervalSeconds = 10;
        bot.start();
        bot.reload(t.build());
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().failures());
        assertEquals(200, scheduler.lastDelay);
        assertEquals(1, scheduler.active());

        chat("a", "one");
        next = p -> {
            throw new IOException("x");
        };
        bot.tick();
        assertEquals(ChatBot.State.BACKOFF, state());
        bot.reload(t.build());
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().failures());
        assertEquals(1, scheduler.active());
    }

    @Test
    void reloadAppliesNewQueueCapacity() {
        for (int i = 0; i < 20; i++) chat("a", "m" + i);
        TestSettings t = TestSettings.defaults();
        t.maxQueuedMessages = 5;
        bot.reload(t.build());
        assertEquals(5, bot.status().queued());
    }

    @Test
    void startNeverDuplicatesTimersAndStopIsIdempotent() {
        bot.start();
        bot.start();
        assertEquals(2, scheduler.started);
        assertEquals(1, scheduler.active());
        assertEquals(60 * 20, scheduler.lastDelay);
        bot.stop();
        bot.stop();
        assertEquals(0, scheduler.active());
        assertEquals(2, scheduler.cancelled);
        new ChatBot(log, scheduler, x -> p -> SAY, (n, t) -> { }, online::get, clock::get, settings).stop();
    }
}
