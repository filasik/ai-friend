# Privacy and Data

AI Friend has **no server of its own** and nothing goes through the plugin author. Chat goes only to the provider you configure.

## What is read

**Only public chat.** Private messages (`/msg`, `/tell`, `/r`), other commands, cancelled messages and messages a chat plugin delivers to only some players (staff, party or local channels) are never collected or sent. The one exception is a question a player asks the bot on purpose with `/ai`. The plugin does not write chat lines to its own log.

## What is sent, and where

A request contains the recent chat lines since the last request **including player names**, the prompt (bot name, language, personalization, persona), and, when enabled, conversation memory, game event descriptions, fitting knowledge entries, notes, relationships and server context. Nothing is sent while no provider is usable or `enabled` is `false`.

| Provider | Destination |
| :--- | :--- |
| `openai` | HTTPS directly to `api.openai.com` (or your `base-url`) with your key in the `Authorization` header. OpenAI's data terms apply |
| `anthropic` | HTTPS directly to `api.anthropic.com` with your key in the `x-api-key` header. Anthropic's data terms apply |
| `ollama` | Only your Ollama server (`localhost` by default). Nothing leaves your machine if Ollama runs there |
| `custom` | The `base-url` you configured. The operator of that endpoint sees the chat |

With a failover chain, the chat goes to whichever provider answers; every member of the chain is a potential recipient.

## Please note

- **Your players' chat is sent to a third party** unless you use a local Ollama or your own server. On a public server, mention it in your rules or privacy policy and in any notices your jurisdiction requires.
- API keys live only in `config.yml`, in plain text like any plugin setting. Keep that file private. Keys are masked in `/aifriend status`, removed from error messages and the log, and sent only to the provider they belong to. Redirects are not followed.
- Muted players are never stored or sent. `muted-players.yml` holds their UUIDs.

## Per feature

| Feature | Stored | Sent to the provider |
| :--- | :--- | :--- |
| Core bot | Nothing | Chat lines with player names |
| Conversation memory, limits, cost, cache | Memory only | Memory: past exchanges |
| [Long-term memory](Long-Term-Memory) | Your database: UUID, name, note (max 120 chars), dates, optional embedding vector | An extra request with recent chat lines every `extract-every-exchanges` answers; with embeddings, notes and chat text go to the embedding service. Asking the bot to forget also sends that player's notes and the bot's last messages |
| [Server knowledge](Server-Knowledge) | Your files only | Entries that fit the chat |
| [Learning from chat](Server-Knowledge#learning-from-the-chat) | `knowledge-learned.json`: each fact and the last five player names that mentioned it | Uses the extraction request: chat lines, fitting facts and knowledge entries |
| [Server context](World-Awareness-and-Personality#server-context) | Nothing | Time, weather, online count; for players who wrote: world, biome, nearby hostile mobs. Never coordinates |
| [Relationships](World-Awareness-and-Personality#relationships) | Your database: UUID, last name, counter, first/last time, nickname | Level and nickname with that player's lines |
| Mood, boredom | Counters in memory | Mood level as a hint |
| Chat supervision | Nothing (hashes of a player's last lines in memory for ten seconds) | Nothing, no request is made |

## Players' rights in practice

- `/aifriend memory` and `/aifriend forget` to see and delete their notes (also by asking the bot in chat to forget something).
- `/aifriend mute` stops collection and, by default, deletes their notes and relationship.
- Admins: `/aifriend forget <player>` and `/aifriend forget-all`; `retention-days` removes old notes.
- With MariaDB, notes live on your database server and are shared by all servers that use it.

## Other network calls

- Anonymous [bStats](https://bstats.org) statistics. Turn off with `metrics: false`.
- The GitHub update check. Turn off with `update-check: false`.
- Maven Central once on the first start to download libraries.

There is no other telemetry.
