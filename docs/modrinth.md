# AI Friend

A friendly AI companion for your Minecraft chat. It reads the public chat and posts short replies as a bot with your own name, language and personality, using OpenAI, Claude, a local Ollama model or any OpenAI-compatible server.

> **Privacy:** chat lines, including player names, go only to the AI provider you choose yourself (OpenAI, Anthropic, your own Ollama or another OpenAI-compatible server). Nothing goes through the plugin author. Details in the Privacy / data section below.

![banner](https://raw.githubusercontent.com/filasik/ai-friend/main/docs/assets/banner.png)

> **Paper only.** Version 3.x requires Paper 26.3+ and Java 25. Spigot and Bukkit are no longer supported; the 1.x jar stays available on [GitHub](https://github.com/filasik/ai-friend/releases).

## Features
- One short reply per interval (`interval-seconds`, default 60) as `<chatbot-name>: <reply>`, plus direct questions with `/ai <question>` or `@AI Friend ...`
- Choose your AI: OpenAI, Anthropic Claude, local Ollama, any OpenAI-compatible endpoint (OpenRouter, LM Studio, ...) (OpenAI is the default); failover chain and optional answer cache
- Optional chat features, all off by default: personas (by world and time of day), conversation memory, rate and daily limits, cost estimate, reply filter, reactions to deaths, advancements, first joins, boss kills and explosions
- Players can switch the bot off for themselves with `/aifriend mute`
- Optional long-term memory (off by default): the bot remembers facts players told about themselves, in SQLite or MariaDB; players list and delete their notes with `/aifriend memory` and `/aifriend forget`, or just ask the bot in chat to forget something ("forget that", "zapomeň na to")
- World awareness and personality (all off by default): the bot knows the surroundings of the players who write (world, biome, nearby hostile mobs, and the time and weather each player really sees, also with Multiverse or Residence; never coordinates), has a mood that follows what happens (advancements, boss kills, deaths, griefing), gets to know players (stranger, acquaintance, regular, friend, with a nickname they pick via `/aifriend nickname`), starts a conversation or quiz when the chat is quiet (only with at least `min-players` online, never on an empty server) and sends friendly private hints about caps lock, spam and language (never a punishment)
- Server knowledge (off by default): fill `knowledge.txt` with your rules, IP, shops and events, or add facts in game with `/aifriend teach <fact>` (admins only); the bot also knows what it sees, stores and can do, and answers questions like "do you read private messages?" correctly. Plain word matching, no extra request, no database
- Learning from the chat (off by default): the bot picks up general facts about your server that players state ("keys and chests open at /truhly", "shops are at /warp shop"); once a fact came up often enough, admins get a message and approve, reject, disable or delete it in a GUI (`/aifriend learned`). Nothing is used before an admin approves it; rejected facts are never proposed again
- Fits the chat: does not answer every time (stays quiet when players talk among themselves, joins in when spoken to), picks up the tone, slang and atmosphere of the recent chat, and does not keep telling players where they are
- Public API (`AiFriendResponseEvent`, `AiFriendApi`) and PlaceholderAPI placeholders (`%aifriend_state%`, `%aifriend_mood%`, `%aifriend_relationship%`, `%aifriend_nickname%`, `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`)
- Admin tools: `/aifriend sleep`, `wake`, `simulate` (dry run, nothing is posted), `cost`, `status`
- Configurable bot name, reply language and a free-text `personalization`
- Goes to sleep when players seem annoyed; one player alone cannot silence it
- Never blocks the server: async requests with timeouts, backoff on failures, bounded queue
- Player chat is sent as clearly delimited data, never as instructions
- Commented `config.yml` that gains new options on update without touching your values; English and Czech messages
- Folia-compatible code (no `BukkitScheduler`)

## Quick start
1. Put the jar in `plugins/` and start the server.
2. In `plugins/ai-friend/config.yml` choose `provider.primary` (OpenAI, Claude, Ollama, custom) and put that provider's `api-key` under `provider.providers.<name>`. Without a key the bot stays silent and sends nothing.
3. `/aifriend reload`, then `/aifriend status`.

## Commands & Permissions
| Command | Permission | Description |
|---|---|---|
| `/aifriend` | `aifriend.use` (everyone) | Version and short help |
| `/aifriend status` | `aifriend.admin` (op) | State, provider, persona, masked API key, queue |
| `/aifriend reload` | `aifriend.reload` (op) | Reload config and messages (`/aifriendreload` still works) |
| `/ai <question>` | `aifriend.ask` (everyone) | Ask the bot directly |
| `/aifriend limits` | `aifriend.ask` (everyone) | Your remaining calls and cooldown |
| `/aifriend nickname [text\|clear]` | `aifriend.nickname` (everyone) | Pick the nickname the bot may use, or show how well it knows you |
| `/aifriend mute` / `unmute` | `aifriend.mute` (everyone) | Switch the bot off or on for yourself |
| `/aifriend memory` / `forget` | `aifriend.mute` (everyone) | List / delete the notes the bot keeps about you (long-term memory) |
| `/aifriend memory <player>` / `forget <player>` / `forget-all` | `aifriend.admin` (op) | List / delete notes of a player or of everybody |
| `/aifriend sleep [minutes]` / `wake` | `aifriend.sleep` (op) | Quiet the bot or wake it |
| `/aifriend simulate [nocall]` | `aifriend.simulate` (op) | Dry run without posting |
| `/aifriend cost` | `aifriend.cost` (op) | Token totals and estimated cost |
| `/aifriend teach <fact>` / `knowledge [list\|forget <n>]` | `aifriend.teach` (op) | Add / list / remove facts in the bot's knowledge |
| `/aifriend learned` | `aifriend.teach` (op) | Review GUI for facts learned from the chat (also `list`, `approve`, `reject`, `disable`, `delete`, `edit`) |

`aifriend.bypass-limits` (op) exempts a player from the cooldown and daily limits, `aifriend.bypass-supervision` (op) from the friendly chat hints.

## Configuration
Key options in `config.yml` (all commented): `provider.*` (primary, failover, cache and per-provider settings), `personas`, `memory`, `limits`, `cost`, `reply-filter`, `mentions`, `opt-out`, `events`, `storage` (SQLite or MariaDB), `long-term-memory`, `server-context`, `mood`, `relationships`, `boredom`, `supervision`, `knowledge` (with `knowledge.learning`), `enabled`, `chatbot-name`, `language`, `personalization`, `interval-seconds` (5-3600), `stop-when-ignored`, `sleep-minutes`, `chattiness` (`low`/`normal`/`high`), `max-queued-messages`, `max-reply-length`, `http.*` timeouts, `locale` (`en`/`cs`), `update-check`, `metrics`. Full reference in the [README](https://github.com/filasik/ai-friend#configuration).

## Privacy / data

**Only public chat is read.** Private messages (`/msg`, `/tell`, `/r`), other commands, cancelled messages and messages that a chat plugin delivers to only a part of the players (staff, party or local channels) are never collected or sent anywhere. The one exception is a question the player asks the bot on purpose with `/ai`. The plugin does not write chat lines to its own log.
What leaves your server depends on `provider.primary` (and the failover list). A request contains recent chat lines **including player names**, the prompt, optional conversation memory and event descriptions.

- **openai / anthropic**: sent directly to `api.openai.com` / `api.anthropic.com` with your key. Nobody in between.
- **ollama**: sent only to your Ollama server (local by default); nothing leaves the machine.
- **custom**: sent to the endpoint you configure (OpenRouter, your own server, ...).

**Long-term memory** (off by default) stores, in your own database, the player UUID and name and short notes (max 120 characters) the model wrote from what the player said about themselves, plus optionally an embedding of each note. To find them, an extra request sends recent chat lines (with player names) to your AI provider every `extract-every-exchanges` answers, and with embeddings notes and chat text also go to the embedding service. The model is told to keep nothing sensitive and notes that look like contact data are dropped, but check `/aifriend memory <player>` if in doubt. Players delete their notes with `/aifriend forget` or by asking the bot in chat to forget something (that extraction request also sends the asking player's stored notes and the bot's last few messages to your provider), muting the bot stops collection (and deletes the notes by default), `retention-days` removes old ones.

**Server knowledge** (off by default): the entries of your `knowledge.txt`, of the taught facts and of the built-in self-description that fit the players' words are sent to your AI provider with the chat lines; nothing about players is stored there. **Learning from the chat** (off by default) uses the same extraction request as long-term memory and keeps each fact with the last five player names that mentioned it in `knowledge-learned.json`.

**World awareness** (off by default): *server context* adds the time, weather, players online and, for the players who wrote, their world, biome and nearby hostile mobs (no coordinates) to every request; *relationships* store the player UUID, last name, a counter, first/last time and an optional nickname in your database (deleted by `/aifriend forget` and by muting the bot); *chat supervision* only looks at public lines in memory, makes no request and stores nothing.

Chat of your players is therefore sent to the provider you chose (never to the plugin author) unless you use a local model; mention it in your server rules or privacy policy. API keys stay in `config.yml` and are masked in output. Nothing is sent while no provider is usable or the bot is disabled; muted players are never stored or sent. The only other network calls are anonymous bStats statistics (`metrics: false` turns them off) and the GitHub update check (`update-check: false`).

## Integrations
- **API for plugins:** `AiFriendResponseEvent` (asynchronous, cancellable, `setText`) fires right before the bot posts; `AiFriendApi` is available from the Bukkit `ServicesManager`.
- **PlaceholderAPI** (soft dependency): `%aifriend_state%`, `%aifriend_mood%` (happy/sleepy/confused/off), `%aifriend_lastmessage%`, `%aifriend_provider%`, `%aifriend_queued%`, `%aifriend_muted%`, `%aifriend_calls_left%`.

## Compatibility
- Paper 26.3+ (Java 25). Not Spigot or Bukkit.
- Needs outbound HTTPS to your chosen provider (not for a local Ollama). The first start downloads the plugin's libraries (database drivers, connection pool) from Maven Central.

## Support
Issues: https://github.com/filasik/ai-friend/issues · Web: https://github.com/filasik/ai-friend · Authors: Filasik (Discord `filasik`), Patr1xCZ (Discord `paprik_man`)

This plugin is free and has no in-game perks. Source code is MIT licensed.
