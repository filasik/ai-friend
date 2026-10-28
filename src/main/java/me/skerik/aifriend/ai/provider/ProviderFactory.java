package me.skerik.aifriend.ai.provider;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import me.skerik.aifriend.ai.provider.ProviderSettings.Entry;

/** Builds the provider chain: primary, then the failover list, optionally wrapped in a cache. */
public final class ProviderFactory {

    private ProviderFactory() {
    }

    /** @return empty when no provider in the chain is usable (missing key / model / URL); each reason was logged */
    public static Optional<AiProvider> create(ProviderSettings settings, HttpOptions http, Clock clock, Logger log) {
        // a config that still names the retired proxy stays inactive until the admin picks a provider (AIFriend logs it)
        if (settings.retiredProxy()) return Optional.empty();
        List<String> names = new ArrayList<>();
        names.add(settings.primary());
        names.addAll(settings.failover());

        List<AiProvider> chain = new ArrayList<>();
        for (String name : names) {
            AiProvider p = build(settings.providers().get(name), http, log);
            if (p != null) chain.add(p);
        }
        if (chain.isEmpty()) return Optional.empty();

        AiProvider result = chain.size() == 1 ? chain.getFirst()
                : new FailoverProvider(chain, Duration.ofSeconds(settings.failoverCooldownSeconds()), clock);
        if (settings.cacheEnabled()) {
            result = new CachingProvider(result, settings.cacheMaxEntries(),
                    Duration.ofMinutes(settings.cacheTtlMinutes()), clock);
        }
        return Optional.of(result);
    }

    /** A single provider, or null (with a logged reason) when it lacks something it needs. Never logs a key. */
    static AiProvider build(Entry e, HttpOptions http, Logger log) {
        String path = "provider.providers." + e.name();
        switch (e.type()) {
            case OPENAI -> {
                if (e.apiKey().isEmpty()) return missing(log, path + ".api-key", e.name());
                return new OpenAiProvider(e.name(), e.baseUrl(), e.apiKey(), e.model(), false, http);
            }
            case ANTHROPIC -> {
                if (e.apiKey().isEmpty()) return missing(log, path + ".api-key", e.name());
                return new AnthropicProvider(e.baseUrl(), e.apiKey(), e.model(), http);
            }
            case OLLAMA -> {
                return new OllamaProvider(e.baseUrl(), e.model(), http);
            }
            case CUSTOM -> {
                if (e.baseUrl().isEmpty()) return missing(log, path + ".base-url", e.name());
                if (e.model().isEmpty()) return missing(log, path + ".model", e.name());
                return new OpenAiProvider(e.name(), e.baseUrl(), e.apiKey(), e.model(), true, http);
            }
            default -> throw new IllegalStateException();
        }
    }

    private static AiProvider missing(Logger log, String option, String name) {
        log.warning("Provider \"" + name + "\" skipped: " + option + " is not set");
        return null;
    }
}
