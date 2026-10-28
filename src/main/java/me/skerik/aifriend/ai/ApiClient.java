package me.skerik.aifriend.ai;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import me.skerik.aifriend.config.Settings;

/**
 * The only class that talks to the AI proxy (https://gpt.netuj.eu). Timeouts come from config.yml, the body is built
 * with Gson (no string templates), and nothing here runs on the main thread. The wire format is the one the proxy
 * already speaks, it cannot be changed from the plugin.
 */
public final class ApiClient implements Transport {

    public static final URI ENDPOINT = URI.create("https://gpt.netuj.eu/api/ai_friend.php");

    private final Settings settings;
    private final String userAgent;
    private final HttpClient http;
    private volatile String serverAddress;

    public ApiClient(Settings settings, String pluginVersion) {
        this.settings = settings;
        this.userAgent = "Mozilla/5.0 (compatible; AIFriend/" + pluginVersion + ")";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String send(String prompt) throws IOException, InterruptedException {
        JsonObject json = new JsonObject();
        json.addProperty("message", prompt);
        json.addProperty("api_key", settings.apiKey());
        json.addProperty("gpt_version", settings.model());
        json.addProperty("server_ip", serverAddress());
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(settings.readTimeoutSeconds()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), "HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Resolved lazily on the first request (async thread): the lookup can block and must not run on the main thread. */
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
