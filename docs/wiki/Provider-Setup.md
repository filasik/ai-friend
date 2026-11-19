# Provider Setup

AI Friend talks only to the provider you configure. Set `provider.primary` and fill in that provider under `provider.providers`. Only what you change needs to be listed; the rest uses the defaults. Run `/aifriend reload` afterwards and check `/aifriend status`.

| Provider | `provider.primary` | Needs | Chat goes to |
| :--- | :--- | :--- | :--- |
| OpenAI (default) | `openai` | `providers.openai.api-key` | `api.openai.com` |
| Anthropic Claude | `anthropic` | `providers.anthropic.api-key` | `api.anthropic.com` |
| Ollama | `ollama` | A running Ollama, no key | Your Ollama server (default `localhost`) |
| Custom (OpenAI-compatible) | `custom` | `base-url` and `model`, a key if the server wants one | The `base-url` you set |

Every provider accepts the same four settings: `model`, `base-url` (with a sensible default), `api-key` and `max-output-tokens` (16-4000, default 400).

## OpenAI

```yaml
provider:
  primary: openai
  providers:
    openai:
      api-key: "sk-..."
      model: gpt-4o-mini
```

Change `base-url` (default `https://api.openai.com/v1`) only for a compatible gateway.

## Anthropic Claude

```yaml
provider:
  primary: anthropic
  providers:
    anthropic:
      api-key: "sk-ant-..."
      model: claude-haiku-5-5
```

The key is sent in the `x-api-key` header to `https://api.anthropic.com`. Models that think before writing can use up a tight `max-output-tokens` before any text appears; the plugin then retries once with more room. If you still see `no text content` in the log, raise `max-output-tokens`.

## Ollama (fully local)

```yaml
provider:
  primary: ollama
  providers:
    ollama:
      model: llama3.2            # run "ollama pull llama3.2" first
      base-url: http://localhost:11434
```

No key is needed. If Ollama runs on the same machine, nothing leaves your server. Pointing `base-url` at another host sends the chat there.

## OpenRouter or any OpenAI-compatible server

You can use the built-in `custom` entry, or add an entry under a name of your own. A name of your own needs a `type`:

```yaml
provider:
  primary: openrouter
  providers:
    openrouter:
      type: custom
      base-url: https://openrouter.ai/api/v1
      api-key: "sk-or-..."
      model: openai/gpt-4o-mini
```

For a server without a key (LM Studio, for example `base-url: http://localhost:1234/v1`) leave `api-key` empty.

## Failover

When the primary fails (error, timeout, rate limit, bad key), the providers in `failover` are tried in order. A failing provider is skipped for `failover-cooldown-seconds`. A provider with missing settings is skipped with a warning at startup.

```yaml
provider:
  primary: openai
  failover: [anthropic, ollama]     # Claude if OpenAI is down, a local model as the last resort
  failover-cooldown-seconds: 60
  providers:
    openai:    { api-key: "sk-...",     model: gpt-4o-mini }
    anthropic: { api-key: "sk-ant-..." }
    ollama:    { model: llama3.2 }
```

With a failover chain, the chat goes to whichever provider ends up answering, so every member of the chain is a potential recipient. See [Privacy and Data](Privacy-and-Data).

## Answer cache

```yaml
provider:
  cache:
    enabled: true
    max-entries: 200
    ttl-minutes: 10
```

Remembers answers to identical requests in memory (lost on restart). Chat is rarely identical, so the cache is off by default and is mostly useful for repeated direct questions without conversation memory.

## Good to know

- Redirects are never followed, so a key cannot be forwarded to another host.
- Keys are masked in `/aifriend status` and removed from error messages and the log.
- `max-output-tokens` of the primary provider sets the answer size limit. A very small value can produce empty answers with reasoning models.
- The HTTP timeouts are `http.connect-timeout-seconds` (10) and `http.read-timeout-seconds` (30).
- After a failure the bot waits 1, 2, 4, ... minutes (at most 15) before the next try. `/aifriend status` shows the state as *backoff*.

## Which provider should I choose?

- **Lowest cost and effort:** `openai` with `gpt-4o-mini`, or `anthropic` with the small Claude model.
- **Privacy:** a local `ollama` model (needs a capable machine).
- **Choice of many models:** `custom` with OpenRouter.

Long-term memory can also use an embedding service; see [Long-Term Memory](Long-Term-Memory#which-notes-fit).
