package me.skerik.aifriend.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import me.skerik.aifriend.ai.ApiException;
import me.skerik.aifriend.ai.provider.ChatMessage.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpProvidersTest {

    private static final String KEY = "sk-test-SECRETKEY1234567890";
    private static final HttpOptions OPTS = TestServer.options(Duration.ofSeconds(5));
    private static final ProviderRequest REQ = new ProviderRequest(List.of(
            new ChatMessage(Role.SYSTEM, "be brief"),
            new ChatMessage(Role.USER, "hello"),
            new ChatMessage(Role.ASSISTANT, "hi"),
            new ChatMessage(Role.USER, "again")), 123, true);

    private TestServer server;

    @BeforeEach
    void start() throws IOException {
        server = new TestServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    // --- proxy ---

    @Test
    void proxyFlattensSystemFirstAndReturnsRawBody() throws Exception {
        server.response = "{\"action\":\"say\",\"text\":\"x\"}";
        var p = new ProxyProvider(java.net.URI.create(server.baseUrl() + "/api/ai_friend.php"), KEY, "gpt-4o-mini", OPTS);
        ProviderResult r = p.complete(new ProviderRequest(List.of(
                new ChatMessage(Role.USER, "u1"), new ChatMessage(Role.SYSTEM, "s1")), 50, true));
        assertEquals("{\"action\":\"say\",\"text\":\"x\"}", r.text());
        assertEquals(0, r.inputTokens());
        assertEquals("/api/ai_friend.php", server.path);
        JsonObject o = server.json();
        assertEquals("s1\n\nu1", o.get("message").getAsString());
        assertEquals(KEY, o.get("api_key").getAsString());
        assertEquals("gpt-4o-mini", o.get("gpt_version").getAsString());
        assertTrue(o.has("server_ip"));
        assertEquals(4, o.size(), "same four fields as ApiClient");
        assertEquals("application/json; charset=utf-8", server.headers.get("Content-Type"));
        assertEquals("test-agent", server.headers.get("User-Agent"));
    }

    @Test
    void proxySingleMessageIsSentUnchanged() throws Exception {
        var p = new ProxyProvider(java.net.URI.create(server.baseUrl()), KEY, "m", OPTS);
        p.complete(new ProviderRequest(List.of(new ChatMessage(Role.USER, "just the prompt")), 10, false));
        assertEquals("just the prompt", server.json().get("message").getAsString());
    }

    // --- openai / custom ---

    @Test
    void openAiRequestShapeAndUsage() throws Exception {
        server.response = """
                {"model":"gpt-4o-mini-2024","choices":[{"message":{"role":"assistant","content":"{\\"action\\":\\"say\\"}"}}],
                 "usage":{"prompt_tokens":11,"completion_tokens":7}}""";
        var p = new OpenAiProvider("openai", server.baseUrl() + "/v1/", KEY, "gpt-4o-mini", false, OPTS);
        ProviderResult r = p.complete(REQ);
        assertEquals("/v1/chat/completions", server.path);
        assertEquals("Bearer " + KEY, server.headers.get("Authorization"));
        JsonObject o = server.json();
        assertEquals("gpt-4o-mini", o.get("model").getAsString());
        assertEquals(123, o.get("max_completion_tokens").getAsInt());
        assertFalse(o.has("max_tokens"));
        assertEquals("json_object", o.getAsJsonObject("response_format").get("type").getAsString());
        var msgs = o.getAsJsonArray("messages");
        assertEquals(4, msgs.size());
        assertEquals("system", msgs.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("assistant", msgs.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("again", msgs.get(3).getAsJsonObject().get("content").getAsString());
        assertEquals("{\"action\":\"say\"}", r.text());
        assertEquals(11, r.inputTokens());
        assertEquals(7, r.outputTokens());
        assertEquals("gpt-4o-mini-2024", r.model());
    }

    @Test
    void openAiWithoutJsonModeOmitsResponseFormat() throws Exception {
        server.response = "{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}";
        var p = new OpenAiProvider("openai", server.baseUrl(), KEY, "m", false, OPTS);
        ProviderResult r = p.complete(new ProviderRequest(REQ.messages(), 10, false));
        assertFalse(server.json().has("response_format"));
        assertEquals(0, r.inputTokens());
        assertEquals("m", r.model());
    }

    @Test
    void customEndpointUsesLegacyMaxTokensAndNoAuthWithoutKey() throws Exception {
        server.response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        var p = new OpenAiProvider("custom", server.baseUrl(), "", "local-model", true, OPTS);
        assertEquals("custom", p.id());
        p.complete(REQ);
        assertEquals(123, server.json().get("max_tokens").getAsInt());
        assertFalse(server.json().has("max_completion_tokens"));
        assertFalse(server.headers.containsKey("Authorization"));
    }

    @Test
    void openAiMalformedAnswersAreIoExceptions() {
        var p = new OpenAiProvider("openai", server.baseUrl(), KEY, "m", false, OPTS);
        for (String bad : List.of("not json", "[]", "{}", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":null}}]}")) {
            server.response = bad;
            assertThrows(IOException.class, () -> p.complete(REQ), bad);
        }
    }

    // --- anthropic ---

    @Test
    void anthropicRequestShapeAndUsage() throws Exception {
        server.response = """
                {"model":"claude-haiku-5-5","content":[{"type":"text","text":"Hel"},{"type":"thinking","thinking":"x"},
                 {"type":"text","text":"lo"}],"usage":{"input_tokens":20,"output_tokens":5}}""";
        var p = new AnthropicProvider(server.baseUrl(), KEY, "claude-haiku-5-5", OPTS);
        ProviderResult r = p.complete(new ProviderRequest(List.of(
                new ChatMessage(Role.SYSTEM, "sys A"), new ChatMessage(Role.SYSTEM, "sys B"),
                new ChatMessage(Role.USER, "one"), new ChatMessage(Role.USER, "two"),
                new ChatMessage(Role.ASSISTANT, "reply"), new ChatMessage(Role.USER, "three")), 99, true));
        assertEquals("/v1/messages", server.path);
        assertEquals(KEY, server.headers.get("x-api-key"));
        assertEquals("2023-06-01", server.headers.get("anthropic-version"));
        assertFalse(server.headers.containsKey("Authorization"));
        JsonObject o = server.json();
        assertEquals("sys A\n\nsys B", o.get("system").getAsString());
        assertEquals(99, o.get("max_tokens").getAsInt());
        assertFalse(o.has("response_format"));
        var msgs = o.getAsJsonArray("messages");
        assertEquals(3, msgs.size(), "consecutive user messages are merged");
        assertEquals("one\n\ntwo", msgs.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", msgs.get(1).getAsJsonObject().get("role").getAsString());
        for (var m : msgs) assertFalse("system".equals(m.getAsJsonObject().get("role").getAsString()));
        assertEquals("Hello", r.text());
        assertEquals(20, r.inputTokens());
        assertEquals(5, r.outputTokens());
    }

    @Test
    void anthropicSystemOnlyBecomesUserTurn() throws Exception {
        server.response = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
        new AnthropicProvider(server.baseUrl(), KEY, "m", OPTS)
                .complete(new ProviderRequest(List.of(new ChatMessage(Role.SYSTEM, "only")), 10, false));
        JsonObject o = server.json();
        assertFalse(o.has("system"));
        assertEquals("only", o.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void anthropicNoTextBlockIsIoException() {
        server.response = "{\"content\":[{\"type\":\"tool_use\"}]}";
        var p = new AnthropicProvider(server.baseUrl(), KEY, "m", OPTS);
        assertThrows(IOException.class, () -> p.complete(REQ));
    }

    // --- ollama ---

    @Test
    void ollamaRequestShapeAndUsage() throws Exception {
        server.response = "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\"yo\"},"
                + "\"prompt_eval_count\":9,\"eval_count\":3,\"done\":true}";
        var p = new OllamaProvider(server.baseUrl(), "llama3.2", OPTS);
        ProviderResult r = p.complete(REQ);
        assertEquals("/api/chat", server.path);
        assertFalse(server.headers.containsKey("Authorization"));
        JsonObject o = server.json();
        assertFalse(o.get("stream").getAsBoolean());
        assertEquals("json", o.get("format").getAsString());
        assertEquals(123, o.getAsJsonObject("options").get("num_predict").getAsInt());
        assertEquals("system", o.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("yo", r.text());
        assertEquals(9, r.inputTokens());
        assertEquals(3, r.outputTokens());
    }

    // --- errors, timeouts, secrets ---

    private List<AiProvider> all() {
        return List.of(
                new ProxyProvider(java.net.URI.create(server.baseUrl()), KEY, "m", OPTS),
                new OpenAiProvider("openai", server.baseUrl(), KEY, "m", false, OPTS),
                new AnthropicProvider(server.baseUrl(), KEY, "m", OPTS),
                new OllamaProvider(server.baseUrl(), "m", OPTS));
    }

    @Test
    void httpErrorsBecomeApiExceptionWithStatus() {
        for (int status : new int[] {401, 429, 500, 302}) {
            server.status = status;
            server.response = "<html>nope</html>";
            for (AiProvider p : all()) {
                ApiException e = assertThrows(ApiException.class, () -> p.complete(REQ), p.id());
                assertEquals(status, e.status());
                assertEquals("HTTP " + status, e.getMessage());
            }
        }
    }

    @Test
    void apiErrorTextIsIncludedAndKeyIsScrubbed() {
        server.status = 401;
        server.response = "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + ". Check it.\","
                + "\"type\":\"invalid_request_error\"}}";
        for (AiProvider p : all()) {
            ApiException e = assertThrows(ApiException.class, () -> p.complete(REQ), p.id());
            assertTrue(e.getMessage().startsWith("HTTP 401: Incorrect API key provided: ***"), e.getMessage());
            assertFalse(e.getMessage().contains("SECRETKEY"));
        }
        server.response = "{\"error\":\"model 'x' not found\"}"; // Ollama style
        ApiException e = assertThrows(ApiException.class, () -> all().get(3).complete(REQ));
        assertEquals("HTTP 401: model 'x' not found", e.getMessage());
    }

    @Test
    void keyShapedTextIsScrubbedEvenWhenItIsNotOurKey() {
        server.status = 401;
        server.response = "{\"error\":{\"message\":\"Incorrect API key provided: sk-proj-abcdef123456\"}}";
        ApiException e = assertThrows(ApiException.class, () -> all().get(1).complete(REQ));
        assertFalse(e.getMessage().contains("abcdef"));
    }

    @Test
    void longErrorTextIsTruncated() {
        server.status = 500;
        server.response = "{\"error\":\"" + "x".repeat(5000) + "\"}";
        ApiException e = assertThrows(ApiException.class, () -> all().get(0).complete(REQ));
        assertTrue(e.getMessage().length() < 260);
    }

    @Test
    void readTimeoutIsReportedWithoutKey() throws Exception {
        server.delayMillis = 1500;
        server.response = "{}";
        var fast = TestServer.options(Duration.ofMillis(300));
        List<AiProvider> providers = List.of(
                new ProxyProvider(java.net.URI.create(server.baseUrl()), KEY, "m", fast),
                new OpenAiProvider("openai", server.baseUrl(), KEY, "m", false, fast),
                new AnthropicProvider(server.baseUrl(), KEY, "m", fast),
                new OllamaProvider(server.baseUrl(), "m", fast));
        for (AiProvider p : providers) {
            HttpTimeoutException e = assertThrows(HttpTimeoutException.class, () -> p.complete(REQ), p.id());
            assertNull(e.getCause());
            assertFalse(stack(e).contains("SECRETKEY"));
        }
    }

    @Test
    void connectionRefusedHasNoKeyAndNoCause() throws Exception {
        String url = server.baseUrl();
        server.close();
        for (AiProvider p : List.of(
                new OpenAiProvider("openai", url, KEY, "m", false, OPTS),
                new AnthropicProvider(url, KEY, "m", OPTS),
                new ProxyProvider(java.net.URI.create(url), KEY, "m", OPTS))) {
            IOException e = assertThrows(IOException.class, () -> p.complete(REQ));
            assertFalse(stack(e).contains("SECRETKEY"));
            assertNull(e.getCause());
        }
        server = new TestServer(); // for @AfterEach
    }

    private static String stack(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }
}
