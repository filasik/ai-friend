package me.skerik.aifriend.ai.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.skerik.aifriend.TestSettings;
import me.skerik.aifriend.longmemory.Embedder;
import me.skerik.aifriend.longmemory.LongTermMemory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EmbedderTest {

    private static final Logger LOG = Logger.getLogger("EmbedderTest");

    static {
        LOG.setLevel(Level.OFF);
    }

    @Test
    void openAiRequestShapeAndResponse() throws Exception {
        try (TestServer server = new TestServer()) {
            server.response = "{\"data\":[{\"embedding\":[0.5,-0.25,1.0]}],\"model\":\"m\"}";
            Embedder e = new OpenAiEmbedder("openai", server.baseUrl() + "/v1", "sk-secret-key-123456", "text-embedding-3-small",
                    TestServer.options(Duration.ofSeconds(2)));
            assertArrayEquals(new float[]{0.5f, -0.25f, 1.0f}, e.embed("hello"));
            assertEquals("/v1/embeddings", server.path);
            assertEquals("text-embedding-3-small", server.json().get("model").getAsString());
            assertEquals("hello", server.json().get("input").getAsString());
            assertEquals("Bearer sk-secret-key-123456", server.headers.get("Authorization"));
        }
    }

    @Test
    void openAiCompatibleServerWithoutKeySendsNoAuthorization() throws Exception {
        try (TestServer server = new TestServer()) {
            server.response = "{\"data\":[{\"embedding\":[1,2]}]}";
            new OpenAiEmbedder("custom", server.baseUrl() + "/v1/", "", "local-model", TestServer.options(Duration.ofSeconds(2))).embed("x");
            assertFalse(server.headers.containsKey("Authorization"));
            assertEquals("/v1/embeddings", server.path);
        }
    }

    @Test
    void ollamaRequestShapeAndResponse() throws Exception {
        try (TestServer server = new TestServer()) {
            server.response = "{\"model\":\"nomic-embed-text\",\"embeddings\":[[0.1,0.2,0.3]]}";
            Embedder e = new OllamaEmbedder(server.baseUrl(), "nomic-embed-text", TestServer.options(Duration.ofSeconds(2)));
            assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, e.embed("hi"));
            assertEquals("/api/embed", server.path);
            assertEquals("nomic-embed-text", server.json().get("model").getAsString());
            assertEquals("hi", server.json().get("input").getAsString());
            assertEquals("nomic-embed-text", e.model());
        }
    }

    @Test
    void errorsAreIOExceptionsWithoutTheKey() throws Exception {
        try (TestServer server = new TestServer()) {
            server.status = 401;
            server.response = "{\"error\":{\"message\":\"Incorrect API key provided: sk-secret-key-123456\"}}";
            Embedder e = new OpenAiEmbedder("openai", server.baseUrl(), "sk-secret-key-123456", "m", TestServer.options(Duration.ofSeconds(2)));
            IOException ex = assertThrows(IOException.class, () -> e.embed("x"));
            assertFalse(String.valueOf(ex.getMessage()).contains("sk-secret-key-123456"), ex.getMessage());
            server.status = 200;
            server.response = "{\"data\":[]}";
            assertThrows(IOException.class, () -> e.embed("x"));
            server.response = "not json";
            assertThrows(IOException.class, () -> e.embed("x"));
        }
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        try (TestServer server = new TestServer()) {
            server.status = 302;
            Embedder e = new OllamaEmbedder(server.baseUrl(), "m", TestServer.options(Duration.ofSeconds(2)));
            assertThrows(IOException.class, () -> e.embed("x"));
            assertEquals(1, server.hits.get());
        }
    }

    private static LongTermMemory.Config backend(String name, String model, String baseUrl) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("enabled: true\nembeddings:\n  backend: " + name + "\n  model: \"" + model + "\"\n  base-url: \"" + baseUrl + "\"\n");
        return LongTermMemory.Config.load(y, LOG);
    }

    private static HttpOptions http() {
        return TestServer.options(Duration.ofSeconds(2));
    }

    @Test
    void factoryPicksTheEmbedderFromTheProviderEntry() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                providers:
                  openai:
                    api-key: sk-abcdefghijkl
                  custom:
                    base-url: http://127.0.0.1:1234/v1
                    model: chat
                  ollama: {}
                  anthropic:
                    api-key: sk-ant-abcdefghijkl
                """);
        var provider = ProviderSettings.load(y, LOG);
        TestSettings t = TestSettings.defaults();
        t.provider = provider;

        t.longMemory(backend("none", "", ""));
        assertTrue(EmbedderFactory.create(t.build(), http(), LOG).isEmpty());

        t.longMemory(backend("openai", "", ""));
        assertEquals("text-embedding-3-small", EmbedderFactory.create(t.build(), http(), LOG).orElseThrow().model());

        t.longMemory(backend("custom", "bge-small", ""));
        assertEquals("bge-small", EmbedderFactory.create(t.build(), http(), LOG).orElseThrow().model());

        t.longMemory(backend("ollama", "", "http://127.0.0.1:9"));
        assertEquals("nomic-embed-text", EmbedderFactory.create(t.build(), http(), LOG).orElseThrow().model());

        // no embeddings API / unknown / missing key: keyword fallback
        t.longMemory(backend("anthropic", "", ""));
        assertTrue(EmbedderFactory.create(t.build(), http(), LOG).isEmpty());
        t.longMemory(backend("nonsense", "", ""));
        assertTrue(EmbedderFactory.create(t.build(), http(), LOG).isEmpty());
        t.provider = ProviderSettings.defaults();
        t.longMemory(backend("openai", "", ""));
        assertTrue(EmbedderFactory.create(t.build(), http(), LOG).isEmpty());
    }
}
