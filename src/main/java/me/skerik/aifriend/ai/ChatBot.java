package me.skerik.aifriend.ai;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.Scheduler;
import me.skerik.aifriend.ai.provider.AiProvider;
import me.skerik.aifriend.ai.provider.ChatMessage;
import me.skerik.aifriend.ai.provider.ProviderRequest;
import me.skerik.aifriend.ai.provider.ProviderResult;
import me.skerik.aifriend.chat.BoredomDetector;
import me.skerik.aifriend.chat.ChatSupervisor;
import me.skerik.aifriend.chat.ConversationMemory;
import me.skerik.aifriend.chat.CostTracker;
import me.skerik.aifriend.chat.EventPrompts;
import me.skerik.aifriend.chat.MentionDetector;
import me.skerik.aifriend.chat.OptOutStore;
import me.skerik.aifriend.chat.Mood;
import me.skerik.aifriend.chat.Persona;
import me.skerik.aifriend.chat.PersonaSelector;
import me.skerik.aifriend.chat.RateLimiter;
import me.skerik.aifriend.chat.ReplyFilter;
import me.skerik.aifriend.chat.Relationships;
import me.skerik.aifriend.chat.ServerContext;
import me.skerik.aifriend.chat.SimulationResult;
import me.skerik.aifriend.config.Awareness;
import me.skerik.aifriend.config.ChatFeatures;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.knowledge.KnowledgeBase;
import me.skerik.aifriend.knowledge.ServerKnowledge;
import me.skerik.aifriend.longmemory.LongTermMemory;
import me.skerik.aifriend.storage.PlayerNotesStore;
import org.jspecify.annotations.Nullable;

/**
 * The chat loop. Three things can make the bot talk: the timer (every {@code interval-seconds}, answers the queued chat
 * lines and event reactions), a direct question (/ai or a mention) and nothing else. All of them go through
 * {@link #exchange}: build the request (system rules + persona, memory, chat lines), call the provider, account tokens,
 * parse, filter, post. Failures never escape; they only stretch the pause (1, 2, 4 ... max 15 min).
 * {@link #tick()} and the direct paths are plain code, testable without a server.
 */
public final class ChatBot {

    /** Posts the bot's answer in public chat; the implementation moves to the right thread. */
    @FunctionalInterface
    public interface Broadcaster {
        void say(String botName, String text);
    }

    /** Tells one player (or everybody with a permission) something; keys are message keys, values are plain text. */
    public interface Notifier {
        void tell(UUID player, String key, Map<String, String> placeholders);

        void tellPermission(String permission, String key, Map<String, String> placeholders);
    }

    /** World name and time of day for persona selection (read on the right thread by the plugin). */
    public record WorldView(String world, long timeTicks) {
    }

    /** What /aifriend status shows. */
    public enum State { RUNNING, DISABLED, NO_KEY, SLEEPING, BACKOFF }

    public record Status(State state, long waitSeconds, int failures, int queued, String provider, int memoryTurns,
                         int mutedPlayers, String persona, String longMemory) {
    }

    public enum MuteResult { DISABLED, DONE, ALREADY }

    public enum NicknameResult { DISABLED, MUTED, INVALID, REJECTED, SET, CLEARED }

    /** What caused an answer. */
    public enum Source { TIMED, DIRECT }

    /**
     * Last stop before an answer reaches chat (public API event). Return the text to post (it is cleaned and cut again),
     * or null to post nothing. Runs on an async thread.
     */
    @FunctionalInterface
    public interface ResponseInterceptor {
        @Nullable String intercept(String botName, String text, @Nullable UUID asker, Source source);
    }

    static final long MAX_BACKOFF_SECONDS = 15 * 60;
    /** How many different players must take part before a "sleep" answer is believed. */
    static final int MIN_PLAYERS_FOR_SLEEP = 2;
    static final int MAX_EVENT_LINES = 5;
    static final int MAX_PARALLEL_ASKS = 3;
    /** Three notes of 120 characters in a language that tokenizes badly (Czech) plus the JSON do not fit into 300. */
    static final int EXTRACT_MAX_TOKENS = 600;
    static final int MAX_PENDING_LINES = 60;
    static final int RECENT_REPLIES = 3;
    static final int MAX_KNOWN_NAMES = 5000;
    static final int MAX_RELATIONS = 5;

    /** Everything built from one {@link Settings} snapshot; reused across reloads where the config did not change. */
    private record Runtime(Settings settings, Optional<AiProvider> provider, PersonaSelector personas,
                           ConversationMemory memory, RateLimiter limiter, CostTracker cost, ReplyFilter filter,
                           MentionDetector mentions, EventPrompts events, Mood mood, BoredomDetector boredom,
                           ChatSupervisor supervisor) {
    }

    /** A request ready to send. */
    private record Prepared(List<ChatMessage> messages, String userText, String memoryText, @Nullable Persona persona,
                            int historyTurns) {
    }

    private record Answer(Reply reply) {
    }

