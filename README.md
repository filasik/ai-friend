<div align="center">

<img src="docs/assets/icon.svg" width="112" alt="AI Friend">

# AI Friend

**A friendly AI chat companion for Paper servers.**
It reads the public chat and, once a minute, posts one short reply as a bot of your own name, language and personality.

[![Paper](https://img.shields.io/badge/Paper-26.3-1f6feb?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-e0a21a?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-2ea043?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/filasik/ai-friend/build.yml?style=flat-square&label=build)](https://github.com/filasik/ai-friend/actions)

[Česky](README.cs.md)

</div>

---

> **Privacy first.** Recent chat lines (with player names) are sent to a third-party service when the bot is active. Read [Privacy and data](#privacy-and-data) before you enable it on a public server.

## Highlights

- **Chat companion.** Collects what players write and answers with one short message per interval, as `<chatbot-name>: <reply>`.
- **Yours to shape.** Bot name, reply language and a free-text `personalization` (your website, jokes, house rules, ...).
- **Knows when to be quiet.** If players seem annoyed with the bot, it sleeps for `sleep-minutes`. One player alone cannot silence it for everyone (see [How it works](#how-it-works)).
- **Never blocks the server.** Network calls are asynchronous with timeouts, failures back off exponentially and every error is caught and logged as one clear line.
- **Free trial.** The special key `trial` works for 3 days, no account needed.
- **Low maintenance.** Commented `config.yml` that is never overwritten and gains new options on update, editable messages (English and Czech), update notices from GitHub.
- **Folia-compatible code.** No `BukkitScheduler`; everything goes through region/global/async schedulers.

## Quick start

1. Put `ai-friend-2.0.0.jar` in `plugins/` and start the server.
2. Open `plugins/ai-friend/config.yml` and set `api-key: "trial"` (3 days free) or your own OpenAI key.
3. Run `/aifriend reload`, then `/aifriend status` to check the bot is running.

> The key `your_openai_api_key_here` (the default) means "not set": the bot stays silent and nothing is sent anywhere.

## Requirements

| | |
|---|---|
| Server | Paper **26.3** or newer (**Paper only**; Spigot and plain Bukkit are not supported since 2.0.0) |
| Java | **25** |
| Network | Outbound HTTPS to `gpt.netuj.eu` (and `api.github.com` for update checks) |
| Key | `trial` or an OpenAI API key |

The old 1.x jar (Spigot-compatible) is still available on the [GitHub releases](https://github.com/filasik/ai-friend/releases) page.

## Installation

1. Download the jar from [Modrinth](https://modrinth.com/plugin/aifriend), [Hangar](https://hangar.papermc.io/filasik/AIFriend-Personal_Assistant) or [GitHub Releases](https://github.com/filasik/ai-friend/releases).
2. Put it into the `plugins/` folder of a Paper server and start the server.
3. Edit `plugins/ai-friend/config.yml` (see [Configuration](#configuration)) and run `/aifriend reload`.

The first start needs internet access because the plugin loader downloads its libraries from Maven Central once.

## How it works

1. Every chat message is added to a bounded queue (`max-queued-messages`).
2. Every `interval-seconds` the queued messages are sent as one batch to the AI backend, together with your settings (bot name, language, personalization).
3. The reply is posted in chat on the main thread as `<chatbot-name>: <reply>` (shortened to `max-reply-length` characters).
4. Messages that arrive while a request is running are kept for the next batch. If a request fails, the batch is kept and retried after a backoff of 1, 2, 4, ... up to 15 minutes.

**Going to sleep.** With `stop-when-ignored: true` the model may report that players are annoyed with the bot. The plugin then stays quiet for `sleep-minutes`. It only honors this when at least 2 different players took part in the batch (or all online players, if fewer than 2 are online), so one player cannot silence the bot for everyone.

**Untrusted input.** Player text is JSON-escaped and sent as clearly delimited data, never as instructions to the model. A model can still be talked around by a creative player; treat the bot like any public chat participant.

## Commands

`/aifriend`. Extra aliases can be added with `command.aliases` (restart needed). `/aifriendreload` is kept as a legacy alias for `/aifriend reload`. All commands work from the console, and tab completion is available.

| Command | What it does | Permission |
|---|---|---|
| `/aifriend` | Plugin version and short help | `aifriend.use` |
| `/aifriend status` | State (enabled, sleeping, backoff), masked API key (e.g. `sk-...abcd`), queue size | `aifriend.admin` |
| `/aifriend reload` | Reload `config.yml` and messages | `aifriend.reload` |

## Permissions

| Permission | Default | |
|---|---|---|
| `aifriend.use` | everyone | Basic `/aifriend` info |
| `aifriend.admin` | op | `/aifriend status`, update notices |
| `aifriend.reload` | op | `/aifriend reload` |

## Configuration

`plugins/ai-friend/config.yml` is fully commented. It is never overwritten; when you update the plugin, options added by the new version are appended with their comments (`config-version`), your values stay and a backup `config.yml.bak-v<old>` is saved first. Invalid values fall back to the default with a warning in the log.

| Key | Default | Description |
|---|---|---|
| `locale` | `en` | Language of plugin messages: `en` or `cs` |
| `update-check` | `true` | Check GitHub releases for a newer version (async, cached) |
| `metrics` | `true` | Anonymous bStats statistics |
| `command.aliases` | `[]` | Extra aliases for `/aifriend` (restart needed) |
| `enabled` | `true` | Switch the bot on or off |
| `api-key` | `your_openai_api_key_here` | `trial` for the 3-day free trial, or your OpenAI key. The default value means "not set" |
| `model` | `gpt-4o-mini` | Model name. Ignored with the `trial` key |
| `chatbot-name` | `AI Friend` | Name shown in front of replies |
| `language` | `English` | Language the bot replies in (free text, e.g. `Czech`) |
| `personalization` | empty | Free-text instructions for the bot: your website, jokes, tone |
| `interval-seconds` | `60` | How often the batch is sent. Allowed range 5 to 3600 |
| `stop-when-ignored` | `true` | Let the bot go to sleep when players seem annoyed |
| `sleep-minutes` | `30` | How long the bot sleeps |
| `max-queued-messages` | `50` | Upper bound of the chat queue (memory limit) |
| `max-reply-length` | `256` | Longer replies are shortened |
| `http.connect-timeout-seconds` | `10` | HTTP connect timeout |
| `http.read-timeout-seconds` | `30` | HTTP read timeout |

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

When the bot is active, AI Friend sends HTTPS requests to a proxy at `https://gpt.netuj.eu/api/ai_friend.php`. **The proxy is operated by the plugin author**, who forwards the request to the AI provider. A request contains:

- the recent chat lines since the last request **including player names**,
- the prompt (bot name, language, your personalization text),
- your `api-key` (`trial` or your OpenAI key),
- the `model` name,
- the address of your server as seen by the proxy (the connecting IP address).

Please note:

- **Chat of your players is sent to a third party** (the proxy and the AI provider behind it). If you run a public server, mention it in your rules or privacy policy and in the privacy notices that your jurisdiction requires.
- **Nothing is sent** while `api-key` is unset or `enabled` is `false`.
- Besides the above, the only other network calls are anonymous [bStats](https://bstats.org) statistics (turn off with `metrics: false`) and the GitHub update check (turn off with `update-check: false`). There is no other telemetry.
- Your API key is stored in plain text in `config.yml`, like any plugin setting. It is shown masked in `/aifriend status`.

Using your own OpenAI key still goes through the proxy. Direct OpenAI access and other providers are on the [roadmap](ROADMAP.md), not implemented yet.

## Upgrading from 1.x

2.0.0 is a rewrite. Drop the new jar in place of the old one; the folder `plugins/ai-friend/` is the same, so your settings are kept.

- Old keys are migrated automatically on the first start (the old file is backed up): `openai_api_key` -> `api-key`, `gpt_version` -> `model`, `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`; `enabled` and `personalization` keep their names.
- **Paper is required now.** 1.x also ran on Spigot; 2.0.0 does not. Stay on 1.4.1 if you run Spigot.
- `/aifriend` now has subcommands (`status`, `reload`); `/aifriendreload` still works. Commands need permissions instead of op status.

See the [CHANGELOG](CHANGELOG.md) for details.

## FAQ and troubleshooting

**The bot says nothing.** Check `/aifriend status`:
- *API key not set*: set `api-key` (default `your_openai_api_key_here` means unset) and `/aifriend reload`.
- *Disabled*: `enabled: false` in the config.
- *Sleeping*: the bot went to sleep after annoyed players; it wakes after `sleep-minutes`.
- *Backoff*: recent requests failed, the next try is delayed (up to 15 minutes). The log has one line per failure with the reason.
- Nobody wrote in chat since the last interval (nothing is sent without messages).

**`401 Unauthorized` in the log.** The key was rejected. Check for typos or whitespace, that the OpenAI key is active and has credit, and that you did not leave the default placeholder.

**The trial stopped working.** The `trial` key is valid for 3 days. Use your own OpenAI key afterwards.

**Does it work on Spigot or Bukkit?** No. Use 1.4.1 or move to Paper.

**Does it work on Folia?** The code uses Folia-compatible schedulers; the plugin is declared Paper-based and has not been tested on a Folia server yet.

**Can I use it without sending chat to your proxy?** Not yet. Direct provider access is on the roadmap.

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
