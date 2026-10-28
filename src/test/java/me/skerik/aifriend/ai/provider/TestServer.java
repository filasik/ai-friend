package me.skerik.aifriend.ai.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Local HTTP server on an ephemeral port that records the last request and plays back a canned answer. */
final class TestServer implements AutoCloseable {

    final HttpServer server;
    volatile int status = 200;
    volatile String response = "{}";
    volatile long delayMillis;
    volatile String path;
    volatile String body;
    volatile Map<String, String> headers = Map.of();
    final AtomicInteger hits = new AtomicInteger();

    TestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            path = exchange.getRequestURI().getPath();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> h.put(k, v.getFirst()));
            headers = h;
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, out.length);
                exchange.getResponseBody().write(out);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    JsonObject json() {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    static HttpOptions options(Duration read) {
        return new HttpOptions(Duration.ofSeconds(2), read, "test-agent");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
