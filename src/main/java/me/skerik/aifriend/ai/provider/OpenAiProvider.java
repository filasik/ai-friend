package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI Chat Completions ({@code POST {base}/chat/completions}). Also serves any OpenAI-compatible endpoint
 * (OpenRouter, LM Studio, vLLM, ...): set {@code base-url} and use {@code legacyMaxTokens} for servers that do not
 * know {@code max_completion_tokens}. An empty key sends no Authorization header (local servers).
 */
public final class OpenAiProvider implements AiProvider {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final String id;
    private final HttpSupport http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final boolean legacyMaxTokens;

    public OpenAiProvider(String id, String baseUrl, String apiKey, String model, boolean legacyMaxTokens,
                          HttpOptions options) {
        this.id = id;
        this.endpoint = HttpSupport.join(baseUrl, "/chat/completions");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.legacyMaxTokens = legacyMaxTokens;
        this.http = new HttpSupport(options, this.apiKey);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        json.addProperty("model", model);
        JsonArray messages = new JsonArray();
        for (ChatMessage m : request.messages()) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role().name().toLowerCase(Locale.ROOT));
            o.addProperty("content", m.content());
            messages.add(o);
        }
        json.add("messages", messages);
        json.addProperty(legacyMaxTokens ? "max_tokens" : "max_completion_tokens", request.maxOutputTokens());
        if (request.jsonReply()) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            json.add("response_format", format);
        }
        Map<String, String> headers = apiKey.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
        JsonObject answer = HttpSupport.parseObject(http.postJson(endpoint, json, headers), id);
        String text = HttpSupport.string(answer, "choices", 0, "message", "content");
        if (text == null) throw new IOException("Unexpected response from " + id + ": no message content");
        String usedModel = HttpSupport.string(answer, "model");
        return new ProviderResult(text, HttpSupport.integer(answer, "usage", "prompt_tokens"),
                HttpSupport.integer(answer, "usage", "completion_tokens"), usedModel == null ? model : usedModel);
    }
}
