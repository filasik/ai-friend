# Changelog

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
