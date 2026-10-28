package me.skerik.aifriend.ai.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers answers for identical requests (key = SHA-256 of the whole request) in a bounded LRU map with a TTL.
 * A hit returns the stored text with 0 tokens (nothing was spent). Failures are never cached. The lock is not held
 * while the delegate runs, so concurrent identical requests may both reach the delegate; that is harmless.
 */
public final class CachingProvider implements AiProvider {

    private record Cached(ProviderResult result, long expiresAt) {
    }

    private final AiProvider delegate;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, Cached> entries;

    public CachingProvider(AiProvider delegate, int maxEntries, Duration ttl, Clock clock) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.delegate = delegate;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public CachingProvider(AiProvider delegate, int maxEntries, Duration ttl) {
        this(delegate, maxEntries, ttl, Clock.systemUTC());
    }

    @Override
    public String id() {
        return delegate.id();
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    @Override
    public ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException {
        String key = hash(request);
        synchronized (entries) {
            Cached hit = entries.get(key);
            if (hit != null) {
                if (hit.expiresAt() > clock.millis()) {
                    ProviderResult r = hit.result();
                    return new ProviderResult(r.text(), 0, 0, r.model());
                }
                entries.remove(key);
            }
        }
        ProviderResult result = delegate.complete(request);
        synchronized (entries) {
            entries.put(key, new Cached(result, clock.millis() + ttlMillis));
        }
        return result;
    }

    static String hash(ProviderRequest request) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            update(md, request.jsonReply() ? "json" : "text");
            update(md, Integer.toString(request.maxOutputTokens()));
            for (ChatMessage m : request.messages()) {
                update(md, m.role().name());
                update(md, m.content());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** Length-prefixed so that field boundaries cannot be forged by content. */
    private static void update(MessageDigest md, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        md.update((b.length + ":").getBytes(StandardCharsets.US_ASCII));
        md.update(b);
    }
}
