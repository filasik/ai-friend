package me.skerik.aifriend.api;

import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * Fired right before the bot posts an answer in public chat. Listeners may change the text with {@link #setText} or
 * cancel the event to post nothing. <b>Asynchronous</b>: it is called off the main thread, so listeners must be
 * thread safe and must not touch the world. The final text is cleaned (no colour codes, one line) and cut to
 * {@code max-reply-length} again.
 */
public final class AiFriendResponseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** What caused the answer. */
    public enum Source { TIMED, DIRECT }

    private final String botName;
    private final @Nullable UUID asker;
    private final Source source;
    private String text;
    private boolean cancelled;

    public AiFriendResponseEvent(String botName, String text, @Nullable UUID asker, Source source) {
        super(true);
        this.botName = botName;
        this.text = text;
        this.asker = asker;
        this.source = source;
    }

    public String botName() {
        return botName;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /** The player who asked (/ai or a mention), null for the timed answer to the general chat. */
    public @Nullable UUID asker() {
        return asker;
    }

    public Source source() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
