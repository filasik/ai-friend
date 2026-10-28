package me.skerik.aifriend.chat;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a request would do, for {@code /aifriend simulate}: nothing is posted to chat. ChatBot fills it in a dry run.
 *
 * @param persona      name of the persona that would be used, or null
 * @param request      the user part of the request that would be sent (chat lines, event lines)
 * @param historyTurns number of memory turns that would be sent along
 * @param queuedLines  chat lines waiting in the buffer
 * @param rawReply     the AI answer as received (null when no call was made)
 * @param filteredReply the answer after {@link ReplyFilter}, null when none remains or no call was made
 * @param blockers     everything that would stop a real request (empty = it would be sent)
 */
public record SimulationResult(@Nullable String persona, String request, int historyTurns, int queuedLines,
                               @Nullable String rawReply, @Nullable String filteredReply, List<Blocker> blockers) {

    public enum Blocker { DISABLED, NO_KEY, SLEEPING, BACKOFF, RATE_LIMITED, NO_CHAT, MUTED, FILTERED_OUT }

    public SimulationResult {
        blockers = List.copyOf(blockers);
    }

    /** True when nothing blocks the request. */
    public boolean wouldSend() {
        return blockers.isEmpty();
    }

    /** True when an answer would appear in chat. */
    public boolean wouldAnswer() {
        return wouldSend() && filteredReply != null;
    }

    /** Rough token estimate of the request (about 4 characters per token), good enough for a preview. */
    public int estimatedInputTokens() {
        return estimateTokens(request);
    }

    public static int estimateTokens(String text) {
        return (text.length() + 3) / 4;
    }
}
