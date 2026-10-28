package me.skerik.aifriend.ai.provider;

import java.util.Objects;

/** One message of a conversation sent to a provider. */
public record ChatMessage(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
