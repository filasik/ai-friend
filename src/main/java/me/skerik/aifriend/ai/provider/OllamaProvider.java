package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** Local Ollama ({@code POST {base}/api/chat}, {@code stream: false}). No key. */
public final class OllamaProvider implements AiProvider {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final HttpSupport http;
    private final URI endpoint;
    private final String model;

    public OllamaProvider(String baseUrl, String model, HttpOptions options) {
        this.endpoint = HttpSupport.join(baseUrl, "/api/chat");
        this.model = model;
        this.http = new HttpSupport(options, "");
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        json.addProperty("model", model);
        json.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        for (ChatMessage m : request.messages()) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role().name().toLowerCase(Locale.ROOT));
            o.addProperty("content", m.content());
            messages.add(o);
        }
        json.add("messages", messages);
        if (request.jsonReply()) json.addProperty("format", "json");
        JsonObject options = new JsonObject();
        options.addProperty("num_predict", request.maxOutputTokens());
        json.add("options", options);

        JsonObject answer = HttpSupport.parseObject(http.postJson(endpoint, json, Map.of()), id());
        String text = HttpSupport.string(answer, "message", "content");
        if (text == null) throw new IOException("Unexpected response from ollama: no message content");
        String usedModel = HttpSupport.string(answer, "model");
        return new ProviderResult(text, HttpSupport.integer(answer, "prompt_eval_count"),
                HttpSupport.integer(answer, "eval_count"), usedModel == null ? model : usedModel);
    }
}