    private final Logger log;
    private final Scheduler scheduler;
    private final Function<Settings, Optional<AiProvider>> providerFactory;
    private final Broadcaster broadcaster;
    private final Notifier notifier;
    private final Supplier<WorldView> world;
    private final IntSupplier onlinePlayers;
    private final LongSupplier clock;
    private final OptOutStore optOut;
    private final ZoneId zone = ZoneId.systemDefault();
    private final ChatBuffer buffer;
    private final ConcurrentLinkedQueue<String> eventLines = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicInteger asks = new AtomicInteger();
    /** Chat name (lower case) to UUID, filled by {@link #onChat}; the {@link ChatLine} record only carries names. */
    private final ConcurrentHashMap<String, UUID> knownNames = new ConcurrentHashMap<>();
    /** Chat lines of answered exchanges that long-term memory has not looked at yet. */
    private final ConcurrentLinkedQueue<ChatLine> pendingNotes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger answeredExchanges = new AtomicInteger();
    private final AtomicBoolean extracting = new AtomicBoolean();
    /** A queued line sounds like "forget that": extract as soon as possible, not only every N-th exchange. */
    private final AtomicBoolean forgetPending = new AtomicBoolean();
    /** The bot's latest posted messages, newest last: context for "forget that" in the extraction request. */
    private final ConcurrentLinkedQueue<String> recentReplies = new ConcurrentLinkedQueue<>();
    private volatile @Nullable LongTermMemory longMemory;
    private volatile @Nullable ServerKnowledge knowledge;
    private final Relationships relationships = new Relationships();
    private volatile Supplier<ServerContext.Snapshot> contextSource = ServerContext.Snapshot::empty;

    private volatile Runtime rt;
    private volatile @Nullable Runnable cancelTimer;
    private volatile long sleepUntil;
    private volatile long retryAt;
    private volatile int failures;
    private volatile String lastReply = "";
    private volatile ResponseInterceptor interceptor = (bot, text, asker, source) -> text;

    public ChatBot(Logger log, Scheduler scheduler, Function<Settings, Optional<AiProvider>> providerFactory,
                   Broadcaster broadcaster, Notifier notifier, Supplier<WorldView> world, IntSupplier onlinePlayers,
                   LongSupplier clock, OptOutStore optOut, Settings settings) {
        this.log = log;
        this.scheduler = scheduler;
        this.providerFactory = providerFactory;
        this.broadcaster = broadcaster;
        this.notifier = notifier;
        this.world = world;
        this.onlinePlayers = onlinePlayers;
        this.clock = clock;
        this.optOut = optOut;
        this.buffer = new ChatBuffer(settings.maxQueuedMessages());
        this.rt = build(settings, null);
    }

    private Runtime build(Settings s, @Nullable Runtime old) {
        ChatFeatures f = s.features();
        ReplyFilter.Config fc = f.replyFilter().enabled() ? f.replyFilter() : f.replyFilter().withMaxLength(s.maxReplyLength());
        boolean same = old != null;
        // counters and memory survive a reload when their configuration did not change
        ConversationMemory memory = same && old.settings.features().memory().equals(f.memory()) ? old.memory : new ConversationMemory(f.memory());
        RateLimiter limiter = same && old.settings.features().limits().equals(f.limits()) ? old.limiter : new RateLimiter(f.limits(), zone);
        CostTracker cost = same && old.settings.features().cost().equals(f.cost()) ? old.cost : new CostTracker(f.cost(), zone);
        EventPrompts events = same && old.settings.features().events().equals(f.events()) ? old.events : new EventPrompts(f.events());
        Awareness a = f.awareness();
        Mood mood = same && old.settings.features().awareness().mood().equals(a.mood()) ? old.mood : new Mood(a.mood());
        BoredomDetector boredom = same && old.settings.features().awareness().boredom().equals(a.boredom()) ? old.boredom
                : new BoredomDetector(a.boredom(), zone);
        ChatSupervisor supervisor = same && old.settings.features().awareness().supervision().equals(a.supervision())
                ? old.supervisor : new ChatSupervisor(a.supervision());
        return new Runtime(s, providerFactory.apply(s), new PersonaSelector(f.personas()), memory, limiter, cost,
                new ReplyFilter(fc), new MentionDetector(s.chatbotName(), f.mentions()), events, mood, boredom, supervisor);
    }

    // ---- lifecycle ----

