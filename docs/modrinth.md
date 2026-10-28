# AI Friend

A friendly AI companion for your Minecraft chat. It reads the public chat and, every minute, posts one short reply as a bot with your own name, language and personality.

![banner](https://raw.githubusercontent.com/filasik/ai-friend/main/docs/assets/banner.png)

> **Paper only.** Version 2.0.0 requires Paper 26.3+ and Java 25. Spigot and Bukkit are no longer supported; the 1.x jar stays available on [GitHub](https://github.com/filasik/ai-friend/releases).

## Features
- One short reply per interval (`interval-seconds`, default 60) as `<chatbot-name>: <reply>`
- Configurable bot name, reply language and a free-text `personalization` (website, jokes, tone)
- Goes to sleep (`sleep-minutes`) when players seem annoyed; one player alone cannot silence it, at least 2 different players must be involved
- Free 3-day trial with the key `trial`, or your own OpenAI key through the proxy
- Never blocks the server: async requests with timeouts, exponential backoff on failures (max 15 min), bounded message queue
- Player chat is sent as clearly delimited data, never as instructions
- Commented `config.yml` that is never overwritten and gains new options on update; old 1.x keys are migrated automatically
- English and Czech messages (MiniMessage, editable), update notices from GitHub
- Folia-compatible code (no `BukkitScheduler`)

## Quick start
1. Put the jar in `plugins/` and start the server.
2. Set `api-key: "trial"` (or your OpenAI key) in `plugins/ai-friend/config.yml`.
3. `/aifriend reload`, then `/aifriend status`.

## Commands & Permissions
| Command | Permission | Description |
|---|---|---|
| `/aifriend` | `aifriend.use` (everyone) | Version and short help |
| `/aifriend status` | `aifriend.admin` (op) | State, masked API key, queue, sleeping/backoff info |
| `/aifriend reload` | `aifriend.reload` (op) | Reload config and messages (`/aifriendreload` still works) |

## Configuration
Key options in `config.yml` (all commented): `enabled`, `api-key`, `model` (default `gpt-4o-mini`), `chatbot-name`, `language`, `personalization`, `interval-seconds` (5-3600), `stop-when-ignored`, `sleep-minutes`, `max-queued-messages`, `max-reply-length`, `http.*` timeouts, `locale` (`en`/`cs`), `update-check`, `metrics`. Full reference in the [README](https://github.com/filasik/ai-friend#configuration).

## Privacy / data
When the bot is active, AI Friend sends recent chat lines **including player names**, the prompt, your API key, the model name and your server's address to a proxy at `gpt.netuj.eu`, **operated by the plugin author**, which forwards it to the AI provider. Chat of your players is therefore sent to a third party; mention it in your server rules or privacy policy.

Nothing is sent while the API key is not set or the bot is disabled. The only other network calls are anonymous bStats statistics (`metrics: false` turns them off) and the GitHub update check (`update-check: false`).

Direct OpenAI access and other providers are planned, not available yet.

## Compatibility
- Paper 26.3+ (Java 25). Not Spigot or Bukkit.
- Needs outbound HTTPS to `gpt.netuj.eu`. The first start downloads the plugin's libraries from Maven Central.

## Support
Issues: https://github.com/filasik/ai-friend/issues · Web: https://github.com/filasik/ai-friend · Authors: Filasik (Discord `filasik`), Patr1xCZ (Discord `paprik_man`)

This plugin is free and has no in-game perks. Source code is MIT licensed.
