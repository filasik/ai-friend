package me.skerik.aifriend.api;

import java.time.Duration;

/**
 * Small public API for other plugins. Get it from the Bukkit services manager:
 * {@code Bukkit.getServicesManager().load(AiFriendApi.class)} (null until AI Friend has finished starting).
 */
public interface AiFriendApi {

    /** RUNNING, DISABLED, NO_KEY (no usable provider), SLEEPING or BACKOFF (waiting after an error). */
    State state();

    /** The last text the bot posted, empty before the first answer. */
    String lastMessage();

    /** Id of the provider that answers (for example {@code openai}), "-" when there is none. */
    String provider();

    /** Chat lines waiting for the next answer. */
    int queuedLines();

    /** Keep quiet for a while (same as /aifriend sleep). */
    void sleep(Duration duration);

    /** Wake the bot up; false if it was not sleeping. */
    boolean wake();

    enum State { RUNNING, DISABLED, NO_KEY, SLEEPING, BACKOFF }
}
