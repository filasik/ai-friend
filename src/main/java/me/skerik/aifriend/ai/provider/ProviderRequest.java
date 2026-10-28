package me.skerik.aifriend.ai.provider;

import java.util.List;

/**
 * What the bot asks a provider. {@code jsonReply} asks the provider to answer with a JSON object where the API has a
 * switch for it (the prompt must still describe the format).
 */
public record ProviderRequest(List<ChatMessage> messages, int maxOutputTokens, boolean jsonReply) {

    public ProviderRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
        if (maxOutputTokens < 1) throw new IllegalArgumentException("maxOutputTokens must be positive");
    }
}
