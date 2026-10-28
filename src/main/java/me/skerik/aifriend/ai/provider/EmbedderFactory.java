package me.skerik.aifriend.ai.provider;

import java.util.Optional;
import java.util.logging.Logger;
import me.skerik.aifriend.ai.provider.ProviderSettings.Entry;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.longmemory.Embedder;
import me.skerik.aifriend.longmemory.LongTermMemory;

/** Builds the embedder named in {@code long-term-memory.embeddings.backend} from the matching provider entry. */
public final class EmbedderFactory {

    private EmbedderFactory() {
    }

    /** @return empty for "none" and for every backend that cannot embed (the reason is logged); keywords are used then */
    public static Optional<Embedder> create(Settings s, HttpOptions http, Logger log) {
        LongTermMemory.Embeddings e = s.features().longMemory().embeddings();
        if (e.backend().isEmpty() || e.backend().equals("none")) return Optional.empty();
        String path = "long-term-memory.embeddings.backend";
        Entry entry = s.provider().providers().get(e.backend());
        if (entry == null) {
            log.warning(path + " \"" + e.backend() + "\" is not a configured provider; using keyword matching for notes");
            return Optional.empty();
        }
        String base = e.baseUrl().isEmpty() ? entry.baseUrl() : e.baseUrl();
        switch (entry.type()) {
            case OPENAI, CUSTOM -> {
                if (entry.type() == ProviderSettings.Type.OPENAI && entry.apiKey().isEmpty()) {
                    log.warning(path + ": provider \"" + entry.name() + "\" has no api-key; using keyword matching for notes");
                    return Optional.empty();
                }
                if (base.isEmpty()) {
                    log.warning(path + ": provider \"" + entry.name() + "\" has no base-url; using keyword matching for notes");
                    return Optional.empty();
                }
                String model = e.model().isEmpty() ? OpenAiEmbedder.DEFAULT_MODEL : e.model();
                return Optional.of(new OpenAiEmbedder(entry.name(), base, entry.apiKey(), model, http));
            }
            case OLLAMA -> {
                String model = e.model().isEmpty() ? OllamaEmbedder.DEFAULT_MODEL : e.model();
                return Optional.of(new OllamaEmbedder(base, model, http));
            }
            default -> {
                log.warning(path + ": provider \"" + entry.name() + "\" (" + entry.type().name().toLowerCase(java.util.Locale.ROOT)
                        + ") has no embeddings API; using keyword matching for notes");
                return Optional.empty();
            }
        }
    }
}
