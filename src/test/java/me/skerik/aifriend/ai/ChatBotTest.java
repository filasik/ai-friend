package me.skerik.aifriend.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.Scheduler;
import me.skerik.aifriend.TestSettings;
import me.skerik.aifriend.ai.provider.AiProvider;
import me.skerik.aifriend.ai.provider.ChatMessage;
import me.skerik.aifriend.ai.provider.ProviderRequest;
import me.skerik.aifriend.ai.provider.ProviderResult;
import me.skerik.aifriend.chat.ConversationMemory;
import me.skerik.aifriend.chat.CostTracker;
import me.skerik.aifriend.chat.EventPrompts;
import me.skerik.aifriend.chat.EventPrompts.GameEvent;
import me.skerik.aifriend.chat.MentionDetector;
import me.skerik.aifriend.chat.OptOutStore;
import me.skerik.aifriend.chat.Persona;
import me.skerik.aifriend.chat.PersonaSelector;
import me.skerik.aifriend.chat.RateLimiter;
import me.skerik.aifriend.chat.ReplyFilter;
import me.skerik.aifriend.chat.SimulationResult;
import me.skerik.aifriend.chat.SimulationResult.Blocker;
import me.skerik.aifriend.config.Settings;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatBotTest {

    private static final String SAY = "{\"action\":\"say\",\"text\":\"Hello\"}";
    private static final String SLEEP = "{\"action\":\"sleep\"}";
    private static final long START = Instant.parse("2026-06-15T12:30:00Z").toEpochMilli();

    /** Counts timers; async tasks run immediately unless {@link #defer} is set. */
    private static final class FakeScheduler implements Scheduler {
        int started;
        int cancelled;
        long lastDelay;
        boolean defer;
        final List<Runnable> deferred = new ArrayList<>();

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
            if (defer) deferred.add(task);
            else task.run();
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

    @FunctionalInterface
    private interface Answer {
        ProviderResult apply(ProviderRequest request) throws IOException;
    }

    private record Told(UUID player, String key, Map<String, String> placeholders) {
    }

    private record ToldPermission(String permission, String key, Map<String, String> placeholders) {
    }

    @TempDir
    Path tmp;

    private final FakeScheduler scheduler = new FakeScheduler();
    private final AtomicLong clock = new AtomicLong(START);
    private final AtomicInteger online = new AtomicInteger(2);
    private final List<String> said = new ArrayList<>();
    private final List<ProviderRequest> requests = new ArrayList<>();
    private final List<Told> told = new ArrayList<>();
    private final List<ToldPermission> toldPermission = new ArrayList<>();
    private Answer next = r -> ok(SAY);
    private Settings settings;
    private OptOutStore optOut;
    private ChatBot bot;
    private final Logger log = Logger.getLogger("ChatBotTest");

    private final AiProvider provider = new AiProvider() {
        @Override
        public String id() {
            return "fake";
        }

        @Override
        public ProviderResult complete(ProviderRequest request) throws IOException {
            requests.add(request);
            return next.apply(request);
        }
    };

    @BeforeEach
    void setUp() {
        log.setLevel(Level.OFF);
        optOut = new OptOutStore(tmp.resolve("muted.yml"), log);
        settings = TestSettings.defaults().mentions(mentions(false)).build(); // /ai needs mentions.enabled; direct-answer stays off
        bot = newBot(settings);
    }

    private static ProviderResult ok(String text) {
        return new ProviderResult(text, 0, 0, "fake-model");
    }

    private static ProviderResult usage(String text, int in, int out) {
        return new ProviderResult(text, in, out, "fake-model");
    }

    private ChatBot newBot(Settings s) {
        return new ChatBot(log, scheduler, x -> x.hasApiKey() ? Optional.of(provider) : Optional.empty(),
                (name, text) -> said.add(name + "|" + text),
                new ChatBot.Notifier() {
                    @Override
                    public void tell(UUID player, String key, Map<String, String> placeholders) {
                        told.add(new Told(player, key, placeholders));
                    }

                    @Override
                    public void tellPermission(String permission, String key, Map<String, String> placeholders) {
                        toldPermission.add(new ToldPermission(permission, key, placeholders));
                    }
                },
                () -> new ChatBot.WorldView("world", 1000), online::get, clock::get, optOut, s);
    }

    private static UUID id(String player) {
        return UUID.nameUUIDFromBytes(player.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void chat(String player, String text) {
        bot.onChat(id(player), player, text, true, false);
    }

    private ChatBot.State state() {
        return bot.status().state();
    }

    private static String userOf(ProviderRequest r) {
        ChatMessage last = r.messages().getLast();
        assertEquals(ChatMessage.Role.USER, last.role());
        return last.content();
    }

    private static String systemOf(ProviderRequest r) {
        ChatMessage first = r.messages().getFirst();
        assertEquals(ChatMessage.Role.SYSTEM, first.role());
        return first.content();
    }

    private ProviderRequest lastRequest() {
        return requests.getLast();
    }

    /** The JSON objects of the data block of a user message. */
    private static List<JsonObject> data(String user) {
        List<String> all = user.lines().toList();
        int open = all.indexOf(PromptBuilder.LOG_OPEN);
        int close = all.indexOf(PromptBuilder.LOG_CLOSE);
        assertTrue(open >= 0 && close > open, user);
        return all.subList(open + 1, close).stream().map(l -> JsonParser.parseString(l).getAsJsonObject()).toList();
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    private SimulationResult simulate(boolean callProvider) {
        AtomicReference<SimulationResult> ref = new AtomicReference<>();
        bot.simulate(callProvider, ref::set);
        assertNotNull(ref.get(), "simulate result not delivered");
        return ref.get();
    }

    private static PersonaSelector.Config persona(String prompt) {
        return new PersonaSelector.Config(true,
                List.of(new Persona("Pirate", prompt, Set.of(), Persona.TimeOfDay.ANY, 1)));
    }

    private static ConversationMemory.Config memory() {
        return new ConversationMemory.Config(true, 5, 2000);
    }

    private static RateLimiter.Config limits(int cooldown, int playerCalls, long playerTokens, int globalCalls, long globalTokens) {
        return new RateLimiter.Config(true, cooldown, playerCalls, playerTokens, globalCalls, globalTokens);
    }

    private static ReplyFilter.Config filter(ReplyFilter.BannedMode mode) {
        return new ReplyFilter.Config(true, 256, List.of("badword"), mode, false);
    }

    private static MentionDetector.Config mentions(boolean direct) {
        return new MentionDetector.Config(true, direct, List.of(), false);
    }

    private static EventPrompts.Config events(int cooldown) {
        return new EventPrompts.Config(true, cooldown, true, true, true, true, false, 6);
    }

    private static OptOutStore.Config optOutOn() {
        return new OptOutStore.Config(true, "muted.yml");
    }

    // ---- basic loop (unchanged behaviour) ----

    @Test
    void tickDoesNothingWithoutKey() {
        TestSettings t = TestSettings.defaults();
        t.apiKey = Settings.KEY_PLACEHOLDER;
        bot = newBot(t.build());
        chat("a", "hello");
        bot.tick();
        assertTrue(requests.isEmpty());
        assertEquals(ChatBot.State.NO_KEY, state());
        assertEquals("-", bot.status().provider());
        assertEquals(0, bot.status().queued()); // lines are not even kept
    }

    @Test
    void tickDoesNothingWhenDisabled() {
        TestSettings t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        chat("a", "hello");
        bot.tick();
        assertTrue(requests.isEmpty());
        assertEquals(ChatBot.State.DISABLED, state());
    }

    @Test
    void tickDoesNothingWithoutPlayersOrMessages() {
        bot.tick(); // empty buffer
        chat("a", "hello");
        online.set(0);
        bot.tick(); // nobody online
        assertTrue(requests.isEmpty());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void successfulTickBroadcastsAndRemovesSentLines() {
        chat("a", "one");
        chat("b", "two");
        bot.tick();
        assertEquals(List.of("AI Friend|Hello"), said);
        assertEquals(0, bot.status().queued());
        assertEquals(1, requests.size());
        assertEquals("fake", bot.status().provider());

        chat("a", "three");
        bot.tick();
        // memory is off: the previous message travels as context inside the user message
        assertTrue(userOf(requests.get(1)).contains("your_previous_message"));
        assertEquals(2, requests.get(1).messages().size());
    }

    @Test
    void requestUsesTheConfiguredOutputTokensAndAsksForJson() {
        chat("a", "one");
        bot.tick();
        assertEquals(settings.provider().maxOutputTokens(), lastRequest().maxOutputTokens());
        assertTrue(lastRequest().jsonReply());
    }

    @Test
    void messagesArrivingWhileInFlightAreKept() {
        chat("a", "one");
        next = r -> {
            chat("b", "late");
            return ok(SAY);
        };
        bot.tick();
        assertEquals(1, bot.status().queued());
        next = r -> ok(SAY);
        bot.tick();
        assertTrue(userOf(requests.get(1)).contains("late"));
        assertEquals(0, bot.status().queued());
    }

    @Test
    void failureKeepsLinesAndBacksOff() {
        chat("a", "one");
        next = r -> {
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
        assertEquals(1, requests.size()); // still backing off: provider not called

        clock.addAndGet(1);
        bot.tick();
        assertEquals(2, requests.size());
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
        next = r -> {
            throw new IOException("down");
        };
        bot.tick();
        next = r -> ok(SAY);
        clock.addAndGet(60_000);
        bot.tick();
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().failures());
        assertEquals(List.of("AI Friend|Hello"), said);
        assertEquals(0, bot.status().queued());
    }

    @Test
    void providerErrorsAreContained() {
        chat("a", "one");
        List<Answer> failing = List.of(
                r -> {
                    throw new IOException("io");
                },
                r -> {
                    throw new ApiException(401, "Unauthorized");
                },
                r -> {
                    throw new IllegalStateException("bug");
                },
                r -> ok("{\"error\":\"nope\"}"));
        for (Answer a : failing) {
            next = a;
            assertDoesNotThrow(bot::tick);
            assertEquals(ChatBot.State.BACKOFF, state());
            clock.addAndGet(15 * 60_000);
        }
        assertEquals(4, bot.status().failures());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void tickNeverThrowsEvenIfTheBroadcasterDoes() {
        ChatBot throwing = new ChatBot(log, scheduler, x -> Optional.of(provider), (n, t) -> {
            throw new IllegalStateException("chat broke");
        }, new ChatBot.Notifier() {
            @Override
            public void tell(UUID player, String key, Map<String, String> placeholders) {
            }

            @Override
            public void tellPermission(String permission, String key, Map<String, String> placeholders) {
            }
        }, () -> new ChatBot.WorldView("world", 0), online::get, clock::get, optOut, settings);
        throwing.onChat(id("a"), "a", "hi", true, false);
        assertDoesNotThrow(throwing::tick);
        assertDoesNotThrow(throwing::tick);
    }

    @Test
    void sleepIsHonoredWithTwoDistinctPlayers() {
        chat("a", "go away");
        chat("b", "bot be quiet");
        next = r -> ok(SLEEP);
        bot.tick();
        ChatBot.Status st = bot.status();
        assertEquals(ChatBot.State.SLEEPING, st.state());
        assertEquals(30 * 60, st.waitSeconds());
        assertEquals(0, st.queued());
        assertTrue(said.isEmpty());

        chat("a", "still here"); // ignored while asleep
        assertEquals(0, bot.status().queued());
        bot.tick();
        assertEquals(1, requests.size());

        clock.addAndGet(30 * 60_000L);
        assertEquals(ChatBot.State.RUNNING, state());
        chat("a", "back");
        assertEquals(1, bot.status().queued());
    }

    @Test
    void sleepFromOnePlayerIsIgnoredWhenTwoAreOnline() {
        chat("a", "go away");
        chat("a", "really");
        next = r -> ok(SLEEP);
        bot.tick();
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void sleepFromOnePlayerCountsWhenOnlyOneIsOnline() {
        online.set(1);
        chat("a", "go away");
        next = r -> ok(SLEEP);
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
        next = r -> ok(SLEEP);
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
    void blankChatIsNotQueued() {
        chat("a", "   ");
        assertEquals(0, bot.status().queued());
    }

    @Test
    void reloadWakesUpClearsFailuresAndRestartsTimer() {
        chat("a", "one");
        chat("b", "two");
        next = r -> ok(SLEEP);
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
        next = r -> {
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
    void reloadRebuildsTheProviderFromTheNewSettings() {
        TestSettings t = TestSettings.defaults();
        t.apiKey = Settings.KEY_PLACEHOLDER;
        bot.reload(t.build());
        assertEquals(ChatBot.State.NO_KEY, state());
        bot.reload(TestSettings.defaults().build());
        assertEquals(ChatBot.State.RUNNING, state());
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
    }

    // ---- prompt: system / user split ----

    @Test
    void personaIsOnlyInTheSystemMessage() {
        TestSettings t = TestSettings.defaults().personas(persona("Talk like PIRATE-MARKER"));
        bot = newBot(t.build());
        chat("Steve", "PLAYER-TEXT-MARKER");
        bot.tick();

        ProviderRequest r = lastRequest();
        assertEquals(2, r.messages().size());
        assertTrue(systemOf(r).contains("PIRATE-MARKER"));
        assertFalse(systemOf(r).contains("PLAYER-TEXT-MARKER"));
        assertFalse(userOf(r).contains("PIRATE-MARKER"));
        assertTrue(userOf(r).contains("PLAYER-TEXT-MARKER"));
        assertEquals("1 configured", bot.status().persona());
    }

    @Test
    void withoutPersonasTheSystemMessageHasNoPersonality() {
        chat("Steve", "hi");
        bot.tick();
        assertFalse(systemOf(lastRequest()).contains("Your personality"));
        assertEquals("-", bot.status().persona());
    }

    // ---- memory ----

    @Test
    void afterAnAnswerTheNextRequestContainsHistory() {
        bot = newBot(TestSettings.defaults().memory(memory()).build());
        chat("Steve", "first question");
        bot.tick();
        String firstUser = userOf(requests.get(0));
        assertEquals(2, requests.get(0).messages().size()); // no history yet
        assertEquals(1, bot.status().memoryTurns());

        chat("Alex", "second question");
        bot.tick();
        List<ChatMessage> m = requests.get(1).messages();
        assertEquals(4, m.size());
        assertEquals(ChatMessage.Role.SYSTEM, m.get(0).role());
        assertEquals(ChatMessage.Role.USER, m.get(1).role());
        assertEquals(firstUser.replaceAll("\\s+", " ").strip(), m.get(1).content()); // the data block, as one line
        assertTrue(m.get(1).content().startsWith(PromptBuilder.LOG_OPEN));
        assertTrue(m.get(1).content().contains("first question"));
        assertEquals(new ChatMessage(ChatMessage.Role.ASSISTANT, "{\"action\":\"say\",\"text\":\"Hello\"}"), m.get(2)); // wire format
        assertEquals(ChatMessage.Role.USER, m.get(3).role());
        assertTrue(m.get(3).content().contains("second question"));
        assertFalse(m.get(3).content().contains("first question"));
        // the history replaces the "previous message" context line
        assertFalse(m.get(3).content().contains("your_previous_message"));
        assertEquals(2, bot.status().memoryTurns());
    }

    @Test
    void memoryStoresTheFilteredText() {
        bot = newBot(TestSettings.defaults().memory(memory()).replyFilter(filter(ReplyFilter.BannedMode.REPLACE)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"this is badword ok\"}");
        chat("Steve", "q1");
        bot.tick();
        assertEquals(List.of("AI Friend|this is *** ok"), said);

        chat("Steve", "q2");
        bot.tick();
        List<ChatMessage> m = requests.get(1).messages();
        assertEquals("{\"action\":\"say\",\"text\":\"this is *** ok\"}", m.get(2).content()); // stored in the wire format
        assertEquals(ChatMessage.Role.ASSISTANT, m.get(2).role());
        assertTrue(m.stream().noneMatch(x -> x.content().contains("badword")));
    }

    @Test
    void nothingIsRememberedWhenTheAnswerIsNotPosted() {
        bot = newBot(TestSettings.defaults().memory(memory()).replyFilter(filter(ReplyFilter.BannedMode.DROP)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"a badword here\"}");
        chat("Steve", "q1");
        bot.tick();
        assertTrue(said.isEmpty());
        assertEquals(0, bot.status().memoryTurns());
    }

    // ---- reply filter ----

    @Test
    void bannedWordsAreReplaced() {
        bot = newBot(TestSettings.defaults().replyFilter(filter(ReplyFilter.BannedMode.REPLACE)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"You BadWord, friend\"}");
        chat("Steve", "hi");
        bot.tick();
        assertEquals(List.of("AI Friend|You ***, friend"), said);
    }

    @Test
    void bannedWordsDropTheWholeAnswer() {
        bot = newBot(TestSettings.defaults().replyFilter(filter(ReplyFilter.BannedMode.DROP)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"You badword, friend\"}");
        chat("Steve", "hi");
        bot.tick();
        assertTrue(said.isEmpty());
        assertEquals(1, requests.size());
        assertEquals(ChatBot.State.RUNNING, state()); // not a failure
    }

    @Test
    void filterMaxLengthIsAppliedBeforePosting() {
        bot = newBot(TestSettings.defaults()
                .replyFilter(new ReplyFilter.Config(true, 20, List.of(), ReplyFilter.BannedMode.REPLACE, false)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"one two three four five six seven eight nine ten\"}");
        chat("Steve", "hi");
        bot.tick();
        assertEquals(1, said.size());
        String posted = said.getFirst().substring("AI Friend|".length());
        assertTrue(posted.length() <= 20, posted);
        assertTrue(posted.startsWith("one two"), posted);
    }

    @Test
    void globalMaxReplyLengthStillAppliesWithoutTheFilter() {
        TestSettings t = TestSettings.defaults();
        t.maxReplyLength = 20;
        bot = newBot(t.build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"one two three four five six seven eight nine ten\"}");
        chat("Steve", "hi");
        bot.tick();
        assertTrue(said.getFirst().substring("AI Friend|".length()).length() <= 20);
    }

    // ---- rate limiter ----

    @Test
    void serverWideLimitBlocksTheTimedAnswer() {
        bot = newBot(TestSettings.defaults().limits(limits(0, 0, 0, 1, 0)).build());
        chat("a", "one");
        bot.tick();
        assertEquals(1, requests.size());
        chat("a", "two");
        bot.tick();
        assertEquals(1, requests.size());
        assertEquals(1, said.size());
        assertEquals(1, bot.status().queued()); // kept for tomorrow
        assertEquals(ChatBot.State.RUNNING, state()); // a limit is not a failure
    }

    @Test
    void cooldownBlocksAskWithPlaceholders() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(30, 0, 0, 0, 0)).build());
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "first", false);
        assertEquals(1, said.size());
        bot.askCommand(steve, "Steve", "second", false);
        assertEquals(1, requests.size());
        assertEquals(1, said.size());
        Told t = told.getLast();
        assertEquals(steve, t.player());
        assertEquals("command.limit.cooldown", t.key());
        assertEquals("30", t.placeholders().get("seconds"));
        assertEquals("0", t.placeholders().get("hours"));
        assertEquals("1", t.placeholders().get("minutes"));

        clock.addAndGet(10_000);
        bot.askCommand(steve, "Steve", "third", false);
        assertEquals("20", told.getLast().placeholders().get("seconds"));

        clock.addAndGet(20_000);
        bot.askCommand(steve, "Steve", "fourth", false);
        assertEquals(2, said.size());
    }

    @Test
    void dailyCallLimitBlocksAsk() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(0, 1, 0, 0, 0)).build());
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "first", false);
        bot.askCommand(steve, "Steve", "second", false);
        Told t = told.getLast();
        assertEquals("command.limit.player-calls", t.key());
        long secs = Long.parseLong(t.placeholders().get("seconds"));
        assertTrue(secs > 0 && secs <= 24 * 3600 + 3600, "seconds until midnight: " + secs);
        assertEquals(Long.toString(secs / 3600), t.placeholders().get("hours"));
        assertTrue(t.placeholders().containsKey("minutes"));
        assertEquals(1, requests.size());

        // another player has his own allowance
        bot.askCommand(id("Alex"), "Alex", "hi", false);
        assertEquals(2, requests.size());
    }

    @Test
    void dailyTokenLimitBlocksAskAfterUsage() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(0, 0, 100, 0, 0)).build());
        next = r -> usage(SAY, 60, 40);
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "first", false);
        assertEquals(0, bot.remaining(steve).playerTokens());
        bot.askCommand(steve, "Steve", "second", false);
        assertEquals("command.limit.player-tokens", told.getLast().key());
        assertEquals(1, requests.size());
    }

    @Test
    void globalLimitsBlockAsk() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(0, 0, 0, 1, 0)).build());
        bot.askCommand(id("Steve"), "Steve", "first", false);
        bot.askCommand(id("Alex"), "Alex", "second", false);
        assertEquals("command.limit.global-calls", told.getLast().key());

        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(0, 0, 0, 0, 50)).build());
        next = r -> usage(SAY, 30, 30);
        bot.askCommand(id("Steve"), "Steve", "first", false);
        bot.askCommand(id("Alex"), "Alex", "second", false);
        assertEquals("command.limit.global-tokens", told.getLast().key());
    }

    @Test
    void bypassFlagSkipsTheLimits() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(60, 1, 0, 0, 0)).build());
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "first", false);
        bot.askCommand(steve, "Steve", "second", true);
        bot.askCommand(steve, "Steve", "third", true);
        assertEquals(3, requests.size());
        assertEquals(3, said.size());
        assertTrue(told.isEmpty());
    }

    @Test
    void limitsAndMemorySurviveAReloadWithUnchangedConfig() {
        TestSettings t = TestSettings.defaults().limits(limits(0, 0, 0, 1, 0)).memory(memory());
        bot = newBot(t.build());
        chat("a", "one");
        bot.tick();
        assertEquals(1, bot.status().memoryTurns());

        bot.reload(t.build());
        assertEquals(1, bot.status().memoryTurns());
        chat("a", "two");
        bot.tick();
        assertEquals(1, requests.size()); // the exhausted counter was kept

        bot.reload(TestSettings.defaults().limits(limits(0, 0, 0, 5, 0)).memory(memory()).build());
        bot.tick();
        assertEquals(2, requests.size());
    }

    // ---- mentions ----

    @Test
    void mentionInChatAnswersImmediatelyAndIsNotDuplicated() {
        bot = newBot(TestSettings.defaults().mentions(mentions(true)).build());
        chat("Steve", "@AI Friend how are you");
        assertEquals(1, requests.size());
        assertEquals(List.of("AI Friend|Hello"), said);
        List<JsonObject> lines = data(userOf(lastRequest()));
        assertEquals(1, lines.size());
        assertEquals("Steve", lines.getFirst().get("player").getAsString());
        assertEquals("@AI Friend how are you", lines.getFirst().get("text").getAsString());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void mentionIncludesOtherQueuedLines() {
        bot = newBot(TestSettings.defaults().mentions(mentions(true)).build());
        chat("Alex", "building a farm");
        chat("Steve", "@AI Friend what do you think");
        assertEquals(1, requests.size());
        assertEquals(2, data(userOf(lastRequest())).size());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void mentionDoesNothingSpecialWhenFeatureOrDirectAnswerIsOff() {
        chat("Steve", "@AI Friend hello"); // mentions.enabled = false
        assertTrue(requests.isEmpty());
        assertEquals(1, bot.status().queued());

        bot = newBot(TestSettings.defaults().mentions(mentions(false)).build());
        chat("Steve", "@AI Friend hello");
        assertTrue(requests.isEmpty());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void mentionNeedsTheAskPermission() {
        bot = newBot(TestSettings.defaults().mentions(mentions(true)).build());
        bot.onChat(id("Steve"), "Steve", "@AI Friend hello", false, false);
        assertTrue(requests.isEmpty());
        assertEquals(1, bot.status().queued()); // still ordinary chat
    }

    @Test
    void mentionWhileSleepingStillAnswersOnceWithTheLine() {
        bot = newBot(TestSettings.defaults().mentions(mentions(true)).build());
        bot.sleep(Duration.ofMinutes(30));
        chat("Steve", "@AI Friend are you there");
        assertEquals(1, requests.size());
        assertEquals(1, data(userOf(lastRequest())).size());
        assertEquals(List.of("AI Friend|Hello"), said);
        assertEquals(ChatBot.State.SLEEPING, state());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void mentionIsRateLimitedUnlessBypassed() {
        bot = newBot(TestSettings.defaults().mentions(mentions(true)).limits(limits(30, 0, 0, 0, 0)).build());
        chat("Steve", "@AI Friend one");
        chat("Steve", "@AI Friend two");
        assertEquals(1, requests.size());
        assertEquals("command.limit.cooldown", told.getLast().key());
        bot.onChat(id("Steve"), "Steve", "@AI Friend three", true, true);
        assertEquals(2, requests.size());
    }

    // ---- opt-out ----

    @Test
    void mutedPlayersAreNeverBufferedNorAsked() {
        bot = newBot(TestSettings.defaults().optOut(optOutOn()).mentions(mentions(true)).build());
        UUID steve = id("Steve");
        assertEquals(ChatBot.MuteResult.DONE, bot.mute(steve, "Steve"));
        chat("Steve", "hello");
        chat("Steve", "@AI Friend hello");
        assertEquals(0, bot.status().queued());
        assertTrue(requests.isEmpty());
        assertEquals(1, bot.status().mutedPlayers());

        bot.askCommand(steve, "Steve", "question", false);
        assertEquals("command.ask.muted", told.getLast().key());
        assertTrue(requests.isEmpty());

        chat("Alex", "hi");
        assertEquals(1, bot.status().queued());
    }

    @Test
    void muteRemovesBufferedLinesAndSchedulesTheFlush() throws Exception {
        bot = newBot(TestSettings.defaults().optOut(optOutOn()).build());
        chat("Steve", "one");
        chat("Alex", "two");
        scheduler.defer = true;
        assertEquals(ChatBot.MuteResult.DONE, bot.mute(id("Steve"), "Steve"));
        assertEquals(1, bot.status().queued());
        assertEquals(1, scheduler.deferred.size());
        assertFalse(Files.exists(tmp.resolve("muted.yml")));

        scheduler.deferred.forEach(Runnable::run);
        assertTrue(Files.readString(tmp.resolve("muted.yml")).contains(id("Steve").toString()));

        bot.tick();
        assertFalse(userOf(lastRequest()).contains("Steve"));
        assertTrue(userOf(lastRequest()).contains("Alex"));
    }

    @Test
    void muteAndUnmuteResults() {
        assertEquals(ChatBot.MuteResult.DISABLED, bot.mute(id("Steve"), "Steve"));
        assertEquals(ChatBot.MuteResult.DISABLED, bot.unmute(id("Steve")));

        bot = newBot(TestSettings.defaults().optOut(optOutOn()).build());
        UUID steve = id("Steve");
        assertEquals(ChatBot.MuteResult.ALREADY, bot.unmute(steve));
        assertEquals(ChatBot.MuteResult.DONE, bot.mute(steve, "Steve"));
        assertEquals(ChatBot.MuteResult.ALREADY, bot.mute(steve, "Steve"));
        assertEquals(ChatBot.MuteResult.DONE, bot.unmute(steve));
        chat("Steve", "back again");
        assertEquals(1, bot.status().queued());
    }

    // ---- events ----

    private static List<String> eventTexts(String user) {
        return data(user).stream().filter(o -> o.has("event")).map(o -> o.get("event").getAsString()).toList();
    }

    @Test
    void acceptedEventIsAnEventLineInTheUserMessage() {
        bot = newBot(TestSettings.defaults().events(events(30)).build());
        bot.onEvent(GameEvent.death("Steve", "Steve fell from a high place"));
        assertEquals(1, requests.size());
        List<String> ev = eventTexts(userOf(lastRequest()));
        assertEquals(List.of("[event] Steve died: Steve fell from a high place."), ev);
        assertFalse(systemOf(lastRequest()).contains("Steve fell"));
        assertEquals(List.of("AI Friend|Hello"), said);

        // answered events are not sent again
        chat("a", "hello");
        bot.tick();
        assertTrue(eventTexts(userOf(lastRequest())).isEmpty());
    }

    @Test
    void eventsRespectTheCooldown() {
        bot = newBot(TestSettings.defaults().events(events(30)).build());
        bot.onEvent(GameEvent.firstJoin("Steve"));
        bot.onEvent(GameEvent.firstJoin("Alex"));
        assertEquals(1, requests.size());
        clock.addAndGet(29_999);
        bot.onEvent(GameEvent.firstJoin("Alex"));
        assertEquals(1, requests.size());
        clock.addAndGet(1);
        bot.onEvent(GameEvent.firstJoin("Alex"));
        assertEquals(2, requests.size());
        assertEquals(List.of("[event] Alex joined the server for the first time."), eventTexts(userOf(lastRequest())));
    }

    @Test
    void eventsAreIgnoredWhenOffAsleepOrSwitchedOffByType() {
        bot.onEvent(GameEvent.death("Steve", "died")); // events.enabled = false
        assertTrue(requests.isEmpty());

        bot = newBot(TestSettings.defaults().events(events(0)).build());
        bot.sleep(Duration.ofMinutes(10));
        bot.onEvent(GameEvent.death("Steve", "died"));
        assertTrue(requests.isEmpty());
        bot.wake();
        bot.onEvent(GameEvent.explosion("creeper", 3)); // explosions are off by default
        assertTrue(requests.isEmpty());
        bot.onEvent(GameEvent.death("Steve", "died"));
        assertEquals(1, requests.size());
    }

    @Test
    void eventLinesAreCappedAndKeptAcrossAFailure() {
        bot = newBot(TestSettings.defaults().events(events(0)).build());
        next = r -> {
            throw new IOException("down");
        };
        for (int i = 1; i <= 7; i++) bot.onEvent(GameEvent.firstJoin("P" + i));
        assertEquals(1, requests.size()); // backing off after the first one
        assertEquals(ChatBot.State.BACKOFF, state());

        next = r -> ok(SAY);
        clock.addAndGet(60_000);
        bot.tick();
        assertEquals(List.of(
                "[event] P3 joined the server for the first time.", "[event] P4 joined the server for the first time.",
                "[event] P5 joined the server for the first time.", "[event] P6 joined the server for the first time.",
                "[event] P7 joined the server for the first time."), eventTexts(userOf(lastRequest())));
    }

    // ---- token accounting ----

    @Test
    void providerUsageFeedsLimiterAndCost() {
        bot = newBot(TestSettings.defaults().limits(limits(0, 0, 0, 0, 150))
                .cost(new CostTracker.Config(true, 1.0, 2.0, 0)).build());
        next = r -> usage(SAY, 100, 50);
        chat("a", "one");
        bot.tick();
        CostTracker.Totals t = bot.costTotals();
        assertEquals(100, t.todayInput());
        assertEquals(50, t.todayOutput());
        assertEquals(0, bot.remaining(id("a")).globalTokens());

        chat("a", "two");
        bot.tick();
        assertEquals(1, requests.size()); // the token limit is reached
        assertTrue(bot.costEnabled());
    }

    @Test
    void zeroUsageFallsBackToTheEstimate() {
        bot = newBot(TestSettings.defaults().limits(limits(0, 0, 0, 0, 100_000))
                .cost(new CostTracker.Config(true, 1.0, 2.0, 0)).build());
        chat("a", "some words to count");
        bot.tick();
        int expectedIn = lastRequest().messages().stream().mapToInt(m -> SimulationResult.estimateTokens(m.content())).sum();
        int expectedOut = SimulationResult.estimateTokens(SAY);
        assertTrue(expectedIn > 0 && expectedOut > 0);
        CostTracker.Totals t = bot.costTotals();
        assertEquals(expectedIn, t.todayInput());
        assertEquals(expectedOut, t.todayOutput());
        assertEquals(100_000 - expectedIn - expectedOut, bot.remaining(id("a")).globalTokens());
    }

    @Test
    void costWarningGoesThroughTheNotifierOncePerDay() {
        bot = newBot(TestSettings.defaults().cost(new CostTracker.Config(true, 1_000_000, 0, 100)).build());
        next = r -> usage(SAY, 120, 0);
        chat("a", "one");
        bot.tick();
        assertEquals(1, toldPermission.size());
        ToldPermission w = toldPermission.getFirst();
        assertEquals("aifriend.cost", w.permission());
        assertEquals("command.cost.warning", w.key());
        assertEquals("120.00", w.placeholders().get("cost"));
        assertEquals("100.00", w.placeholders().get("warn"));

        chat("a", "two");
        bot.tick();
        assertEquals(2, requests.size());
        assertEquals(1, toldPermission.size());

        clock.addAndGet(24L * 3600_000);
        chat("a", "three");
        bot.tick();
        assertEquals(2, toldPermission.size());
    }

    @Test
    void noWarningWhenCostTrackingIsOff() {
        next = r -> usage(SAY, 5_000_000, 5_000_000);
        chat("a", "one");
        bot.tick();
        assertTrue(toldPermission.isEmpty());
        assertFalse(bot.costEnabled());
    }

    // ---- simulate ----

    @Test
    void dryRunPostsNothingRemembersNothingRemovesNothing() {
        bot = newBot(TestSettings.defaults().memory(memory()).build());
        chat("Steve", "one");
        SimulationResult r = simulate(true);
        assertEquals(1, requests.size());
        assertTrue(said.isEmpty());
        assertEquals(0, bot.status().memoryTurns());
        assertEquals(1, bot.status().queued());
        assertTrue(r.blockers().isEmpty());
        assertTrue(r.wouldSend());
        assertTrue(r.wouldAnswer());
        assertEquals("Hello", r.rawReply());
        assertEquals("Hello", r.filteredReply());
        assertEquals(1, r.queuedLines());
        assertEquals(0, r.historyTurns());
        assertTrue(r.request().contains("one"));
        assertNull(r.persona());

        // the real answer still sees the same line
        bot.tick();
        assertEquals(2, requests.size());
        assertTrue(userOf(lastRequest()).contains("one"));
        assertEquals(1, bot.status().memoryTurns());

        chat("Steve", "two");
        assertEquals(2, simulate(true).historyTurns());
    }

    @Test
    void dryRunWithoutCallMakesNoProviderCall() {
        chat("Steve", "one");
        SimulationResult r = simulate(false);
        assertTrue(requests.isEmpty());
        assertNull(r.rawReply());
        assertNull(r.filteredReply());
        assertTrue(r.blockers().isEmpty());
        assertTrue(r.wouldSend());
        assertFalse(r.wouldAnswer());
        assertEquals(1, r.queuedLines());
    }

    @Test
    void dryRunReportsThePersonaAndDoesNotSleep() {
        bot = newBot(TestSettings.defaults().personas(persona("pirate")).build());
        chat("a", "one");
        next = r -> ok(SLEEP);
        SimulationResult r = simulate(true);
        assertEquals("Pirate", r.persona());
        assertEquals("(sleep)", r.rawReply());
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(1, bot.status().queued());
    }

    @Test
    void dryRunListsBlockers() {
        // DISABLED
        TestSettings t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        SimulationResult r = simulate(true);
        assertTrue(r.blockers().contains(Blocker.DISABLED), r.blockers().toString());
        assertFalse(r.wouldSend());

        // NO_KEY
        t = TestSettings.defaults();
        t.apiKey = Settings.KEY_PLACEHOLDER;
        bot = newBot(t.build());
        r = simulate(true);
        assertTrue(r.blockers().contains(Blocker.NO_KEY), r.blockers().toString());

        // NO_CHAT
        bot = newBot(settings);
        r = simulate(true);
        assertEquals(List.of(Blocker.NO_CHAT), r.blockers());

        // SLEEPING
        chat("a", "x");
        bot.sleep(Duration.ofMinutes(5));
        r = simulate(true);
        assertTrue(r.blockers().contains(Blocker.SLEEPING), r.blockers().toString());

        // BACKOFF
        bot = newBot(settings);
        chat("a", "x");
        next = q -> {
            throw new IOException("down");
        };
        bot.tick();
        int before = requests.size();
        r = simulate(true);
        assertEquals(List.of(Blocker.BACKOFF), r.blockers());
        assertEquals(before, requests.size());
        assertNull(r.rawReply());
    }

    @Test
    void dryRunNeverCallsTheProviderWhenBlocked() {
        TestSettings t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        simulate(true);
        t = TestSettings.defaults();
        t.apiKey = "";
        bot = newBot(t.build());
        simulate(true);
        bot = newBot(settings);
        simulate(true); // no chat
        assertTrue(requests.isEmpty());
    }

    @Test
    void dryRunReportsRateLimitAndFilteredOut() {
        bot = newBot(TestSettings.defaults().limits(limits(0, 0, 0, 1, 0)).build());
        chat("a", "one");
        bot.tick();
        chat("a", "two");
        int before = requests.size();
        SimulationResult r = simulate(true);
        assertTrue(r.blockers().contains(Blocker.RATE_LIMITED));
        assertEquals(before, requests.size());

        bot = newBot(TestSettings.defaults().replyFilter(filter(ReplyFilter.BannedMode.DROP)).build());
        next = q -> ok("{\"action\":\"say\",\"text\":\"badword\"}");
        chat("a", "one");
        r = simulate(true);
        assertEquals(List.of(Blocker.FILTERED_OUT), r.blockers());
        assertEquals("badword", r.rawReply());
        assertNull(r.filteredReply());
        assertTrue(said.size() <= 1);
    }

    @Test
    void dryRunReportsAFailedRequestWithoutBackingOff() {
        chat("a", "one");
        next = q -> {
            throw new IOException("down");
        };
        SimulationResult r = simulate(true);
        assertTrue(r.rawReply().startsWith("(request failed"), r.rawReply());
        assertEquals(ChatBot.State.RUNNING, state());
        assertEquals(0, bot.status().failures());
    }

    // ---- direct questions ----

    @Test
    void askWhileSleepingStillAnswers() {
        bot.sleep(Duration.ofMinutes(30));
        bot.askCommand(id("Steve"), "Steve", "are you there?", false);
        assertEquals(List.of("AI Friend|Hello"), said);
        assertTrue(told.isEmpty());
        assertEquals(ChatBot.State.SLEEPING, state());
    }

    @Test
    void askUsesQueuedChatAndRemovesIt() {
        chat("Alex", "building a farm");
        bot.askCommand(id("Steve"), "Steve", "what should I do?", false);
        List<JsonObject> lines = data(userOf(lastRequest()));
        assertEquals(2, lines.size());
        assertEquals("Alex", lines.get(0).get("player").getAsString());
        assertEquals("Steve", lines.get(1).get("player").getAsString());
        assertEquals(0, bot.status().queued());
    }

    @Test
    void askDuringBackoffReportsFailed() {
        chat("a", "one");
        next = r -> {
            throw new IOException("down");
        };
        bot.tick();
        assertEquals(1, requests.size());
        bot.askCommand(id("Steve"), "Steve", "hello", false);
        assertEquals(1, requests.size());
        assertEquals("command.ask.failed", told.getLast().key());
        assertEquals(1, bot.status().failures()); // not increased
    }

    @Test
    void failedDirectQuestionReportsFailedAndBacksOff() {
        next = r -> {
            throw new IOException("down");
        };
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "hello", false);
        assertEquals(1, told.size());
        assertEquals(steve, told.getFirst().player());
        assertEquals("command.ask.failed", told.getFirst().key());
        assertEquals(ChatBot.State.BACKOFF, state());
        assertEquals(1, bot.status().failures());
        assertEquals(60, bot.status().waitSeconds());
        assertTrue(said.isEmpty());
    }

    @Test
    void askWhenDisabledOrWithoutProvider() {
        TestSettings t = TestSettings.defaults();
        t.enabled = false;
        bot = newBot(t.build());
        bot.askCommand(id("Steve"), "Steve", "hi", false);
        assertEquals("command.ask.disabled", told.getLast().key());

        t = TestSettings.defaults();
        t.apiKey = "";
        bot = newBot(t.build());
        bot.askCommand(id("Steve"), "Steve", "hi", false);
        assertEquals("command.ask.disabled", told.getLast().key());
        assertTrue(requests.isEmpty());
    }

    @Test
    void askWithNothingToSayReportsEmpty() {
        bot.askCommand(id("Steve"), "Steve", "   ", false); // blank question, empty queue
        assertEquals("command.ask.empty", told.getLast().key());
        assertTrue(requests.isEmpty());

        next = r -> ok(SLEEP);
        bot.askCommand(id("Steve"), "Steve", "hi", false);
        assertEquals("command.ask.empty", told.getLast().key());
        assertEquals(ChatBot.State.RUNNING, state()); // a direct question never puts the bot to sleep

        next = r -> ok("{\"action\":\"say\",\"text\":\"   \"}");
        bot.askCommand(id("Steve"), "Steve", "hi", false);
        assertEquals("command.ask.empty", told.getLast().key());
        assertTrue(said.isEmpty());
    }

    @Test
    void askAnswerRemovedByFilterReportsEmpty() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).replyFilter(filter(ReplyFilter.BannedMode.DROP)).build());
        next = r -> ok("{\"action\":\"say\",\"text\":\"badword\"}");
        bot.askCommand(id("Steve"), "Steve", "hi", false);
        assertEquals("command.ask.empty", told.getLast().key());
        assertTrue(said.isEmpty());
    }

    @Test
    void askAccountsTokensForThePlayer() {
        bot = newBot(TestSettings.defaults().mentions(mentions(false)).limits(limits(0, 10, 1000, 0, 0)).build());
        next = r -> usage(SAY, 30, 20);
        UUID steve = id("Steve");
        bot.askCommand(steve, "Steve", "hi", false);
        RateLimiter.Remaining rem = bot.remaining(steve);
        assertEquals(9, rem.playerCalls());
        assertEquals(950, rem.playerTokens());
    }
}
