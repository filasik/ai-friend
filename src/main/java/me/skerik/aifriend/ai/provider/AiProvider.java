package me.skerik.aifriend.ai.provider;

import java.io.IOException;

/**
 * A source of AI answers. Implementations are blocking and thread-safe; call them from async tasks only, never from
 * the main thread. A non-2xx HTTP answer is reported as {@link me.skerik.aifriend.ai.ApiException}.
 */
public interface AiProvider {

    /** Short stable name for logs and status output (never contains secrets). */
    String id();

    ProviderResult complete(ProviderRequest request) throws IOException, InterruptedException;
}
