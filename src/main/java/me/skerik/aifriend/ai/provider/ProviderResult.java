package me.skerik.aifriend.ai.provider;

/** The answer text plus usage as reported by the API (token counts are 0 when unknown). */
public record ProviderResult(String text, int inputTokens, int outputTokens, String model) {
}
