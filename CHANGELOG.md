# Changelog

## 3.0.0

**Breaking: the `proxy` provider and the `trial` key are removed.** AI Friend now talks only to the provider you configure yourself: `openai`, `anthropic`, `ollama` or `custom` (any OpenAI-compatible server). The plugin sends nothing to the author's server (the old relay at `gpt.netuj.eu` is no longer contacted). The only other outbound calls are bStats (`metrics: false`) and the GitHub update check (`update-check: false`). `config-version` is now 5.

- Removed: the `proxy` provider (`ProxyProvider`, `ApiClient`), the free 3-day `trial` key and everything around it
- Removed: the top-level `api-key` and `model` options (they only existed for the proxy). Keys and models live under `provider.providers.<name>.api-key` / `.model`
- Change: `provider.primary` now defaults to `openai`. Without an API key the bot stays silent and nothing is sent
- Change: `/aifriend status` shows the key and model of the provider that answers (key still masked); a provider without a model shows `-`
- Change: the 1.x keys `openai_api_key` and `gpt_version` are no longer migrated to the removed top-level options (they belonged to the proxy)
- Docs: the privacy notice (README, Modrinth, SpigotMC) now starts with the statement that chat goes only to the provider you choose

Migration (nothing is switched for you, because moving chat to another company would be a change you have to agree to):

1. Update the jar. `config.yml` gets `config-version: 5` and a backup `config.yml.bak-v<old>` is written. None of your values is changed or deleted.
2. If `provider.primary` is `proxy`, or your config has a top-level `api-key` (for example `trial`) and never chose a provider, the bot stays **inactive and sends nothing**; the log says so in one line at startup. The same goes for a config that lists `proxy` first and a working provider only under `failover`.
3. Set `provider.primary` to `openai`, `anthropic`, `ollama` or `custom`, put that provider's key under `provider.providers.<name>.api-key`, and run `/aifriend reload`.
4. A `proxy` entry in `provider.failover` is skipped with a warning. A top-level `api-key` left in the file is ignored (with a warning), is never copied anywhere and can be deleted.

## 2.2.0

World awareness and personality. Nothing changes until you switch it on: every new feature is **off by default**. `config-version` is now 4; the five new sections (`server-context`, `mood`, `relationships`, `boredom`, `supervision`) are added to your `config.yml` automatically with comments (backup `config.yml.bak-v3`). Database schema v2 adds one table (`aifriend_relationships`); the migration runs the next time the database is opened, which only happens while long-term memory or relationships are enabled. Existing notes are untouched.

- New: **server context** (`server-context.*`): every request carries a short picture of the server as untrusted data lines: how many players are online and, for the players who just wrote, their world, biome, the hostile mobs around them (`"2 zombie, 1 creeper"`) and the time and weather *that player sees*: read per player, so other worlds (Multiverse) and per-player time or weather (Residence, `/ptime`, `/pweather`) are respected, and worlds without a day cycle (Nether, End) give none; the general time and weather are sent only when every player sees the same. No coordinates, inventories or anything about players who did not write. Collected on the right threads (one entity task per player, Folia safe) every `refresh-seconds`, every detail has its own switch
- New: **mood** (`mood.*`): a mood from -100 to 100 that advancements, boss kills and first joins raise and deaths (more when several players die within five minutes) and repeated big explosions (looks like griefing) lower; it fades back to calm with a half-life. It is a hint in the system prompt, shows in `/aifriend status` and in `%aifriend_mood%`, and works without the `events` reactions
- New: **relationships** (`relationships.*`): the bot counts the answered exchanges a player took part in and treats strangers, acquaintances, regulars and friends differently; players can pick a nickname with `/aifriend nickname <text>` (letters, digits, space and `_ - . '`, up to 24 characters, checked against the reply filter, `clear` removes it). Stored in the database from `storage` (UUID, last name, counter, first and last time, nickname); `/aifriend forget`, `forget <player>`, `forget-all` and muting the bot delete it. Without a database it stays in memory
- New: **boredom** (`boredom.*`): when nobody has written for `quiet-minutes`, the bot may start a conversation (topic, quiz question, challenge, joke or fact, `ideas`). Only while at least `min-players` (default 1) are online, never on an empty server (the quiet time starts when the first player arrives), never when asleep, rate limited or waiting after an error, with a `cooldown-minutes` that doubles (up to 8 times) while nobody reacts and a `max-per-day`
- New: **chat supervision** (`supervision.*`): a friendly private note to a player who writes in caps lock, spams (the same line three times or six lines in ten seconds) or uses a word from `bad-words` (accents, look-alike characters and stretched letters are ignored). Nothing else happens: no kick, mute or ban, nobody else sees it, nothing is stored (only hashes of the last lines, for ten seconds, in memory); players with `aifriend.bypass-supervision` and players who muted the bot are skipped
- New: `/aifriend nickname [text|clear]`; permissions `aifriend.nickname` (everyone) and `aifriend.bypass-supervision` (op); `/aifriend status` shows the awareness features; the startup banner lists them
- New: PlaceholderAPI `%aifriend_relationship%` (stranger, acquaintance, regular, friend) and `%aifriend_nickname%`; `%aifriend_mood%` is the real mood (thrilled, cheerful, calm, uneasy, upset) when `mood.enabled`
- New: English and Czech messages for all of the above
- Change: the database is opened when long-term memory **or** relationships are enabled; `/aifriend forget` works with either
- Change: the prompt builder takes the extra data lines (`{"context":...}`, `{"player_context":...}`, `{"relationship":...}`) in the same escaped-JSON data block as chat lines; the mood and the conversation starter are in the trusted system part (computed by the plugin, never from player text)
- Not included: several bots at once (planned, see ROADMAP.md)

## 2.1.0

Direct API calls and chat intelligence. Existing installs keep working unchanged: the original provider stays the default and every new chat feature is off until you switch it on. `config-version` is now 3; the new sections are added to your `config.yml` automatically with comments (backup `config.yml.bak-v<old>`, `-v0` when coming from a 1.x file).

- New: AI providers. Besides the original relay, chat can go straight to OpenAI, Anthropic (Claude), a local Ollama server or any OpenAI-compatible endpoint (OpenRouter, LM Studio, vLLM, ...), with real system and user roles; select with `provider.primary`, configure under `provider.providers.*`, extra named endpoints with `type: custom`
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
- Change: the prompt is split into a system part (rules, persona) and a user part (chat lines as delimited data) for providers that support roles; the original relay still received one combined message, as before
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

- New: documentation, API response handling

## 1.0.0 - 1.2.0 - 2024-10-20

First releases: chat collection and scheduled AI replies, `/aifriend` configuration output, config options, update checker, personalization and recognition of players who lose interest (the bot pauses for 30 minutes).