    public void setInterceptor(ResponseInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /** The last text the bot posted, empty before the first answer. */
    public String lastMessage() {
        return lastReply;
    }

    /** (Re)starts the timer with the current settings. */
    public synchronized void start() {
        stop();
        long ticks = rt.settings.intervalSeconds() * 20L;
        cancelTimer = scheduler.runAsyncTimer(this::tick, ticks, ticks);
    }

    /** Cancels the timer (disable, reload). Safe to call any number of times. */
    public synchronized void stop() {
        Runnable cancel = cancelTimer;
        cancelTimer = null;
        if (cancel != null) cancel.run();
    }

    /** New settings from a reload: the bot wakes up, forgets failures and restarts its timer. */
    public synchronized void reload(Settings newSettings) {
        buffer.setCapacity(newSettings.maxQueuedMessages());
        rt = build(newSettings, rt);
        sleepUntil = 0;
        retryAt = 0;
        failures = 0;
        start();
    }

    /** Switches long-term memory on (non-null) or off (null); called by the plugin once the database is ready or gone. */
    public void setLongMemory(@Nullable LongTermMemory memory) {
        this.longMemory = memory;
        if (memory == null) {
            pendingNotes.clear();
            forgetPending.set(false);
        }
    }

    /** The server knowledge (file, taught facts, self-description); null until the plugin has loaded it. */
    public void setKnowledge(@Nullable ServerKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public @Nullable ServerKnowledge knowledge() {
        return knowledge;
    }

    public @Nullable LongTermMemory longMemory() {
        return longMemory;
    }

    /** Where the world picture (time, weather, player surroundings) comes from; read on async threads, must not block. */
    public void setContextSource(Supplier<ServerContext.Snapshot> source) {
        this.contextSource = source;
    }

    /** The relationship records (in memory; the plugin attaches a database to them). */
    public Relationships relationships() {
        return relationships;
    }

    /** Saves the opt-out list and the relationships; call on disable (before the database is closed). */
    public void flush() {
        optOut.flush();
        try {
            relationships.flush();
        } catch (java.sql.SQLException | RuntimeException e) {
            log.warning("Relationships: could not save to the database (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ---- intake ----

    /**
     * Called by the chat listener for every public chat message (async chat thread).
     *
     * @param mayAsk       the player may address the bot directly (aifriend.ask)
     * @param bypassLimits the player ignores the rate limits (aifriend.bypass-limits)
     */
    public void onChat(UUID player, String name, String text, boolean mayAsk, boolean bypassLimits) {
        onChat(player, name, text, mayAsk, bypassLimits, false);
    }

    /** As above; {@code exemptFromSupervision} (aifriend.bypass-supervision) skips the friendly spam/caps/language notes. */
    public void onChat(UUID player, String name, String text, boolean mayAsk, boolean bypassLimits,
                       boolean exemptFromSupervision) {
        Runtime r = rt;
        if (knownNames.size() > MAX_KNOWN_NAMES) knownNames.clear();
        knownNames.put(name.toLowerCase(Locale.ROOT), player);
        long now = clock.getAsLong();
        // somebody is talking, muted or not: the chat is not quiet
        r.boredom.playerChatted(now);
        if (r.settings.features().optOut().enabled() && optOut.isMuted(player)) return;
        if (!exemptFromSupervision && r.supervisor.enabled()) {
            r.supervisor.inspect(player, text, now).ifPresent(issue ->
                    notifier.tell(player, "supervision." + issue.name().toLowerCase(Locale.ROOT), Map.of()));
        }
        ChatLine line = ChatLine.of(name, text);
        boolean buffered = false;
        if (line != null && listening(r)) {
            buffer.add(line);
            buffered = true;
        }
        MentionDetector.Config mc = r.settings.features().mentions();
        if (mayAsk && mc.enabled() && mc.directAnswer()) {
            // a line that was not buffered (bot asleep) is passed along as the question itself
            ChatLine extra = buffered ? null : line;
            r.mentions.detect(text).ifPresent(m -> scheduler.runAsync(() -> runAsk(player, name, bypassLimits, extra)));
        }
    }

    /** True while chat lines are worth keeping: bot on, provider usable, not asleep. */
    private boolean listening(Runtime r) {
        return r.settings.enabled() && r.provider.isPresent() && clock.getAsLong() >= sleepUntil;
    }

    /** A game event (death, advancement, ...). May cause an early answer. */
    public void onEvent(EventPrompts.GameEvent event) {
        Runtime r = rt;
        r.mood.record(event, clock.getAsLong());
        if (!listening(r)) return;
        r.events.accept(event, Instant.ofEpochMilli(clock.getAsLong())).ifPresent(line -> {
            eventLines.add(line);
            while (eventLines.size() > MAX_EVENT_LINES) eventLines.poll();
            scheduler.runAsync(this::tick);
        });
    }

    // ---- timed answer ----

    /** One timer run. Never throws. */
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            run();
        } catch (RuntimeException e) {
            // a bug must not kill the repeating task
            log.log(Level.SEVERE, "Unexpected error in the AI Friend loop", e);
        } finally {
            busy.set(false);
        }
    }

    private void run() {
        Runtime r = rt;
        Settings s = r.settings;
        long now = clock.getAsLong();
        int online = onlinePlayers.getAsInt();
        // an empty server is not "bored": the quiet time starts when the first player arrives
        if (online == 0) r.boredom.touch(now);
        if (!s.enabled() || r.provider.isEmpty() || now < sleepUntil || now < retryAt) return;
        if (online == 0) return;
        List<ChatLine> batch = buffer.snapshot();
        List<String> events = new ArrayList<>(eventLines);
        BoredomDetector.Kind idle = null;
        if (batch.isEmpty() && events.isEmpty()) {
            // nothing to answer: maybe the chat is just quiet (only with at least min-players online)
            idle = r.boredom.ready(now, online, ThreadLocalRandom.current()).orElse(null);
            if (idle == null) return;
        }
        Instant at = Instant.ofEpochMilli(now);
        if (!r.limiter.tryAcquire(null, at).allowed()) {
            log.fine("Server-wide AI limit reached; waiting");
            return;
        }
        String idleHint = null;
        if (idle != null) {
            idleHint = BoredomDetector.prompt(idle, r.boredom.quietMinutes(now), online);
            r.boredom.commit(now);
        }

        Prepared p = prepare(r, batch, events, true, idleHint);
        Answer answer;
        try {
            answer = call(r, p, null, at);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | RuntimeException e) {
            fail(e, now);
            return;
        }

        failures = 0;
        retryAt = 0;
        buffer.remove(batch);
        events.forEach(eventLines::remove);
        switch (answer.reply.action()) {
            case SAY -> {
                if (post(r, answer.reply, p, null, Source.TIMED).isPresent()) afterAnswer(batch);
            }
            case SLEEP -> maybeSleep(r, batch, now);
            case NONE -> {
                log.fine("The AI service returned nothing to say");
                collectNotes(batch); // staying quiet is no reason to lose what players told about themselves
            }
        }
    }

    // ---- direct questions ----

    /**
     * Answers one player's question (/ai or a mention) on an async task. Everything the player needs to know (limit
     * reached, bot unavailable, no answer) is sent through the {@link Notifier}; the answer goes to public chat.
     */
    public void askCommand(UUID player, String name, String question, boolean bypassLimits) {
        if (!rt.settings.features().mentions().enabled()) {
            notifier.tell(player, "command.ask.disabled", Map.of());
            return;
        }
        ChatLine line = ChatLine.of(name, question);
        scheduler.runAsync(() -> runAsk(player, name, bypassLimits, line));
    }

    private void runAsk(UUID player, String name, boolean bypassLimits, @Nullable ChatLine extra) {
        if (asks.incrementAndGet() > MAX_PARALLEL_ASKS) {
            asks.decrementAndGet();
            return;
        }
        try {
            Runtime r = rt;
            long now = clock.getAsLong();
            Instant at = Instant.ofEpochMilli(now);
            if (!r.settings.enabled() || r.provider.isEmpty()) {
                notifier.tell(player, "command.ask.disabled", Map.of());
                return;
            }
            if (r.settings.features().optOut().enabled() && optOut.isMuted(player)) {
                notifier.tell(player, "command.ask.muted", Map.of());
                return;
            }
            if (now < retryAt) {
                notifier.tell(player, "command.ask.failed", Map.of());
                return;
            }
            if (!bypassLimits) {
                RateLimiter.Decision d = r.limiter.tryAcquire(player, at);
                if (!d.allowed()) {
                    notifier.tell(player, "command.limit." + d.reason().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                            durationPlaceholders(d.retryAfter()));
                    return;
                }
            }
            List<ChatLine> batch = buffer.snapshot();
            List<ChatLine> lines = new ArrayList<>(batch);
            if (extra != null) lines.add(extra);
            if (lines.isEmpty()) {
                notifier.tell(player, "command.ask.empty", Map.of());
                return;
            }
            Prepared p = prepare(r, lines, List.of(), true, null);
            Answer answer;
            try {
                answer = call(r, p, player, at);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | RuntimeException e) {
                fail(e, now);
                notifier.tell(player, "command.ask.failed", Map.of());
                return;
            }
            failures = 0;
            retryAt = 0;
            buffer.remove(batch);
            if (answer.reply.action() != Reply.Action.SAY || post(r, answer.reply, p, player, Source.DIRECT).isEmpty()) {
                notifier.tell(player, "command.ask.empty", Map.of());
            } else {
                afterAnswer(lines);
            }
        } finally {
            asks.decrementAndGet();
        }
    }

    private static Map<String, String> durationPlaceholders(Duration d) {
        long secs = Math.max(0, d.toSeconds());
        return Map.of("seconds", Long.toString(secs), "hours", Long.toString(secs / 3600),
                "minutes", Long.toString((secs % 3600 + 59) / 60));
    }

    // ---- simulate ----

    /** Dry run for /aifriend simulate: nothing is posted, remembered or removed. {@code callProvider} false stops before the request. */
    public void simulate(boolean callProvider, Consumer<SimulationResult> result) {
        scheduler.runAsync(() -> result.accept(runSimulate(callProvider)));
    }

    private SimulationResult runSimulate(boolean callProvider) {
        Runtime r = rt;
        long now = clock.getAsLong();
        Instant at = Instant.ofEpochMilli(now);
        List<ChatLine> batch = buffer.snapshot();
        List<String> events = new ArrayList<>(eventLines);
        Prepared p = prepare(r, batch, events, false, null);
        List<SimulationResult.Blocker> blockers = new ArrayList<>();
        if (!r.settings.enabled()) blockers.add(SimulationResult.Blocker.DISABLED);
        if (r.provider.isEmpty()) blockers.add(SimulationResult.Blocker.NO_KEY);
        if (now < sleepUntil) blockers.add(SimulationResult.Blocker.SLEEPING);
        if (now < retryAt) blockers.add(SimulationResult.Blocker.BACKOFF);
        if (batch.isEmpty() && events.isEmpty()) blockers.add(SimulationResult.Blocker.NO_CHAT);
        RateLimiter.Remaining rem = r.limiter.remaining(null, at);
        if (rem.globalCalls() == 0 || rem.globalTokens() == 0) blockers.add(SimulationResult.Blocker.RATE_LIMITED);
        String persona = p.persona == null ? null : p.persona.name();
        if (!callProvider || !blockers.isEmpty()) {
            return new SimulationResult(persona, p.userText, p.historyTurns, batch.size(), null, null, blockers);
        }
        try {
            Answer a = call(r, p, null, at);
            String raw = a.reply.action() == Reply.Action.SAY ? a.reply.text()
                    : a.reply.action() == Reply.Action.SLEEP ? "(sleep)" : null;
            String filtered = a.reply.action() == Reply.Action.SAY ? r.filter.apply(a.reply.text()).orElse(null) : null;
            if (a.reply.action() == Reply.Action.SAY && filtered == null) blockers.add(SimulationResult.Blocker.FILTERED_OUT);
            return new SimulationResult(persona, p.userText, p.historyTurns, batch.size(), raw, filtered, blockers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SimulationResult(persona, p.userText, p.historyTurns, batch.size(), null, null, blockers);
        } catch (IOException | RuntimeException e) {
            return new SimulationResult(persona, p.userText, p.historyTurns, batch.size(),
                    "(request failed: " + e.getClass().getSimpleName() + ")", null, blockers);
        }
    }

    // ---- request / response ----

    private Prepared prepare(Runtime r, List<ChatLine> lines, List<String> events, boolean countUse, @Nullable String idleHint) {
        WorldView w = world.get();
        long now = clock.getAsLong();
        Persona persona = r.personas.select(w.world(), w.timeTicks(), ThreadLocalRandom.current()).orElse(null);
        List<ChatMessage> messages = new ArrayList<>();
        Mood.Level mood = r.mood.enabled() ? r.mood.level(now) : null;
        messages.add(new ChatMessage(ChatMessage.Role.SYSTEM, PromptBuilder.system(r.settings, persona, mood, idleHint)));
        int history = 0;
        if (r.memory.isActive()) {
            for (ConversationMemory.Turn t : r.memory.history()) {
                messages.add(new ChatMessage(t.role() == ConversationMemory.Role.USER ? ChatMessage.Role.USER
                        : ChatMessage.Role.ASSISTANT, t.text()));
                history++;
            }
        }
        String previous = r.memory.isActive() ? "" : lastReply;
        List<PromptBuilder.MemoryNote> notes = recall(lines, countUse);
        PromptBuilder.Data data = new PromptBuilder.Data(lines, events, notes, relationsFor(r, lines, now), contextFor(r, lines),
                knowledgeFor(r, lines));
        String user = PromptBuilder.user(data, previous);
        // the conversation history keeps the chat only: remembered notes are fetched fresh for every request
        boolean extras = !notes.isEmpty() || !data.relations().isEmpty() || !data.context().isEmpty() || !data.knowledge().isEmpty();
        String plain = extras ? PromptBuilder.user(lines, events, previous) : user;
        messages.add(new ChatMessage(ChatMessage.Role.USER, user));
        return new Prepared(messages, user, plain, persona, history);
    }

    /** Long-term notes about the players in this batch, as untrusted data lines. Blocking (async thread); empty when off or on any error. */
    private List<PromptBuilder.MemoryNote> recall(List<ChatLine> lines, boolean countUse) {
        LongTermMemory ltm = longMemory;
        if (ltm == null || !ltm.config().enabled()) return List.of();
        Map<UUID, String> who = new LinkedHashMap<>();
        for (ChatLine line : lines) {
            UUID id = knownNames.get(line.player().toLowerCase(Locale.ROOT));
            if (id != null && !isMuted(id)) who.putIfAbsent(id, line.player());
        }
        if (who.isEmpty()) return List.of();
        StringBuilder query = new StringBuilder();
        for (ChatLine line : lines) query.append(line.text()).append(' ');
        List<PromptBuilder.MemoryNote> out = new ArrayList<>();
        for (PlayerNotesStore.Note n : ltm.retrieve(who.keySet(), query.toString().strip(), countUse)) {
            out.add(new PromptBuilder.MemoryNote(who.getOrDefault(n.player(), n.playerName()), n.text()));
        }
        return out;
    }

    /** Server and self knowledge that fits what the players just wrote (in-memory word matching, no I/O). */
    private List<KnowledgeBase.Entry> knowledgeFor(Runtime r, List<ChatLine> lines) {
        ServerKnowledge k = knowledge;
        if (k == null) return List.of();
        StringBuilder query = new StringBuilder();
        for (ChatLine line : lines) query.append(line.text()).append(' ');
        return k.retrieve(query.toString(), r.settings.features().knowledge());
    }

    /** How well the bot knows the players of this batch (only those it knows at least a little), as untrusted data lines. */
    private List<PromptBuilder.Relation> relationsFor(Runtime r, List<ChatLine> lines, long now) {
        Relationships.Config rc = r.settings.features().awareness().relationships();
        if (!rc.enabled()) return List.of();
        Map<UUID, String> who = new LinkedHashMap<>();
        for (ChatLine line : lines) {
            UUID id = knownNames.get(line.player().toLowerCase(Locale.ROOT));
            if (id != null && !isMuted(id)) who.putIfAbsent(id, line.player());
        }
        List<PromptBuilder.Relation> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : who.entrySet()) {
            Relationships.View v = relationships.view(e.getKey(), now, rc);
            String nick = rc.nicknames() ? v.nickname() : null;
            if (v.level() == Relationships.Level.STRANGER && nick == null) continue;
            out.add(new PromptBuilder.Relation(e.getValue(), v.level(), v.knownDays(), nick));
            if (out.size() >= MAX_RELATIONS) break;
        }
        return out;
    }

    /** The world picture for this request: the general facts plus the surroundings of the players who wrote. */
    private ServerContext.Snapshot contextFor(Runtime r, List<ChatLine> lines) {
        ServerContext.Config cc = r.settings.features().awareness().context();
        if (!cc.enabled()) return ServerContext.Snapshot.empty();
        StringBuilder said = new StringBuilder();
        for (ChatLine line : lines) said.append(line.text()).append(' ');
        // the bot must not keep telling players where they are: most requests carry no surroundings at all
        if (!ServerContext.share(cc, said.toString(), ThreadLocalRandom.current())) return ServerContext.Snapshot.empty();
        List<String> names = lines.stream().map(ChatLine::player).distinct().toList();
        return contextSource.get().forPlayers(names, MAX_RELATIONS);
    }

    // ---- long-term memory: collecting notes ----

    /** Called after an answer was posted: counts the exchange and, every N-th time, extracts notes in the background. */
    private void afterAnswer(List<ChatLine> lines) {
        if (lines.isEmpty()) return;
        countRelationships(lines);
        collectNotes(lines);
    }

    /** Queues the lines for long-term notes and, every N-th batch, extracts them in the background. */
    private void collectNotes(List<ChatLine> lines) {
        if (lines.isEmpty()) return;
        LongTermMemory ltm = longMemory;
        if (ltm == null || !ltm.config().enabled()) return;
        for (ChatLine line : lines) {
            UUID id = knownNames.get(line.player().toLowerCase(Locale.ROOT));
            if (id == null || isMuted(id)) continue;
            pendingNotes.add(line);
            if (NoteExtractor.mentionsForgetting(line.text())) forgetPending.set(true);
        }
        while (pendingNotes.size() > MAX_PENDING_LINES) pendingNotes.poll();
        // a request to forget is handled at once (and retried with the next exchange when skipped), not only every N-th
        // exchange, so the note is not used again meanwhile
        if (answeredExchanges.incrementAndGet() % ltm.config().extractEveryExchanges() == 0 || forgetPending.get()) {
            scheduler.runAsync(() -> extractNotes(ltm));
        }
    }

    /** Every player who took part in an answered exchange gets to know the bot a little better. */
    private void countRelationships(List<ChatLine> lines) {
        if (!rt.settings.features().awareness().relationships().enabled()) return;
        long now = clock.getAsLong();
        Set<UUID> counted = new HashSet<>();
        for (ChatLine line : lines) {
            UUID id = knownNames.get(line.player().toLowerCase(Locale.ROOT));
            if (id != null && !isMuted(id) && counted.add(id)) relationships.interaction(id, line.player(), now);
        }
    }

    /**
     * One extraction call: same provider chain, global rate limit and cost tracking as any other call. Skipped (the
     * lines stay queued for the next round) when the bot is off, asleep, in backoff or rate limited. Never throws and
     * never touches the answering state, so a failure here cannot slow the bot down.
     */
    private void extractNotes(LongTermMemory ltm) {
        if (!extracting.compareAndSet(false, true)) return;
        List<ChatLine> lines = new ArrayList<>();
        try {
            Runtime r = rt;
            long now = clock.getAsLong();
            if (!r.settings.enabled() || r.provider.isEmpty() || now < sleepUntil || now < retryAt) return;
            Instant at = Instant.ofEpochMilli(now);
            if (!r.limiter.tryAcquire(null, at).allowed()) {
                log.fine("Long-term memory: server-wide AI limit reached; extraction skipped");
                return;
            }
            ChatLine line;
            while ((line = pendingNotes.poll()) != null) {
                UUID id = knownNames.get(line.player().toLowerCase(Locale.ROOT));
                if (id != null && !isMuted(id)) lines.add(line);
            }
            forgetPending.set(false);
            if (lines.isEmpty()) return;
            Map<String, String> names = new LinkedHashMap<>();
            for (ChatLine l : lines) names.putIfAbsent(l.player().toLowerCase(Locale.ROOT), l.player());
            // the stored notes of players who seem to ask for forgetting, so the model can name exactly which ones
            Map<Long, UUID> shown = new HashMap<>();
            List<NoteExtractor.KnownNote> known = new ArrayList<>();
            Set<UUID> asking = new HashSet<>();
            for (ChatLine l : lines) {
                UUID id = knownNames.get(l.player().toLowerCase(Locale.ROOT));
                if (id == null || !NoteExtractor.mentionsForgetting(l.text()) || !asking.add(id)) continue;
                for (PlayerNotesStore.Note n : ltm.notesNow(id)) {
                    shown.put(n.id(), id);
                    known.add(new NoteExtractor.KnownNote(n.id(), names.get(l.player().toLowerCase(Locale.ROOT)), n.text()));
                }
            }
            List<String> botSaid = asking.isEmpty() ? List.of() : List.copyOf(recentReplies);
            ProviderResult res = complete(r, NoteExtractor.messages(lines, known, botSaid), EXTRACT_MAX_TOKENS, true, null, at);
            List<NoteExtractor.Candidate> candidates = NoteExtractor.parse(res.text(), names);
            List<NoteExtractor.Forget> forgets = NoteExtractor.parseForgets(res.text(), names);
            if (candidates.isEmpty() && forgets.isEmpty() && NoteExtractor.unexpectedlyEmpty(res.text())) {
                // nothing usable although the model did not say "nothing to keep": wrong format, filtered or cut off
                log.warning("Long-term memory: no note could be read from the model's answer (" + res.text().length()
                        + " characters). If this repeats, check that the provider answers with the requested JSON.");
            }
            forget(ltm, forgets, shown, asking);
            for (NoteExtractor.Candidate c : candidates) {
                UUID id = knownNames.get(c.player().toLowerCase(Locale.ROOT));
                if (id == null || isMuted(id)) continue;
                ltm.remember(id, c.player(), c.note());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the provider failed: keep the lines (above all a request to forget) for the next round
            requeue(lines);
            log.warning("Long-term memory: note extraction failed (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
        } catch (RuntimeException e) {
            log.warning("Long-term memory: note extraction failed (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
        } finally {
            extracting.set(false);
        }
    }

    /** Puts lines of a failed extraction back in front of newer ones (still capped at {@link #MAX_PENDING_LINES}). */
    private void requeue(List<ChatLine> lines) {
        if (lines.isEmpty()) return;
        List<ChatLine> newer = new ArrayList<>();
        ChatLine l;
        while ((l = pendingNotes.poll()) != null) newer.add(l);
        pendingNotes.addAll(lines);
        pendingNotes.addAll(newer);
        while (pendingNotes.size() > MAX_PENDING_LINES) pendingNotes.poll();
        if (pendingNotes.stream().anyMatch(x -> NoteExtractor.mentionsForgetting(x.text()))) forgetPending.set(true);
    }

    /**
     * Deletes what players asked to forget, before new notes are stored. Only players whose own line sounds like a
     * request to forget ({@code asking}) lose notes, so nobody can talk the model into deleting another player's notes.
     * A note id counts only when that note was shown to the model as a note of the same player; a topic is matched
     * against the player's notes.
     */
    private void forget(LongTermMemory ltm, List<NoteExtractor.Forget> forgets, Map<Long, UUID> shown, Set<UUID> asking) {
        Map<UUID, List<Long>> ids = new LinkedHashMap<>();
        Map<UUID, Integer> removed = new LinkedHashMap<>();
        Map<UUID, String> who = new HashMap<>();
        for (NoteExtractor.Forget f : forgets) {
            UUID id = knownNames.get(f.player().toLowerCase(Locale.ROOT));
            if (id == null || !asking.contains(id)) continue;
            who.putIfAbsent(id, f.player());
            if (f.id() < 0) removed.merge(id, ltm.forgetTopic(id, f.topic()), Integer::sum);
            else if (id.equals(shown.get(f.id()))) ids.computeIfAbsent(id, k -> new ArrayList<>()).add(f.id());
        }
        ids.forEach((id, list) -> removed.merge(id, ltm.forgetNotes(id, list), Integer::sum));
        removed.forEach((id, n) -> {
            if (n > 0) log.info("Long-term memory: deleted " + n + " note(s) of " + who.get(id) + " at their request");
        });
    }

    /** Sends the request, accounts the tokens (limits, cost) and parses the answer. Blocking. */
    private Answer call(Runtime r, Prepared p, @Nullable UUID asker, Instant at) throws IOException, InterruptedException {
        ProviderResult res = complete(r, p.messages, r.settings.provider().maxOutputTokens(), true, asker, at);
        return new Answer(ReplyParser.parse(res.text(), r.settings.chatbotName(), r.settings.maxReplyLength()));
    }

    /** One provider request with token accounting (rate limiter, cost tracker). Blocking. */
    private ProviderResult complete(Runtime r, List<ChatMessage> messages, int maxOutputTokens, boolean json,
                                    @Nullable UUID asker, Instant at) throws IOException, InterruptedException {
        AiProvider provider = r.provider.orElseThrow();
        ProviderResult res = provider.complete(new ProviderRequest(messages, maxOutputTokens, json));
        int in = res.inputTokens();
        int out = res.outputTokens();
        if (in == 0 && out == 0) {
            in = messages.stream().mapToInt(m -> SimulationResult.estimateTokens(m.content())).sum();
            out = SimulationResult.estimateTokens(res.text());
        }
        r.limiter.recordUsage(asker, (long) in + out, at);
        if (r.cost.record(in, out, at)) {
            CostTracker.Totals t = r.cost.totals(at);
            notifier.tellPermission("aifriend.cost", "command.cost.warning", Map.of(
                    "cost", String.format(Locale.ROOT, "%.2f", t.todayCost()),
                    "warn", String.format(Locale.ROOT, "%.2f", t.warnDailyCost())));
        }
        return res;
    }

    /** Filters and posts a SAY answer, remembers what was really posted. Returns the posted text. */
    private Optional<String> post(Runtime r, Reply reply, Prepared p, @Nullable UUID asker, Source source) {
        Optional<String> filtered = r.filter.apply(reply.text());
        if (filtered.isEmpty()) {
            log.fine("The reply filter removed the whole answer");
            return filtered;
        }
        String name = r.settings.chatbotName();
        String changed;
        try {
            changed = interceptor.intercept(name, filtered.get(), asker, source);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "A response listener failed; posting the original answer", e);
            changed = filtered.get();
        }
        // a listener may have replaced the text: clean and cut it again, it is still going to public chat
        Optional<String> result = changed == null ? Optional.empty()
                : Optional.of(Text.cut(Text.singleLine(changed), r.settings.maxReplyLength())).filter(t -> !t.isEmpty());
        if (result.isEmpty()) return result;
        String text = result.get();
        lastReply = text;
        recentReplies.add(text);
        while (recentReplies.size() > RECENT_REPLIES) recentReplies.poll();
        r.boredom.touch(clock.getAsLong());
        // the history shows the model its own answers in the format it must keep using (plain text here makes JSON mode drift)
        if (r.memory.isActive()) r.memory.add(p.memoryText, Reply.asJson(text));
        broadcaster.say(name, text);
        return result;
    }

    private void maybeSleep(Runtime r, List<ChatLine> batch, long now) {
        Settings s = r.settings;
        int needed = Math.min(MIN_PLAYERS_FOR_SLEEP, Math.max(1, onlinePlayers.getAsInt()));
        if (!s.stopWhenIgnored() || distinctPlayers(batch) < needed) {
            log.fine("Ignored a request to stay quiet (not enough different players in the chat)");
            return;
        }
        sleep(Duration.ofMinutes(s.sleepMinutes()));
        log.info("Players are not interested in talking to the bot; staying quiet for " + s.sleepMinutes() + " minutes.");
    }

    static int distinctPlayers(List<ChatLine> batch) {
        Set<String> names = new HashSet<>();
        for (ChatLine line : batch) names.add(line.player());
        return names.size();
    }

    private void fail(Exception e, long now) {
        int n = failures + 1;
        failures = n;
        long wait = backoffSeconds(n);
        retryAt = now + wait * 1000;
        String why;
        if (e instanceof ApiException api && api.status() == 401) {
            why = "HTTP 401, the api-key was rejected; check the key of the provider in config.yml";
        } else {
            why = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        log.warning("AI request failed (" + why + "). Trying again in " + formatWait(wait) + ".");
    }

    /** 1, 2, 4, 8 ... minutes after the 1st, 2nd, 3rd ... failure in a row, never more than 15. */
    static long backoffSeconds(int failuresInARow) {
        int exponent = Math.min(Math.max(failuresInARow - 1, 0), 10);
        return Math.min(MAX_BACKOFF_SECONDS, 60L << exponent);
    }

    private static String formatWait(long seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + " min" : seconds + " s";
    }

    // ---- admin / player actions ----

    /** Quiet for the given time: chat is not collected, the queue is dropped. */
    public void sleep(Duration duration) {
        sleepUntil = clock.getAsLong() + duration.toMillis();
        buffer.clear();
        eventLines.clear();
    }

    /** @return false when the bot was not sleeping */
    public boolean wake() {
        boolean was = clock.getAsLong() < sleepUntil;
        sleepUntil = 0;
        return was;
    }

    public boolean isMuted(UUID player) {
        return rt.settings.features().optOut().enabled() && optOut.isMuted(player);
    }

    public MuteResult mute(UUID player, String name) {
        if (!rt.settings.features().optOut().enabled()) return MuteResult.DISABLED;
        if (!optOut.setMuted(player, true)) return MuteResult.ALREADY;
        buffer.removeFrom(name);
        pendingNotes.removeIf(l -> l.player().equalsIgnoreCase(name));
        // a player who muted the bot is no longer known to it
        relationships.forget(player);
        LongTermMemory ltm = longMemory;
        if (ltm != null && ltm.config().deleteOnMute()) {
            ltm.forget(player).exceptionally(e -> {
                log.warning("Long-term memory: cannot delete the notes of a muted player (" + e.getClass().getSimpleName() + ")");
                return 0;
            });
        }
        scheduler.runAsync(optOut::flush);
        return MuteResult.DONE;
    }

    public MuteResult unmute(UUID player) {
        if (!rt.settings.features().optOut().enabled()) return MuteResult.DISABLED;
        if (!optOut.setMuted(player, false)) return MuteResult.ALREADY;
        scheduler.runAsync(optOut::flush);
        return MuteResult.DONE;
    }

    // ---- relationships (nickname, forgetting) ----

    public boolean relationshipsEnabled() {
        return rt.settings.features().awareness().relationships().enabled();
    }

    /** Sets or, with a blank text, clears the nickname the bot may use for this player. */
    public NicknameResult setNickname(UUID player, String name, String raw) {
        Relationships.Config rc = rt.settings.features().awareness().relationships();
        if (!rc.enabled() || !rc.nicknames()) return NicknameResult.DISABLED;
        if (isMuted(player)) return NicknameResult.MUTED;
        long now = clock.getAsLong();
        if (raw.isBlank()) {
            relationships.setNickname(player, name, null, now);
            return NicknameResult.CLEARED;
        }
        String nick = Relationships.sanitizeNickname(raw);
        if (nick == null) return NicknameResult.INVALID;
        // the bot may repeat it in public chat, so it has to pass the same filter as the bot's own answers
        if (!rt.filter.apply(nick).filter(nick::equals).isPresent()) return NicknameResult.REJECTED;
        relationships.setNickname(player, name, nick, now);
        return NicknameResult.SET;
    }

    /** Forgets what the bot knows about the player. False (and nothing happens) when relationships are switched off. */
    public boolean forgetRelationship(UUID player) {
        if (!relationshipsEnabled()) return false;
        relationships.forget(player);
        flushRelationships();
        return true;
    }

    /** Forgets the players with this name; returns how many were known. */
    public int forgetRelationshipsByName(String name) {
        if (!relationshipsEnabled()) return 0;
        List<UUID> ids = relationships.findByName(name);
        ids.forEach(relationships::forget);
        if (!ids.isEmpty()) flushRelationships();
        return ids.size();
    }

    /** Forgets everybody; returns how many were known (0 when relationships are off). */
    public int forgetAllRelationships() {
        if (!relationshipsEnabled()) return 0;
        int n = relationships.forgetAll();
        flushRelationships();
        return n;
    }

    /** What the bot knows about this player (for /aifriend nickname and %aifriend_relationship%). */
    public Relationships.View relationshipOf(UUID player) {
        return relationships.view(player, clock.getAsLong(), rt.settings.features().awareness().relationships());
    }

    /** The mood of the bot, or empty when the mood feature is off. */
    public Optional<Mood.Level> mood() {
        Runtime r = rt;
        return r.mood.enabled() ? Optional.of(r.mood.level(clock.getAsLong())) : Optional.empty();
    }

    public int moodScore() {
        return rt.mood.score(clock.getAsLong());
    }

    /** Writes pending relationship changes to the database in the background. */
    public void flushRelationships() {
        scheduler.runAsync(() -> {
            try {
                relationships.flush();
            } catch (java.sql.SQLException | RuntimeException e) {
                log.warning("Relationships: could not save to the database (" + e.getClass().getSimpleName() + ")");
            }
        });
    }

    // ---- read-only views ----

    public CostTracker.Totals costTotals() {
        return rt.cost.totals(Instant.ofEpochMilli(clock.getAsLong()));
    }

    public boolean costEnabled() {
        return rt.settings.features().cost().enabled();
    }

    public RateLimiter.Remaining remaining(UUID player) {
        return rt.limiter.remaining(player, Instant.ofEpochMilli(clock.getAsLong()));
    }

    private String longMemoryText() {
        LongTermMemory ltm = longMemory;
        if (ltm != null) return "on, " + ltm.mode();
        return rt.settings.features().longMemory().enabled() ? "enabled but the database is not available" : "off";
    }

    public Status status() {
        Runtime r = rt;
        Settings s = r.settings;
        long now = clock.getAsLong();
        State state;
        long wait = 0;
        if (!s.enabled()) {
            state = State.DISABLED;
        } else if (r.provider.isEmpty()) {
            state = State.NO_KEY;
        } else if (now < sleepUntil) {
            state = State.SLEEPING;
            wait = (sleepUntil - now + 999) / 1000;
        } else if (now < retryAt) {
            state = State.BACKOFF;
            wait = (retryAt - now + 999) / 1000;
        } else {
            state = State.RUNNING;
        }
        String persona = !s.features().personas().enabled() ? "-" : s.features().personas().personas().size() + " configured";
        return new Status(state, wait, failures, buffer.size(), r.provider.map(AiProvider::id).orElse("-"),
                r.memory.size(), optOut.mutedCount(), persona, longMemoryText());
    }
}
