package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import me.skerik.aifriend.ai.ApiException;

/**
 * Shared HTTP plumbing. Redirects are never followed (a key must not travel to another host). Every message that
 * leaves this class is sanitized: the configured secret and anything shaped like a key is replaced by {@code ***},
 * and low-level exceptions are rethrown without their cause.
 */
final class HttpSupport {

    private static final Pattern KEY_LIKE = Pattern.compile("(?i)\\b(sk|key|pk|api)[-_][A-Za-z0-9_\\-*.]{6,}");
    private static final int MAX_MESSAGE = 200;

    private final HttpClient http;
    private final HttpOptions options;
    private final String secret;

    HttpSupport(HttpOptions options, String secret) {
        this.options = options;
        this.secret = secret == null ? "" : secret;
        this.http = HttpClient.newBuilder()
                .connectTimeout(options.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    String postJson(URI uri, JsonObject body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(options.readTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", options.userAgent());
        headers.forEach(b::header);
        HttpRequest request = b.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new HttpTimeoutException(sanitize("Request timed out: " + e.getMessage()));
        } catch (IOException e) {
            throw new IOException(sanitize(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        int status = response.statusCode();
        if (status < 200 || status > 299) {
            String detail = errorDetail(response.body());
            throw new ApiException(status, "HTTP " + status + (detail == null ? "" : ": " + detail));
        }
        return response.body();
    }

    /** The API's own error text if the body is JSON with one (OpenAI/Anthropic/Ollama shapes), sanitized. */
    private String errorDetail(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            JsonElement err = o.get("error");
            String text = null;
            if (err != null && err.isJsonPrimitive()) text = err.getAsString();
            else if (err != null && err.isJsonObject()) text = string(err, "message");
            else if (o.has("message")) text = string(o, "message");
            return text == null || text.isBlank() ? null : sanitize(text);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }

    String sanitize(String text) {
        String s = text == null ? "" : text;
        if (!secret.isEmpty()) s = s.replace(secret, "***");
        s = KEY_LIKE.matcher(s).replaceAll("***").replaceAll("\\s+", " ").strip();
        return s.length() > MAX_MESSAGE ? s.substring(0, MAX_MESSAGE) + "..." : s;
    }

    static JsonObject parseObject(String body, String providerId) throws IOException {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Unexpected response from " + providerId);
        }
    }

    /** Walks object keys (String) and array indexes (Integer); null when anything on the way is missing. */
    static JsonElement path(JsonElement e, Object... keys) {
        JsonElement cur = e;
        for (Object key : keys) {
            if (cur == null) return null;
            if (key instanceof String k && cur.isJsonObject()) {
                cur = cur.getAsJsonObject().get(k);
            } else if (key instanceof Integer i && cur.isJsonArray() && i < cur.getAsJsonArray().size()) {
                cur = cur.getAsJsonArray().get(i);
            } else {
                return null;
            }
        }
        return cur == null || cur.isJsonNull() ? null : cur;
    }

    static String string(JsonElement e, Object... keys) {
        JsonElement v = path(e, keys);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : null;
    }

    static int integer(JsonElement e, Object... keys) {
        JsonElement v = path(e, keys);
        try {
            return v != null && v.isJsonPrimitive() ? Math.max(0, v.getAsInt()) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static URI join(String base, String suffix) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(b + suffix);
    }
}
