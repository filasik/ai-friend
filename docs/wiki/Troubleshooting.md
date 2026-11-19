# Troubleshooting

Start with `/aifriend status`. It shows the state, provider, model, masked key, queue and failed requests in a row. The log has **one clear line per failure** with the reason.

## The bot says nothing

| `/aifriend status` shows | Cause and fix |
| :--- | :--- |
| *idle, no provider api-key* / no usable provider | Set the key of your provider (a default placeholder means unset) and `/aifriend reload`. Warnings in the log say what is missing |
| *disabled* | `enabled: false` in `config.yml` |
| *sleeping* | Players seemed annoyed, or an admin used `/aifriend sleep`. `/aifriend wake` (or `/aifriend reload`) wakes it |
| *waiting ... before the next attempt* (backoff) | Recent requests failed; the next try is delayed (up to 15 minutes). Read the log line for the reason |
| *running* | See below |

If the state is *running* and still nothing appears:

- Nobody wrote in public chat since the last interval (nothing is sent without messages), or everybody who did is muted.
- The bot decided to stay quiet. It does not answer every time when players talk among themselves. Ask it directly (`/ai`, `@AI Friend`) or raise `chattiness`.
- Rate limits are reached: check `/aifriend limits` and `/aifriend cost`.
- Messages are sent through a chat plugin's channel that only some players see. Those are deliberately ignored; only public chat is read.
- `/aifriend simulate` shows what blocks a reply and what the bot would answer.

## Errors in the log

**`401 Unauthorized`.** The key was rejected. Check for typos or whitespace, that the key is active and has credit, that it belongs to the provider you selected, and that you did not leave the default placeholder.

**`429` / rate limit.** Your provider account is rate limited or out of credit. Add a [failover provider](Provider-Setup#failover), raise `interval-seconds`, or check your provider's dashboard.

**`Unexpected response from anthropic: no text content`.** A model that thinks first used up `max-output-tokens` before writing text. The plugin retries once with more room; if it persists, raise `provider.providers.anthropic.max-output-tokens` (400 is the default for new installs since 3.0.2).

**Timeouts.** Raise `http.read-timeout-seconds` (especially for a slow local Ollama), or pick a smaller model.

**`Cannot use authentication plugin caching_sha2_password with a Self signed certificates`.** MySQL 8+ with the default certificate. Set `storage.mariadb.ssl-mode: trust`. See [Storage and Database](Storage-and-Database#ssl-mode).

**Database cannot be opened.** One line is logged and only long-term memory and saved relationships stay off; the bot keeps running. Check credentials and network, then `/aifriend reload`.

**The plugin fails on the first start while downloading libraries.** The server needs access to Maven Central once. Allow it, or start once on a machine with internet and copy the Paper library cache.

## Feature commands say they are switched off

| Command | Enable |
| :--- | :--- |
| `/ai`, mentions | `mentions.enabled` |
| `/aifriend mute` | `opt-out.enabled` |
| `/aifriend cost` | `cost.enabled` |
| `/aifriend memory`, `forget` | `long-term-memory.enabled` |
| `/aifriend nickname` | `relationships.enabled` |
| `/aifriend teach`, `knowledge` | `knowledge.enabled` |
| `/aifriend learned` | `knowledge.learning.enabled` |

## The bot answers too often or costs too much

Raise `interval-seconds`, set `chattiness: low`, enable `limits` and `cost`, keep `memory` off or small, keep `server-context.share-percent` low, and use a cheap model.

## Long-term notes stay empty

- Check that the database opened (log at startup).
- Notes are only looked for every `extract-every-exchanges` answered exchanges, and only facts players say about **themselves** count.
- A warning in the log says when an answer contained no notes although it should. Make sure `max-output-tokens` is not tiny.

## Learned facts never reach the review

A fact must come up in `min-mentions` separate looks (default 2) before admins are asked. Check `/aifriend learned list candidate`. Lower `min-mentions` to 1 to test.

## Still stuck?

Open an issue on [GitHub](https://github.com/filasik/ai-friend/issues) with your Paper version, the plugin version, the relevant log lines (keys are already masked) and the output of `/aifriend status`.
