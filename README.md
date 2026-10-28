<div align="center">

<img src="docs/assets/icon.svg" width="112" alt="AI Friend">

# AI Friend

**A friendly AI chat companion for Paper servers.**
It reads the public chat and posts short replies as a bot of your own name, language and personality, using OpenAI, Claude, a local Ollama model or any OpenAI-compatible server.

[![Paper](https://img.shields.io/badge/Paper-26.3-1f6feb?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-e0a21a?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-2ea043?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/filasik/ai-friend/build.yml?style=flat-square&label=build)](https://github.com/filasik/ai-friend/actions)

[Česky](README.cs.md)

</div>

---

> **Privacy first.** Recent chat lines (with player names) are sent to the AI service you choose when the bot is active. Read [Privacy and data](#privacy-and-data) before you enable it on a public server.

## Highlights

- **Chat companion.** Collects what players write and answers with one short message per interval, as `<chatbot-name>: <reply>`.
- **Your choice of AI.** The old proxy (default, unchanged), OpenAI, Anthropic Claude, a local Ollama server or any OpenAI-compatible endpoint, with a failover chain and an optional answer cache.
- **Chat intelligence, all optional and off by default.** Personas, conversation memory, rate and daily limits, cost estimate, reply filter, direct questions (`/ai`, `@Name`), per-player mute and reactions to game events.
- **Long-term memory** (optional, off by default). The bot remembers what players told it about themselves, in SQLite or MariaDB, and players can list and delete their notes.
- **Integrations.** A public API (`AiFriendResponseEvent`, `AiFriendApi`) and PlaceholderAPI placeholders such as `%aifriend_mood%`.
- **Yours to shape.** Bot name, reply language and a free-text `personalization` (your website, jokes, house rules, ...).
- **Knows when to be quiet.** If players seem annoyed with the bot, it sleeps for `sleep-minutes`. One player alone cannot silence it for everyone.
- **Never blocks the server.** Network calls are asynchronous with timeouts, failures back off exponentially and every error is caught and logged as one clear line.
- **Free trial.** The special key `trial` (proxy provider) works for 3 days, no account needed.
- **Low maintenance.** Commented `config.yml` that is never overwritten and gains new options on update, editable messages (English and Czech), update notices from GitHub.
- **Folia-compatible code.** No `BukkitScheduler`; everything goes through region/global/async schedulers.

## Quick start

1. Put `ai-friend-2.1.0.jar` in `plugins/` and start the server.
2. Open `plugins/ai-friend/config.yml` and set `api-key: "trial"` (3 days free, via the proxy) or pick a provider, see [Provider setup](#provider-setup).
3. Run `/aifriend reload`, then `/aifriend status` to check the bot is running.

> The default placeholders (`your_openai_api_key_here`, `your_anthropic_api_key_here`) mean "not set": the bot stays silent and nothing is sent anywhere.

## Requirements

| | |
|---|---|
| Server | Paper **26.3** or newer (**Paper only**; Spigot and plain Bukkit are not supported since 2.0.0) |
| Java | **25** |
| Network | Outbound HTTPS to the provider you use: `gpt.netuj.eu` (proxy), `api.openai.com`, `api.anthropic.com`, your own endpoint, or nothing outside your network for a local Ollama. Plus `api.github.com` for update checks |
| Key | `trial`, an OpenAI key, an Anthropic key, or none for Ollama / keyless local servers |

The old 1.x jar (Spigot-compatible) is still available on the [GitHub releases](https://github.com/filasik/ai-friend/releases) page.

## Installation

1. Download the jar from [Modrinth](https://modrinth.com/plugin/aifriend), [Hangar](https://hangar.papermc.io/filasik/AIFriend-Personal_Assistant) or [GitHub Releases](https://github.com/filasik/ai-friend/releases).
2. Put it into the `plugins/` folder of a Paper server and start the server.
3. Edit `plugins/ai-friend/config.yml` (see [Configuration](#configuration)) and run `/aifriend reload`.

The first start needs internet access because the plugin loader downloads its libraries (database drivers and connection pool) from Maven Central once.

## How it works

1. Every chat message is added to a bounded queue (`max-queued-messages`). Muted players' lines are skipped.
2. Every `interval-seconds` the queued messages are sent as one batch to the selected provider: rules and persona as the system message, the chat lines as clearly delimited data in the user message.
3. The reply is passed through the reply filter and posted in chat on the main thread as `<chatbot-name>: <reply>`.
4. Messages that arrive while a request is running are kept for the next batch. If a request fails, the batch is kept and retried after a backoff of 1, 2, 4, ... up to 15 minutes. With a failover chain the next provider is tried first.

Players can also talk to the bot directly (`/ai <question>`, `@AI Friend ...`), which answers immediately and does not wait for the interval. Direct questions, events and the timed answers share the same limits, filter and memory.

**Going to sleep.** With `stop-when-ignored: true` the model may report that players are annoyed with the bot. The plugin then stays quiet for `sleep-minutes`. It only honors this when at least 2 different players took part in the batch (or all online players, if fewer than 2 are online), so one player cannot silence the bot for everyone. Admins can also use `/aifriend sleep` and `/aifriend wake`.

**Untrusted input.** Player text is JSON-escaped and sent as clearly delimited data, never as instructions to the model, and the same goes for the conversation memory. A model can still be talked around by a creative player; treat the bot like any public chat participant.

## Provider setup

Set `provider.primary` and fill in that provider under `provider.providers`. Only what you change needs to be listed. Run `/aifriend reload` afterwards and check `/aifriend status`.

| Provider | `provider.primary` | Needs | Chat goes to |
|---|---|---|---|
| Proxy (default) | `proxy` | top-level `api-key` (`trial` or OpenAI key) and `model` | `gpt.netuj.eu`, then OpenAI |
| OpenAI | `openai` | `providers.openai.api-key` | `api.openai.com` |
| Anthropic Claude | `anthropic` | `providers.anthropic.api-key` | `api.anthropic.com` |
| Ollama | `ollama` | a running Ollama, no key | your Ollama server (default `localhost`) |
| Custom (OpenAI-compatible) | `custom` | `base-url` and `model`, key if the server wants one | the `base-url` you set |

**OpenAI**

```yaml
provider:
  primary: openai
  providers:
    openai:
      api-key: "sk-..."
      model: gpt-4o-mini
```

**Anthropic Claude**

```yaml
provider:
  primary: anthropic
  providers:
    anthropic:
      api-key: "sk-ant-..."
      model: claude-haiku-5-5
```

**Ollama (fully local)**

```yaml
provider:
  primary: ollama
  providers:
    ollama:
      model: llama3.2            # run "ollama pull llama3.2" first
      base-url: http://localhost:11434
```

**OpenRouter or any OpenAI-compatible server (LM Studio, vLLM, ...)**

```yaml
provider:
  primary: openrouter
  providers:
    openrouter:
      type: custom               # a name of your own needs a type
      base-url: https://openrouter.ai/api/v1
      api-key: "sk-or-..."
      model: openai/gpt-4o-mini
```

For a server without a key (LM Studio, e.g. `base-url: http://localhost:1234/v1`) leave `api-key` empty. You can use the built-in `custom` name instead of choosing your own.

**Failover.** When the primary fails (error, timeout, rate limit, bad key), the providers in `failover` are tried in order. A failing provider is skipped for `failover-cooldown-seconds`. A provider with missing settings is skipped with a warning at startup.

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

**Cache.** `provider.cache.enabled: true` remembers answers to identical requests (in memory, lost on restart). Chat is rarely identical, so it is off by default and mostly useful for repeated direct questions without memory.

Notes: redirects are never followed (a key cannot be forwarded to another host); `max-output-tokens` of the primary provider sets the answer size limit; a very small value can produce empty answers with reasoning models.

## Chat features

All of these are **off by default** and switched on separately, so upgrading changes nothing until you decide.

- **Personas** (`personas`): a list of personalities (name, prompt, optional `worlds`, `time: any|day|night`, `weight`). For each request one matching persona is picked by weight and its prompt is added to the system prompt. Night means world time 13000-22999.
- **Memory** (`memory`): the last `exchanges` exchanges are sent as chat history, bounded by `max-chars`. Costs more tokens per request. The history is kept in memory only.
- **Limits** (`limits`): `cooldown-seconds` between two direct requests of a player, daily calls/tokens per player and for the whole server (`0` = unlimited). Days end at midnight server time. A call counts when the slot is taken, so a failed request still used one. Players with `aifriend.bypass-limits` are not limited. `/aifriend limits` shows what is left. Counters are in memory only.
- **Cost** (`cost`): `/aifriend cost` shows today's and total tokens and an estimated price from `input-price-per-million` and `output-price-per-million` (copy them from your provider's price list; the currency is yours). `warn-daily-cost` warns admins with `aifriend.cost` once a day. The estimate is only as good as the token counts the provider reports; totals restart with the server.
- **Reply filter** (`reply-filter`): `max-length`, `banned-words` (whole words, `banned-mode: replace` turns them into `***`, `drop` posts nothing), `block-urls`. `§` codes and control characters are always removed. The URL detection is a deterrent, not a security boundary.
- **Direct questions** (`mentions`): `/ai <question>`, `@AI Friend question`, `AI Friend, question`, plus extra `triggers`. With `react-anywhere` the name anywhere in a line counts. With `direct-answer: false` a mention is only normal chat.
- **Opt-out** (`opt-out`): `/aifriend mute` and `unmute` per player. Muted players' lines are never stored or sent, and `/ai` tells them to unmute first. The list is saved in `muted-players.yml` in the plugin folder.
- **Game events** (`events`): the bot may comment on a death, an advancement, the first join of a player, an Ender Dragon or Wither kill and, if enabled, big explosions (`explosion-min-power`). Only one reaction per `cooldown-seconds` for the whole server. Explosions can be frequent on busy survival servers, so they are off by default.

- **Long-term memory** (`long-term-memory`, `storage`): notes about players that survive restarts. See [Long-term memory](#long-term-memory) and [Database](#database).

Use `/aifriend simulate` to see what the next request would look like and what the bot would answer, without posting anything.

## Long-term memory

Optional and **off by default** (`long-term-memory.enabled`). The bot remembers short facts that players told about themselves (their builds, preferences, plans) and brings them back into later answers, so it can say "how is the castle going?" after a restart.

**How it works.**

1. After every `extract-every-exchanges` answered exchanges (default 6) the plugin makes one extra request to your AI provider (same provider chain, counted by the server-wide limits and by the cost estimate). The model gets the recent chat lines of the players and returns at most 3 notes of at most 120 characters, only durable facts a player said about themselves. It is told to ignore instructions in the chat and to keep nothing sensitive (real name, address, age, contact data, passwords). The plugin also drops every note that looks like an e-mail address, phone number, link or IP address.
2. Notes are stored by player UUID in the [database](#database) (`storage`), at most `max-notes-per-player` per player (the least recently used are dropped) and, with `retention-days`, no longer than that without being used. A cleanup runs once a day.
3. For every answer the best `retrieval-top-k` notes about the players in the current chat are added to the request as `{"note":"...","about":"Name"}` lines in the **data** block. Notes come from player text, so they are untrusted data like chat lines, never part of the system rules. Extraction never delays an answer: it runs afterwards on its own task and is skipped while the bot is rate limited, asleep or backing off.

**Which notes fit?** With `embeddings.backend: none` (default) notes are matched by shared words, which is free and works with every provider. Set `embeddings.backend` to the name of an entry under `provider.providers` (`openai`, `custom` for any OpenAI-compatible server, or `ollama`) to match by meaning instead; the default models are `text-embedding-3-small` (OpenAI-compatible) and `nomic-embed-text` (Ollama), `embeddings.model` and `embeddings.base-url` override them. Anthropic and the proxy have no embeddings API, so they fall back to words. If the embedding service fails, words are used for a minute. Every note and every request text is also sent to the embedding service.

**Privacy.** See [Privacy and data](#privacy-and-data). Players control their notes:

- `/aifriend memory` lists the notes about you, `/aifriend forget` deletes them (works even when you have muted the bot).
- Muting the bot (`/aifriend mute`) stops collecting notes about you and, with `delete-on-mute: true` (default), deletes them.
- Admins: `/aifriend memory <player>`, `/aifriend forget <player>` and `/aifriend forget-all`. The player name is matched online or by the name stored with the notes, so offline players work.

`/aifriend reload` applies every option, including switching the feature on or off.

## Database

The `storage` section configures the database for features that keep data (currently only long-term memory; nothing is created while they are off). Two types:

- `sqlite` (default): one file in the plugin folder (`storage.sqlite.file`, default `ai-friend.db`). No setup.
- `mariadb`: a MariaDB or MySQL server (`storage.mariadb.*`: host, port, database, user, password, pool-size, `ssl-mode`). Use it to share notes between servers of a network. The tables are prefixed `aifriend_` so they can live in a shared database.

The JDBC drivers and the connection pool are downloaded from Maven Central together with the other libraries on the first start. All database access is asynchronous, never on the main thread. The schema is versioned (table `aifriend_schema_version`) and upgraded automatically on start; later releases only append new steps. If the database cannot be opened, one line is logged and only long-term memory stays off, the bot keeps running.

## Integrations

**Public API for other plugins.**

- `AiFriendResponseEvent` is fired right before the bot posts an answer. It is **asynchronous** (off the main thread, so listeners must be thread safe and not touch the world) and cancellable; `setText(...)` changes what is posted (the result is cleaned and cut to `max-reply-length` again). It tells the bot name, the text, the source (`TIMED` or `DIRECT`) and the asking player (UUID or null).
- `AiFriendApi` is registered in the Bukkit `ServicesManager` once startup has finished: `state()` (`RUNNING`, `DISABLED`, `NO_KEY`, `SLEEPING`, `BACKOFF`), `lastMessage()`, `provider()`, `queuedLines()`, `sleep(Duration)` and `wake()`.

```java
AiFriendApi api = Bukkit.getServicesManager().load(AiFriendApi.class); // null until AI Friend has started
```

**PlaceholderAPI** (soft dependency, nothing to configure). When PlaceholderAPI is installed, the expansion `aifriend` is registered:

| Placeholder | Value |
|---|---|
| `%aifriend_state%` | `running`, `disabled`, `no-key`, `sleeping`, `backoff` |
| `%aifriend_mood%` | `happy` (running), `sleepy` (sleeping), `confused` (waiting after an error), `off` |
| `%aifriend_lastmessage%` | The last thing the bot said |
| `%aifriend_provider%` | Id of the provider that answers (`-` if none) |
| `%aifriend_queued%` | Chat lines waiting for the next answer |
| `%aifriend_muted%` | `true` / `false`: the player has muted the bot |
| `%aifriend_calls_left%` | Questions the player has left today, or `unlimited` |

## Commands

`/aifriend`. Extra aliases can be added with `command.aliases` (restart needed). `/aifriendreload` is kept as a legacy alias for `/aifriend reload`. Console works for admin commands; tab completion is available.

| Command | What it does | Permission |
|---|---|---|
| `/aifriend` | Plugin version and short help | `aifriend.use` |
| `/aifriend status` | State (enabled, sleeping, backoff), provider, persona, memory, muted players, masked API key, queue size | `aifriend.admin` |
| `/aifriend reload` | Reload `config.yml` and messages | `aifriend.reload` |
| `/ai <question>` | Ask the bot directly (players) | `aifriend.ask` |
| `/aifriend limits` | Your remaining calls, tokens and cooldown (players) | `aifriend.ask` |
| `/aifriend mute` / `unmute` | Switch the bot off / on for yourself (players, needs `opt-out.enabled`) | `aifriend.mute` |
| `/aifriend memory` | List the notes the bot remembers about you (needs long-term memory) | `aifriend.mute` |
| `/aifriend forget` | Delete your own notes (works even when you muted the bot) | `aifriend.mute` |
| `/aifriend memory <player>` | List the notes about a player (also offline) | `aifriend.admin` |
| `/aifriend forget <player>` | Delete a player's notes | `aifriend.admin` |
| `/aifriend forget-all` | Delete all notes | `aifriend.admin` |
| `/aifriend sleep [minutes]` | Make the bot quiet for 1-1440 minutes (default `sleep-minutes`) | `aifriend.sleep` |
| `/aifriend wake` | Wake the bot up | `aifriend.sleep` |
| `/aifriend simulate [nocall]` | Dry run: shows persona, request, history size, token estimate, what blocks a reply and the filtered answer. `nocall` skips the provider call so it costs nothing | `aifriend.simulate` |
| `/aifriend cost` | Token totals and estimated cost (needs `cost.enabled`) | `aifriend.cost` |

## Permissions

| Permission | Default | |
|---|---|---|
| `aifriend.use` | everyone | Basic `/aifriend` info |
| `aifriend.admin` | op | `/aifriend status`, update notices, `/aifriend forget <player>`, `forget-all`, `memory <player>` |
| `aifriend.reload` | op | `/aifriend reload` |
| `aifriend.mute` | everyone | `/aifriend mute`, `unmute`, and your own `memory` and `forget` |
| `aifriend.ask` | everyone | `/ai`, `/aifriend limits`, addressing the bot in chat |
| `aifriend.sleep` | op | `/aifriend sleep`, `wake` |
| `aifriend.simulate` | op | `/aifriend simulate` |
| `aifriend.cost` | op | `/aifriend cost`, daily cost warning |
| `aifriend.bypass-limits` | op | Not affected by cooldown and daily limits |

## Configuration

`plugins/ai-friend/config.yml` is fully commented. It is never overwritten; when you update the plugin, options added by the new version are appended with their comments (`config-version`), your values stay and a backup `config.yml.bak-v<old>` is saved first. Invalid values fall back to the default with a warning in the log. `/aifriend reload` applies most changes (aliases need a restart).

| Key | Default | Description |
|---|---|---|
| `config-version` | `3` | Managed by the plugin, do not change |
| `locale` | `en` | Language of plugin messages: `en` or `cs` |
| `update-check` | `true` | Check GitHub releases for a newer version (async, cached) |
| `metrics` | `true` | Anonymous bStats statistics |
| `command.aliases` | `[]` | Extra aliases for `/aifriend` (restart needed) |
| `enabled` | `true` | Switch the bot on or off |
| `api-key` | `your_openai_api_key_here` | Proxy provider: `trial` for the 3-day free trial, or your OpenAI key. The default value means "not set" |
| `model` | `gpt-4o-mini` | Proxy provider: model name. Ignored with the `trial` key |
| `chatbot-name` | `AI Friend` | Name shown in front of replies |
| `language` | `English` | Language the bot replies in (free text, e.g. `Czech`) |
| `personalization` | empty | Free-text instructions for the bot: your website, jokes, tone |
| `interval-seconds` | `60` | How often the batch is sent. Allowed range 5 to 3600 |
| `stop-when-ignored` | `true` | Let the bot go to sleep when players seem annoyed |
| `sleep-minutes` | `30` | How long the bot sleeps |
| `max-queued-messages` | `50` | Upper bound of the chat queue (5-500) |
| `max-reply-length` | `256` | Longer replies are shortened (20-1000) |
| `http.connect-timeout-seconds` | `10` | HTTP connect timeout (1-120) |
| `http.read-timeout-seconds` | `30` | HTTP read timeout (1-120) |
| `provider.primary` | `proxy` | `proxy`, `openai`, `anthropic`, `ollama`, `custom` or a name you define |
| `provider.failover` | `[]` | Providers to try, in order, when the primary fails |
| `provider.failover-cooldown-seconds` | `60` | How long a failing provider is skipped |
| `provider.cache.enabled` | `false` | Remember answers to identical requests |
| `provider.cache.max-entries` | `200` | Cache size |
| `provider.cache.ttl-minutes` | `10` | How long a cached answer is used |
| `provider.providers.openai.api-key` | placeholder | OpenAI key |
| `provider.providers.openai.model` | `gpt-4o-mini` | OpenAI model |
| `provider.providers.openai.base-url` | `https://api.openai.com/v1` | Change only for a compatible gateway |
| `provider.providers.openai.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.anthropic.api-key` | placeholder | Anthropic key |
| `provider.providers.anthropic.model` | `claude-haiku-5-5` | Claude model |
| `provider.providers.anthropic.base-url` | `https://api.anthropic.com` | |
| `provider.providers.anthropic.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.ollama.model` | `llama3.2` | Local model |
| `provider.providers.ollama.base-url` | `http://localhost:11434` | |
| `provider.providers.ollama.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.custom.model` | empty | Required for `custom` |
| `provider.providers.custom.base-url` | empty | Required, e.g. `https://openrouter.ai/api/v1` |
| `provider.providers.custom.api-key` | empty | Leave empty for servers without a key |
| `provider.providers.custom.max-output-tokens` | `200` | 16-4000 |
| `provider.providers.<name>.type` | | For your own names: which kind to behave like, e.g. `custom` |
| `personas.enabled` | `false` | Use personas |
| `personas.list` | two examples | Each: `name`, `prompt`, `worlds` (empty = all), `time` (`any`/`day`/`night`), `weight` (0-1000) |
| `memory.enabled` | `false` | Send conversation history |
| `memory.exchanges` | `5` | Remembered exchanges (0-50) |
| `memory.max-chars` | `2000` | Hard limit of remembered characters (100-20000) |
| `limits.enabled` | `false` | Apply rate limits |
| `limits.cooldown-seconds` | `30` | Wait between two requests of a player |
| `limits.player-daily-calls` | `50` | Per player and day, `0` = unlimited |
| `limits.player-daily-tokens` | `0` | Per player and day |
| `limits.global-daily-calls` | `0` | Whole server and day (also automatic answers and events) |
| `limits.global-daily-tokens` | `0` | Whole server and day |
| `cost.enabled` | `false` | Track tokens and estimate cost |
| `cost.input-price-per-million` | `0.15` | Price of 1M input tokens |
| `cost.output-price-per-million` | `0.60` | Price of 1M output tokens |
| `cost.warn-daily-cost` | `0` | Warn admins once a day at this cost, `0` = never |
| `reply-filter.enabled` | `false` | Apply banned words, URL removal and `max-length` |
| `reply-filter.max-length` | `256` | Overrides `max-reply-length` when enabled (20-1000) |
| `reply-filter.banned-words` | `[]` | Whole words, case-insensitive |
| `reply-filter.banned-mode` | `replace` | `replace` or `drop` |
| `reply-filter.block-urls` | `false` | Remove links and web addresses |
| `mentions.enabled` | `false` | `/ai` and mentions in chat |
| `mentions.direct-answer` | `true` | Answer a mention immediately |
| `mentions.triggers` | `[]` | More names that call the bot |
| `mentions.react-anywhere` | `false` | The name anywhere in a line counts |
| `opt-out.enabled` | `false` | Allow `/aifriend mute` |
| `opt-out.file` | `muted-players.yml` | Where muted players are saved |
| `events.enabled` | `false` | React to game events |
| `events.cooldown-seconds` | `30` | One reaction per this time, server-wide |
| `events.death` | `true` | Player deaths |
| `events.advancement` | `true` | Advancements announced in chat |
| `events.first-join` | `true` | A player joins for the first time |
| `events.boss-kill` | `true` | Ender Dragon and Wither kills |
| `events.explosion` | `false` | Big explosions |
| `events.explosion-min-power` | `6` | Smallest explosion that counts |
| `storage.type` | `sqlite` | `sqlite` or `mariadb` (MySQL works too) |
| `storage.sqlite.file` | `ai-friend.db` | SQLite file in the plugin folder |
| `storage.sqlite.pool-size` | `3` | Database connections (1-32) |
| `storage.mariadb.host` / `port` | `localhost` / `3306` | MariaDB server |
| `storage.mariadb.database` / `user` / `password` | `aifriend` / `aifriend` / `change-me` | Credentials; keep `config.yml` private |
| `storage.mariadb.pool-size` | `6` | Database connections (1-64) |
| `storage.mariadb.ssl-mode` | empty | e.g. `trust` for a self-signed certificate |
| `long-term-memory.enabled` | `false` | Remember facts about players ([details](#long-term-memory)) |
| `long-term-memory.extract-every-exchanges` | `6` | One extra request to the provider per this many answers (1-1000) |
| `long-term-memory.max-notes-per-player` | `20` | Least recently used notes are dropped (1-500) |
| `long-term-memory.retention-days` | `0` | Delete notes unused for this long, `0` = keep (0-36500) |
| `long-term-memory.retrieval-top-k` | `5` | Notes added to one request (1-50) |
| `long-term-memory.delete-on-mute` | `true` | Delete a player's notes when he mutes the bot |
| `long-term-memory.embeddings.backend` | `none` | `none` (word matching) or a name under `provider.providers` |
| `long-term-memory.embeddings.model` | empty | Empty = `text-embedding-3-small` / `nomic-embed-text` |
| `long-term-memory.embeddings.base-url` | empty | Empty = the provider's `base-url` |

Example:

```yaml
enabled: true
api-key: "trial"
chatbot-name: "Buddy"
language: "Czech"
personalization: "You are the friendly helper of the server example.com. Mention /spawn to new players."
interval-seconds: 90
```

## Messages and localization

Messages live in `plugins/ai-friend/messages_<locale>.yml` (MiniMessage) and are selected with `locale`. English (`en`) and Czech (`cs`) are bundled. You can edit the files freely; a missing key falls back to the bundled text of that language and then to English. To add a language, copy `messages_en.yml` to `messages_<code>.yml` and set `locale: <code>`.

The language the *bot speaks* is a different setting: `language`.

## Privacy and data

**Only public chat is read.** Private messages (`/msg`, `/tell`, `/r`), other commands, cancelled messages and messages that a chat plugin delivers to only a part of the players (staff, party or local channels) are never collected or sent anywhere. The one exception is a question the player asks the bot on purpose with `/ai`. The plugin does not write chat lines to its own log.

What leaves your server depends on `provider.primary` (and `provider.failover`). A request contains the recent chat lines since the last request **including player names**, the prompt (bot name, language, personalization, persona), the conversation memory when enabled, and short descriptions of game events when enabled. Nothing is sent while no provider is usable or `enabled` is `false`.

| Provider | What is sent, and where |
|---|---|
| `proxy` | HTTPS to `https://gpt.netuj.eu/api/ai_friend.php`, **operated by the plugin author**, who forwards it to OpenAI. Additionally: your `api-key` (`trial` or your OpenAI key), the `model` name and the address of your server as seen by the proxy (the connecting IP address) |
| `openai` | HTTPS directly to `api.openai.com` (or your `base-url`) with your key as an `Authorization` header. No proxy involved. OpenAI's own data terms apply |
| `anthropic` | HTTPS directly to `api.anthropic.com` with your key in the `x-api-key` header. No proxy involved. Anthropic's own data terms apply |
| `ollama` | Only to your Ollama server (`localhost` by default). Nothing leaves your machine if Ollama runs there; if you point `base-url` at another host, the chat goes there |
| `custom` | To the `base-url` you configured (OpenRouter, your own server, ...). The operator of that endpoint sees the chat |

Please note:

- **Chat of your players is sent to a third party** unless you use a local Ollama or your own server. If you run a public server, mention it in your rules or privacy policy and in the privacy notices that your jurisdiction requires.
- With a failover chain, the chat goes to whichever provider ends up answering; every member of the chain is a potential recipient.
- API keys live only in `config.yml`, in plain text like any plugin setting. Keep that file private. Keys are masked in `/aifriend status` and removed from error messages and the log; they are sent only in request headers (or, for the proxy, in the request body). Redirects are not followed.
- Muted players (`opt-out`) are never stored or sent. The mute list (`muted-players.yml`) holds player UUIDs on your server.
- **Long-term memory** (off by default) is the one feature that stores player data. It stores, in your own database (`storage`): the player UUID, the player name, a short note (max 120 characters, written by the model from what the player said about themselves), when it was made and last used, and optionally an embedding vector of the note. Nothing else of the chat is saved. To find the notes, **an extra request sends the recent chat lines (with player names) to your AI provider every `extract-every-exchanges` answers**, and with embeddings the notes and chat text also go to the embedding service. Tell your players. The model is told to keep nothing sensitive, notes that look like contact data are dropped by the plugin, but a model can make mistakes: review `/aifriend memory <player>` if in doubt. Players see and delete their notes with `/aifriend memory` and `/aifriend forget`, admins with `forget <player>` and `forget-all`, muting stops collection and (by default) deletes the notes, and `retention-days` removes old ones. With MariaDB the notes live on your database server and are shared by all servers that use it.
- Conversation memory, limits, cost totals and the cache are kept in memory only and are lost on restart (long-term memory is the exception, see above).
- Besides the above, the only other network calls are anonymous [bStats](https://bstats.org) statistics (turn off with `metrics: false`) and the GitHub update check (turn off with `update-check: false`). There is no other telemetry.

## Upgrading

**From 2.0.0.** Drop the new jar in place of the old one. Your `config.yml` gets `config-version: 3` and the new sections (`provider`, `personas`, `memory`, `limits`, `cost`, `reply-filter`, `mentions`, `opt-out`, `events`, `storage`, `long-term-memory`) with comments; a backup `config.yml.bak-v1` is saved first. Long-term memory and the database stay off/unused until you enable them. Nothing changes until you enable something: the proxy stays the primary provider, and all chat features are off. To use OpenAI or Claude directly, follow [Provider setup](#provider-setup).

**From 1.x.** 2.0.0 was a rewrite. Drop the new jar in place of the old one; the folder `plugins/ai-friend/` is the same, so your settings are kept.

- Old keys are migrated automatically on the first start (the old file is backed up as `config.yml.bak-v0`): `openai_api_key` -> `api-key`, `gpt_version` -> `model`, `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`; `enabled` and `personalization` keep their names.
- **Paper is required now.** 1.x also ran on Spigot; 2.x does not. Stay on 1.4.1 if you run Spigot.
- `/aifriend` now has subcommands; `/aifriendreload` still works. Commands need permissions instead of op status.

See the [CHANGELOG](CHANGELOG.md) for details.

## Not done yet

Direct OpenAI/Claude/Ollama/custom calls, the chat features above, long-term memory, the public API and the PlaceholderAPI expansion are implemented. Still planned (see [ROADMAP.md](ROADMAP.md)): a Dynmap hook, persistence of the cache, limits and cost totals across restarts, and the later phases (world awareness, games, player help, admin tools).

## FAQ and troubleshooting

**The bot says nothing.** Check `/aifriend status`:
- *API key not set* / *no usable provider*: set the key of your provider (a default placeholder means unset) and `/aifriend reload`. Warnings in the log say what is missing.
- *Disabled*: `enabled: false` in the config.
- *Sleeping*: the bot went to sleep after annoyed players, or an admin used `/aifriend sleep`; `/aifriend wake` wakes it.
- *Backoff*: recent requests failed, the next try is delayed (up to 15 minutes). The log has one line per failure with the reason.
- Nobody wrote in chat since the last interval (nothing is sent without messages), or everybody who did is muted.
- Rate limits are reached (`/aifriend limits`).

**`401 Unauthorized` in the log.** The key was rejected. Check for typos or whitespace, that the key is active and has credit, that it belongs to the provider you selected, and that you did not leave the default placeholder.

**The trial stopped working.** The `trial` key is valid for 3 days. Use your own key afterwards, with the proxy or directly.

**Does it work on Spigot or Bukkit?** No. Use 1.4.1 or move to Paper.

**Does it work on Folia?** The code uses Folia-compatible schedulers; the plugin is declared Paper-based and has not been tested on a Folia server yet.

**Can I use it without sending chat to your proxy?** Yes: set `provider.primary` to `openai`, `anthropic`, `ollama` or `custom`. Chat then goes only to that provider. With Ollama on the same machine nothing leaves your server.

**Which provider should I choose?** For the lowest cost and effort `openai` with `gpt-4o-mini` or `anthropic` with the small Claude model; for privacy a local `ollama` model (needs a capable machine); for choice of many models `custom` with OpenRouter.

**`/ai` says it is switched off.** Enable `mentions.enabled` in the config. Same for `/aifriend mute` and `opt-out.enabled`.

**The bot answers too often / costs too much.** Raise `interval-seconds`, enable `limits` and `cost`, keep `memory` off or small, and use a cheap model.

**Does the bot read private messages or commands?** It collects public chat messages only.

## Building from source

```bash
./gradlew build         # JDK 25, compiles with warnings as errors, runs tests, jar in build/libs
./gradlew test          # unit tests only
./gradlew runServer     # local Paper test server (-PrunDir=<path> reuses an existing server directory)
```

## Roadmap

Planned work is tracked in [ROADMAP.md](ROADMAP.md) (written in Czech).

## Authors - Support

- Filasik - Discord ID: filasik
- Patr1xCZ - Discord ID: paprik_man

Bugs and ideas: [GitHub issues](https://github.com/filasik/ai-friend/issues). Website: [minebook.eu](https://minebook.eu).

The plugin is free and has no in-game perks. Donation links will be added here when they exist.

## License

[MIT](LICENSE) © Filip Skerik
