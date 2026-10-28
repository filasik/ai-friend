package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Anthropic Messages API ({@code POST {base}/v1/messages}). System messages become the top-level {@code system}
 * string; the rest must alternate user/assistant starting with user, so consecutive same-role messages are merged.
 * The API has no JSON-mode switch, so {@code jsonReply} is not sent (the prompt asks for the format).
 */
public final class AnthropicProvider implements AiProvider {

    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    public static final String API_VERSION = "2023-06-01";

    private final HttpSupport http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public AnthropicProvider(String baseUrl, String apiKey, String model, HttpOptions options) {
        this.endpoint = HttpSupport.join(baseUrl, "/v1/messages");
        this.apiKey = apiKey;
        this.model = model;
        this.http = new HttpSupport(options, apiKey);
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        List<String> system = new ArrayList<>();
        List<ChatMessage> turns = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (m.role() == ChatMessage.Role.SYSTEM) {
                system.add(m.content());
            } else if (!turns.isEmpty() && turns.getLast().role() == m.role()) {
                ChatMessage last = turns.removeLast();
                turns.add(new ChatMessage(m.role(), last.content() + "\n\n" + m.content()));
            } else {
                turns.add(m);
            }
        }
        // The API needs a first user turn: drop a leading assistant turn; if nothing is left, the system text becomes it
        while (!turns.isEmpty() && turns.getFirst().role() != ChatMessage.Role.USER) turns.removeFirst();
        if (turns.isEmpty()) {
            turns.add(new ChatMessage(ChatMessage.Role.USER, String.join("\n\n", system)));
            system.clear();
        }

        JsonObject json = new JsonObject();
        json.addProperty("model", model);
        json.addProperty("max_tokens", request.maxOutputTokens());
        if (!system.isEmpty()) json.addProperty("system", String.join("\n\n", system));
        JsonArray messages = new JsonArray();
        for (ChatMessage m : turns) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role().name().toLowerCase(Locale.ROOT));
            o.addProperty("content", m.content());
            messages.add(o);
        }
        json.add("messages", messages);

        JsonObject answer = HttpSupport.parseObject(
                http.postJson(endpoint, json, Map.of("x-api-key", apiKey, "anthropic-version", API_VERSION)), id());
        StringBuilder text = new StringBuilder();
        JsonElement content = answer.get("content");
        if (content != null && content.isJsonArray()) {
            for (JsonElement block : content.getAsJsonArray()) {
                if ("text".equals(HttpSupport.string(block, "type"))) {
                    String t = HttpSupport.string(block, "text");
                    if (t != null) text.append(t);
                }
            }
        }
        if (text.isEmpty()) throw new IOException("Unexpected response from anthropic: no text content");
        String usedModel = HttpSupport.string(answer, "model");
        return new ProviderResult(text.toString(), HttpSupport.integer(answer, "usage", "input_tokens"),
                HttpSupport.integer(answer, "usage", "output_tokens"), usedModel == null ? model : usedModel);
    }
}
