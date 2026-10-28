package me.skerik.aifriend.ai;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.Scheduler;
import me.skerik.aifriend.config.Settings;
import org.jspecify.annotations.Nullable;

/**
 * The chat loop: every {@code interval-seconds} an async timer takes the queued chat lines, asks the AI and hands the
 * answer to the {@link Broadcaster}. Failures never escape the timer; they only stretch the pause (1, 2, 4 ... max 15
 * min). {@link #tick()} is plain synchronous code so it can be tested without a server.
 */
public final class ChatBot {

    /** Posts the bot's answer in public chat; the implementation moves to the right thread. */
    @FunctionalInterface
    public interface Broadcaster {
        void say(String botName, String text);
    }

    /** What /aifriend status shows. */
    public enum State { RUNNING, DISABLED, NO_KEY, SLEEPING, BACKOFF }

    public record Status(State state, long waitSeconds, int failures, int queued) {
    }

    static final long MAX_BACKOFF_SECONDS = 15 * 60;
    /** How many different players must take part before a "sleep" answer is believed. */
    static final int MIN_PLAYERS_FOR_SLEEP = 2;

    private final Logger log;
    private final Scheduler scheduler;
    private final Function<Settings, Transport> transportFactory;
    private final Broadcaster broadcaster;
    private final IntSupplier onlinePlayers;
    private final LongSupplier clock;
    private final ChatBuffer buffer;
    private final AtomicBoolean busy = new AtomicBoolean();

    private volatile Settings settings;
    private volatile Transport transport;
    private volatile @Nullable Runnable cancelTimer;
    private volatile long sleepUntil;
    private volatile long retryAt;
    private volatile int failures;
    private volatile String lastReply = "";

    public ChatBot(Logger log, Scheduler scheduler, Function<Settings, Transport> transportFactory,
                   Broadcaster broadcaster, IntSupplier onlinePlayers, LongSupplier clock, Settings settings) {
        this.log = log;
        this.scheduler = scheduler;
        this.transportFactory = transportFactory;
        this.broadcaster = broadcaster;
        this.onlinePlayers = onlinePlayers;
        this.clock = clock;
        this.settings = settings;
        this.transport = transportFactory.apply(settings);
        this.buffer = new ChatBuffer(settings.maxQueuedMessages());
    }

    /** (Re)starts the timer with the current settings. */
    public synchronized void start() {
        stop();
        long ticks = settings.intervalSeconds() * 20L;
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
        settings = newSettings;
        transport = transportFactory.apply(newSettings);
        buffer.setCapacity(newSettings.maxQueuedMessages());
        sleepUntil = 0;
        retryAt = 0;
        failures = 0;
        start();
    }

    /** Called by the chat listener for every public chat message. */
    public void onChat(String player, String text) {
        if (!listening()) return;
        ChatLine line = ChatLine.of(player, text);
        if (line != null) buffer.add(line);
    }

    /** True while chat lines are worth keeping: bot on, key set, not asleep. */
    private boolean listening() {
        Settings s = settings;
        return s.enabled() && s.hasApiKey() && clock.getAsLong() >= sleepUntil;
    }

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
        Settings s = settings;
        long now = clock.getAsLong();
        if (!s.enabled() || !s.hasApiKey() || now < sleepUntil || now < retryAt) return;
        if (onlinePlayers.getAsInt() == 0) return;
        List<ChatLine> batch = buffer.snapshot();
        if (batch.isEmpty()) return;

        String prompt = PromptBuilder.build(s, batch, lastReply);
        Reply reply;
        try {
            reply = ReplyParser.parse(transport.send(prompt), s.chatbotName(), s.maxReplyLength());
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
        switch (reply.action()) {
            case SAY -> {
                lastReply = reply.text();
                broadcaster.say(s.chatbotName(), reply.text());
            }
            case SLEEP -> maybeSleep(s, batch, now);
            case NONE -> log.fine("The AI service returned nothing to say");
        }
    }

    private void maybeSleep(Settings s, List<ChatLine> batch, long now) {
        int needed = Math.min(MIN_PLAYERS_FOR_SLEEP, Math.max(1, onlinePlayers.getAsInt()));
        if (!s.stopWhenIgnored() || distinctPlayers(batch) < needed) {
            log.fine("Ignored a request to stay quiet (not enough different players in the chat)");
            return;
        }
        sleepUntil = now + s.sleepMinutes() * 60_000L;
        buffer.clear();
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
            why = "HTTP 401, the api-key was rejected" + (settings.apiKey().equals("trial")
                    ? " (the trial has probably expired; get a key at https://platform.openai.com/signup)"
                    : "; check api-key in config.yml");
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

    public Status status() {
        Settings s = settings;
        long now = clock.getAsLong();
        State state;
        long wait = 0;
        if (!s.enabled()) {
            state = State.DISABLED;
        } else if (!s.hasApiKey()) {
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
        return new Status(state, wait, failures, buffer.size());
    }
}
