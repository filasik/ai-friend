package me.skerik.aifriend.ai.provider;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Tries providers in order. A provider that fails is put on a cooldown and skipped until it ends, so a dead provider
 * is not retried on every request. When every provider is cooling down all of them are tried anyway (better than
 * staying silent). If all fail, the last exception is rethrown (earlier ones are attached as suppressed).
 */
public final class FailoverProvider implements AiProvider {

    private final List<AiProvider> providers;
    private final Duration cooldown;
    private final Clock clock;
    private final AtomicLongArray blockedUntil;

    public FailoverProvider(List<AiProvider> providers, Duration cooldown, Clock clock) {
        if (providers.isEmpty()) throw new IllegalArgumentException("at least one provider is required");
        this.providers = List.copyOf(providers);
        this.cooldown = cooldown;
        this.clock = clock;
        this.blockedUntil = new AtomicLongArray(providers.size());
    }

    public FailoverProvider(List<AiProvider> providers) {
        this(providers, Duration.ofSeconds(60), Clock.systemUTC());
    }

    @Override
    public String id() {
        return providers.getFirst().id();
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        long now = clock.millis();
        boolean anyAvailable = false;
        for (int i = 0; i < providers.size(); i++) anyAvailable |= blockedUntil.get(i) <= now;

        IOException last = null;
        for (int i = 0; i < providers.size(); i++) {
            if (anyAvailable && blockedUntil.get(i) > now) continue;
            try {
                ProviderResult result = providers.get(i).complete(request);
                blockedUntil.set(i, 0);
                return result;
            } catch (IOException e) {
                blockedUntil.set(i, clock.millis() + cooldown.toMillis());
                if (last != null) e.addSuppressed(last);
                last = e;
            }
        }
        throw last; // never null: at least one provider was tried
    }
}
