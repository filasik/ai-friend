package me.skerik.aifriend.ai;

import org.jspecify.annotations.Nullable;

/** One chat message waiting for the next AI request. */
public record ChatLine(String player, String text) {

    /** Longest part of a single chat line that is kept (a player cannot flood the prompt with one message). */
    public static final int MAX_TEXT = 300;

    /** Normalizes whitespace and cuts the text; blank input gives {@code null}. */
    public static @Nullable ChatLine of(String player, String text) {
        String clean = Text.singleLine(text);
        if (clean.isEmpty()) return null;
        return new ChatLine(player, Text.cut(clean, MAX_TEXT));
    }
}
