package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.skerik.aifriend.ai.ApiClient;

/**
 * The original gpt.netuj.eu protocol. The proxy takes one {@code message}, so system and user/assistant messages are
 * flattened (system first, joined by a blank line). The raw body is returned as the answer text, exactly as
 * {@link ApiClient} did, so {@code ReplyParser} keeps working. Token usage is unknown (0).
 */
public final class ProxyProvider implements AiProvider {

    private final HttpSupport http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private volatile String serverAddress;

    public ProxyProvider(URI endpoint, String apiKey, String model, HttpOptions options) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.http = new HttpSupport(options, apiKey);
    }

    public ProxyProvider(String apiKey, String model, HttpOptions options) {
        this(ApiClient.ENDPOINT, apiKey, model, options);
    }

    @Override
    public String id() {
        return "proxy";
    }

    static String flatten(List<ChatMessage> messages) {
        List<String> parts = new ArrayList<>();
        for (ChatMessage m : messages) if (m.role() == ChatMessage.Role.SYSTEM) parts.add(m.content());
        for (ChatMessage m : messages) if (m.role() != ChatMessage.Role.SYSTEM) parts.add(m.content());
        return String.join("\n\n", parts);
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        json.addProperty("message", flatten(request.messages()));
        json.addProperty("api_key", apiKey);
        json.addProperty("gpt_version", model);
        json.addProperty("server_ip", serverAddress());
        String body = http.postJson(endpoint, json, Map.of());
        return new ProviderResult(body, 0, 0, model);
    }

    private String serverAddress() {
        String cached = serverAddress;
        if (cached == null) {
            try {
                cached = InetAddress.getLocalHost().toString();
            } catch (IOException e) {
                cached = "unknown";
            }
            serverAddress = cached;
        }
        return cached;
    }
}
