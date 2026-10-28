package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import me.skerik.aifriend.longmemory.Embedder;

/** Local Ollama embeddings ({@code POST {base}/api/embed}). No key. */
public final class OllamaEmbedder implements Embedder {

    public static final String DEFAULT_MODEL = "nomic-embed-text";

    private final HttpSupport http;
    private final URI endpoint;
    private final String model;

    public OllamaEmbedder(String baseUrl, String model, HttpOptions options) {
        this.endpoint = HttpSupport.join(baseUrl, "/api/embed");
        this.model = model;
        this.http = new HttpSupport(options, "");
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public float[] embed(String text) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        json.addProperty("model", model);
        json.addProperty("input", text);
        JsonObject answer = HttpSupport.parseObject(http.postJson(endpoint, json, Map.of()), "ollama");
        return OpenAiEmbedder.vector(HttpSupport.path(answer, "embeddings", 0), "ollama");
    }
}
