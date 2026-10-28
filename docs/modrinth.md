# AI Friend

A friendly AI companion for your Minecraft chat. It reads the public chat and posts short replies as a bot with your own name, language and personality, using OpenAI, Claude, a local Ollama model or any OpenAI-compatible server.

![banner](https://raw.githubusercontent.com/filasik/ai-friend/main/docs/assets/banner.png)

> **Paper only.** Version 2.x requires Paper 26.3+ and Java 25. Spigot and Bukkit are no longer supported; the 1.x jar stays available on [GitHub](https://github.com/filasik/ai-friend/releases).

## Features
- One short reply per interval (`interval-seconds`, default 60) as `<chatbot-name>: <reply>`, plus direct questions with `/ai <question>` or `@AI Friend ...`
- Choose your AI: OpenAI, Anthropic Claude, local Ollama, any OpenAI-compatible endpoint (OpenRouter, LM Studio, ...) or the original proxy (still the default); failover chain and optional answer cache
- Optional chat features, all off by default: personas (by world and time of day), conversation memory, rate and daily limits, cost estimate, reply filter, reactions to deaths, advancements, first joins, boss kills and explosions
- Players can switch the bot off for themselves with `/aifriend mute`
- Optional long-term memory (off by default): the bot remembers facts players told about themselves, in SQLite or MariaDB; players list and delete their notes with `/aifriend memory` and `/aifriend forget`
- Public API (`AiFriendResponseEvent`, `AiFriendApi`) and PlaceholderAPI placeholders (`%aifriend_state%`, `%aifriend_mood%`, `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`)
- Admin tools: `/aifriend sleep`, `wake`, `simulate` (dry run, nothing is posted), `cost`, `status`
- Configurable bot name, reply language and a free-text `personalization`
- Goes to sleep when players seem annoyed; one player alone cannot silence it
- Free 3-day trial with the key `trial` (proxy), or your own key
- Never blocks the server: async requests with timeouts, backoff on failures, bounded queue
- Player chat is sent as clearly delimited data, never as instructions
- Commented `config.yml` that gains new options on update without touching your values; English and Czech messages
- Folia-compatible code (no `BukkitScheduler`)

## Quick start
1. Put the jar in `plugins/` and start the server.
2. Set `api-key: "trial"` in `plugins/ai-friend/config.yml`, or choose a provider under `provider:` (OpenAI, Claude, Ollama, custom).
3. `/aifriend reload`, then `/aifriend status`.

## Commands & Permissions
| Command | Permission | Description |
|---|---|---|
| `/aifriend` | `aifriend.use` (everyone) | Version and short help |
| `/aifriend status` | `aifriend.admin` (op) | State, provider, persona, masked API key, queue |
| `/aifriend reload` | `aifriend.reload` (op) | Reload config and messages (`/aifriendreload` still works) |
| `/ai <question>` | `aifriend.ask` (everyone) | Ask the bot directly |
| `/aifriend limits` | `aifriend.ask` (everyone) | Your remaining calls and cooldown |
| `/aifriend mute` / `unmute` | `aifriend.mute` (everyone) | Switch the bot off or on for yourself |
| `/aifriend memory` / `forget` | `aifriend.mute` (everyone) | List / delete the notes the bot keeps about you (long-term memory) |
| `/aifriend memory <player>` / `forget <player>` / `forget-all` | `aifriend.admin` (op) | List / delete notes of a player or of everybody |
| `/aifriend sleep [minutes]` / `wake` | `aifriend.sleep` (op) | Quiet the bot or wake it |
| `/aifriend simulate [nocall]` | `aifriend.simulate` (op) | Dry run without posting |
| `/aifriend cost` | `aifriend.cost` (op) | Token totals and estimated cost |

`aifriend.bypass-limits` (op) exempts a player from the cooldown and daily limits.

## Configuration
Key options in `config.yml` (all commented): `provider.*` (primary, failover, cache and per-provider settings), `personas`, `memory`, `limits`, `cost`, `reply-filter`, `mentions`, `opt-out`, `events`, `storage` (SQLite or MariaDB), `long-term-memory`, `enabled`, `api-key`, `model` (default `gpt-4o-mini`), `chatbot-name`, `language`, `personalization`, `interval-seconds` (5-3600), `stop-when-ignored`, `sleep-minutes`, `max-queued-messages`, `max-reply-length`, `http.*` timeouts, `locale` (`en`/`cs`), `update-check`, `metrics`. Full reference in the [README](https://github.com/filasik/ai-friend#configuration).

## Privacy / data

**Only public chat is read.** Private messages (`/msg`, `/tell`, `/r`), other commands, cancelled messages and messages that a chat plugin delivers to only a part of the players (staff, party or local channels) are never collected or sent anywhere. The one exception is a question the player asks the bot on purpose with `/ai`. The plugin does not write chat lines to its own log.
What leaves your server depends on `provider.primary` (and the failover list). A request contains recent chat lines **including player names**, the prompt, optional conversation memory and event descriptions.

- **proxy** (default): sent to `gpt.netuj.eu`, **operated by the plugin author**, together with your API key, the model name and your server's address, and forwarded to OpenAI.
- **openai / anthropic**: sent directly to `api.openai.com` / `api.anthropic.com` with your key, no proxy involved.
- **ollama**: sent only to your Ollama server (local by default); nothing leaves the machine.
- **custom**: sent to the endpoint you configure (OpenRouter, your own server, ...).

**Long-term memory** (off by default) stores, in your own database, the player UUID and name and short notes (max 120 characters) the model wrote from what the player said about themselves, plus optionally an embedding of each note. To find them, an extra request sends recent chat lines (with player names) to your AI provider every `extract-every-exchanges` answers, and with embeddings notes and chat text also go to the embedding service. The model is told to keep nothing sensitive and notes that look like contact data are dropped, but check `/aifriend memory <player>` if in doubt. Players delete their notes with `/aifriend forget`, muting the bot stops collection (and deletes the notes by default), `retention-days` removes old ones.

Chat of your players is therefore sent to a third party unless you use a local model; mention it in your server rules or privacy policy. API keys stay in `config.yml` and are masked in output. Nothing is sent while no provider is usable or the bot is disabled; muted players are never stored or sent. The only other network calls are anonymous bStats statistics (`metrics: false` turns them off) and the GitHub update check (`update-check: false`).

## Integrations
- **API for plugins:** `AiFriendResponseEvent` (asynchronous, cancellable, `setText`) fires right before the bot posts; `AiFriendApi` is available from the Bukkit `ServicesManager`.
- **PlaceholderAPI** (soft dependency): `%aifriend_state%`, `%aifriend_mood%` (happy/sleepy/confused/off), `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`.

## Compatibility
- Paper 26.3+ (Java 25). Not Spigot or Bukkit.
- Needs outbound HTTPS to your chosen provider (not for a local Ollama). The first start downloads the plugin's libraries (database drivers, connection pool) from Maven Central.

## Support
Issues: https://github.com/filasik/ai-friend/issues · Web: https://github.com/filasik/ai-friend · Authors: Filasik (Discord `filasik`), Patr1xCZ (Discord `paprik_man`)

This plugin is free and has no in-game perks. Source code is MIT licensed.
