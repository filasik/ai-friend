package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import me.skerik.aifriend.longmemory.Embedder;

/** OpenAI-compatible embeddings ({@code POST {base}/embeddings}); also serves any {@code custom} base-url. */
public final class OpenAiEmbedder implements Embedder {

    public static final String DEFAULT_MODEL = "text-embedding-3-small";

    private final String id;
    private final HttpSupport http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiEmbedder(String id, String baseUrl, String apiKey, String model, HttpOptions options) {
        this.id = id;
        this.endpoint = HttpSupport.join(baseUrl, "/embeddings");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.http = new HttpSupport(options, this.apiKey);
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
        Map<String, String> headers = apiKey.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
        JsonObject answer = HttpSupport.parseObject(http.postJson(endpoint, json, headers), id);
        JsonElement vector = HttpSupport.path(answer, "data", 0, "embedding");
        return vector(vector, id);
    }

    static float[] vector(JsonElement element, String id) throws IOException {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
            throw new IOException("Unexpected response from " + id + ": no embedding");
        }
        JsonArray a = element.getAsJsonArray();
        float[] v = new float[a.size()];
        try {
            for (int i = 0; i < v.length; i++) {
                v[i] = a.get(i).getAsFloat();
                if (!Float.isFinite(v[i])) throw new IOException("Unexpected response from " + id + ": bad number");
            }
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            throw new IOException("Unexpected response from " + id + ": bad embedding");
        }
        return v;
    }
}
