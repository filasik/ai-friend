# Changelog

## 2.1.0

Direct API calls and chat intelligence. Existing installs keep working unchanged: the proxy stays the default provider and every new chat feature is off until you switch it on. `config-version` is now 3; the new sections are added to your `config.yml` automatically with comments (backup `config.yml.bak-v<old>`, `-v0` when coming from a 1.x file).

- New: AI providers. Besides the proxy, chat can go straight to OpenAI, Anthropic (Claude), a local Ollama server or any OpenAI-compatible endpoint (OpenRouter, LM Studio, vLLM, ...), with real system and user roles; select with `provider.primary`, configure under `provider.providers.*`, extra named endpoints with `type: custom`
- New: failover chain (`provider.failover`) with a cooldown for failing providers (`provider.failover-cooldown-seconds`)
- New: optional cache of answers to identical requests (`provider.cache.*`, in memory)
- New: personas (`personas.*`), picked per request by world and time of day with weights; the prompt text is yours
- New: conversation memory (`memory.*`): the last N exchanges are sent as chat history
- New: rate limits (`limits.*`): cooldown, daily calls and tokens per player and for the whole server; `/aifriend limits` shows what is left; permission `aifriend.bypass-limits` skips them
- New: cost estimate (`cost.*`): `/aifriend cost` shows token totals and an estimated price, with an optional daily cost warning for admins
- New: reply filter (`reply-filter.*`): maximum length, banned words (replace or drop the answer), link removal; `§` colour codes and control characters are always removed
- New: direct questions (`mentions.*`): `/ai <question>`, `@AI Friend question` and `AI Friend, question` in chat, extra trigger names
- New: per-player opt-out (`opt-out.*`): `/aifriend mute` and `/aifriend unmute`; muted players' chat is never stored or sent
- New: reactions to game events (`events.*`): death, advancement, first join, Ender Dragon and Wither kills and big explosions, with a global event cooldown
- New: `/aifriend sleep [minutes]`, `/aifriend wake`, `/aifriend simulate [nocall]` (dry run, nothing is posted) and extended `/aifriend status` (provider, persona, memory, muted players)
- New: permissions `aifriend.mute`, `aifriend.ask`, `aifriend.sleep`, `aifriend.simulate`, `aifriend.cost`, `aifriend.bypass-limits`
- New: English and Czech messages for all of the above
- Change: the prompt is split into a system part (rules, persona) and a user part (chat lines as delimited data) for providers that support roles; the proxy still receives one combined message, as before
- Change: API keys of all providers are only stored in `config.yml`, are masked in output and are removed from error messages
- New: long-term memory (`long-term-memory.*`, off by default): every N answered exchanges one extra request asks the model for durable facts players said about themselves (max 3 notes of 120 characters, nothing sensitive; notes that look like e-mail, phone, link or IP are dropped), stores them by player UUID and puts the best ones about the players in the current chat back into later requests as untrusted `{"note":...,"about":...}` data lines. Notes are matched by shared words or, optionally, by embeddings (`long-term-memory.embeddings.*`: OpenAI-compatible `/embeddings`, Ollama `/api/embed`)
- New: privacy commands `/aifriend memory [player]`, `/aifriend forget [player]` and `/aifriend forget-all`; muting the bot stops collecting notes and (`delete-on-mute`) deletes them; `max-notes-per-player` and `retention-days` with a daily cleanup
- New: database layer (`storage.*`): SQLite (default, one file) or MariaDB/MySQL (shared between servers), HikariCP, versioned append-only migrations, all access asynchronous; the drivers are downloaded with the other libraries on the first start. Nothing is created unless a feature that needs the database is switched on; if it cannot be opened, one line is logged and only that feature stays off
- New: public API: asynchronous, cancellable `AiFriendResponseEvent` (change or drop the text before it is posted) and `AiFriendApi` through the Bukkit `ServicesManager` (state, last message, provider, queue, sleep, wake)
- New: PlaceholderAPI expansion (soft dependency): `%aifriend_state%`, `%aifriend_mood%`, `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`
- Change: `/aifriend status` shows the long-term memory state; the startup banner shows the database and schema version when long-term memory is on
- Not included yet: a Dynmap hook and persistence of cache, limits and cost totals across restarts (see ROADMAP.md)

## 2.0.0

Full rewrite of the plugin (Java and Gradle instead of Kotlin and Maven). **Breaking:** Paper only, Spigot/Bukkit servers are no longer supported (the 1.x jar stays available on the GitHub releases page), and config keys were renamed (migrated automatically).

- Breaking: Paper 26.3+ and Java 25 are required (`paper-plugin.yml`); the code is Folia-compatible (no `BukkitScheduler`)
- Change: config keys renamed (`openai_api_key` -> `api-key`, `gpt_version` -> `model`, `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`); old keys are migrated on first start, the config folder `plugins/ai-friend/` is unchanged
- Fix: the request loop can no longer block or crash. Proper async scheduler instead of `java.util.Timer`, HTTP connect/read timeouts (`http.*`), all errors caught, exponential backoff on failures (1, 2, 4, ... max 15 minutes) with one clear log line each
- Fix: replies are posted on the main/global thread
- Fix: thread-safe bounded message queue (`max-queued-messages`); messages are kept when a request fails and messages arriving during a request are never lost
- Fix: requests and replies are built and parsed as proper JSON; player text is JSON-escaped and sent as clearly delimited untrusted data, never as instructions
- Fix: `/aifriend reload` no longer registers duplicate listeners or timers
- Change: the bot goes to sleep through a structured action reported by the model instead of text matching, and the plugin honors it only when at least 2 different players (or all online players, if fewer) took part in the batch, so one player cannot silence the bot for everyone
- New: `/aifriend status` (state, queue size, sleeping and backoff info); the API key is masked (e.g. `sk-...abcd`)
- New: permissions `aifriend.use`, `aifriend.admin`, `aifriend.reload` instead of an op check; commands work from the console; Brigadier tab completion; configurable `command.aliases`; `/aifriendreload` kept as an alias
- New: `config.yml` is commented, never overwritten and updated automatically with new options (`config-version`, backup `config.yml.bak-v<N>`); invalid values fall back to defaults with a warning
- New: new options `sleep-minutes`, `max-queued-messages`, `max-reply-length`, `http.connect-timeout-seconds`, `http.read-timeout-seconds`, `locale`, `update-check`, `metrics`
- New: messages in `messages_en.yml` and `messages_cs.yml` (MiniMessage), editable
- Change: the update checker is asynchronous, cached and compares versions semantically; the plugin version is read from the jar
- New: bStats statistics, switch off with `metrics: false`

## 1.4.1 - 2025-09-15

Last Kotlin/Maven version, Spigot-compatible. Code cleanup.

## 1.4.0 - 2024-11-02

- New: `/aifriendreload` command, colored messages

## 1.3.0 - 2024-10-21

- New: free `trial` API key (3 days), documentation, API response handling

## 1.0.0 - 1.2.0 - 2024-10-20

First releases: chat collection and scheduled AI replies, `/aifriend` configuration output, config options, update checker, personalization and recognition of players who lose interest (the bot pauses for 30 minutes).
