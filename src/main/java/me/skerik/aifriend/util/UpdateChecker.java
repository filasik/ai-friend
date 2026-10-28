package me.skerik.aifriend.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Looks at the latest GitHub release (async, no auth, no personal data sent) and remembers the result. */
public final class UpdateChecker {

    private static final URI LATEST = URI.create("https://api.github.com/repos/filasik/ai-friend/releases/latest");

    private final String current;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private volatile @Nullable String newer;
    private volatile @Nullable String url;

    public UpdateChecker(String currentVersion, Logger log) {
        this.current = currentVersion;
        this.log = log;
    }

    /** Blocking; call from an async task only. */
    public void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(LATEST).timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AI Friend/" + current).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.fine("Update check: HTTP " + response.statusCode());
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String tag = json.get("tag_name").getAsString();
            if (isNewer(tag, current)) {
                newer = tag.startsWith("v") ? tag.substring(1) : tag;
                url = json.has("html_url") ? json.get("html_url").getAsString() : null;
                log.info("A new version is available: " + newer + " (running " + current + ")" + (url == null ? "" : " " + url));
            } else {
                newer = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.fine("Update check failed: " + e);
        }
    }

    public @Nullable String newerVersion() {
        return newer;
    }

    public @Nullable String url() {
        return url;
    }

    /** Numeric comparison of dotted versions; "v", "-SNAPSHOT" and other suffixes are ignored. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = numbers(candidate);
        int[] b = numbers(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] numbers(String version) {
        String core = version.strip().replaceFirst("^[vV]", "").split("[-+]")[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
