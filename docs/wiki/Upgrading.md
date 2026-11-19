# Upgrading

Drop the new jar in place of the old one and restart. `config.yml` is never overwritten: new options are appended with their comments, your values stay, and a backup `config.yml.bak-v<old>` is saved first. Database schemas are upgraded automatically. The full list of changes is in the [CHANGELOG](https://github.com/filasik/ai-friend/blob/main/CHANGELOG.md).

## Config versions

| Plugin version | `config-version` | Added |
| :--- | :--- | :--- |
| 3.1.0 - 3.1.1 | 8 | `knowledge.learning` |
| 3.0.3 - 3.0.4 | 7 | `chattiness` |
| 3.0.1 - 3.0.2 | 6 | `knowledge`, `server-context.share-percent` |
| 3.0.0 | 5 | Removed the `proxy` provider and top-level `api-key` / `model` |
| 2.2.0 | 4 | `server-context`, `mood`, `relationships`, `boredom`, `supervision` |
| 2.1.0 | 3 | `provider`, `personas`, `memory`, `limits`, `cost`, `reply-filter`, `mentions`, `opt-out`, `events`, `storage`, `long-term-memory` |

## From 2.x to 3.x (breaking)

The provider that 2.x used by default (a relay run by the plugin author) and its free 3-day key are gone, and so are the top-level `api-key` and `model` options. Keys and models now live under `provider.providers.<name>`. The plugin never switches your chat to another company by itself:

- If your config still selects the removed provider in `provider.primary`, or has a top-level `api-key` and never chose a provider, the bot stays **inactive and sends nothing**; the log says so in one line.
- To continue, set `provider.primary` to `openai`, `anthropic`, `ollama` or `custom`, fill in that provider's `api-key` (see [Provider Setup](Provider-Setup)), then `/aifriend reload`.
- The removed name in `provider.failover` is skipped with a warning.
- A leftover top-level `api-key` is ignored (with a warning) and never copied anywhere. Delete it after you moved the key.

## From 2.1 to 2.2

The new sections (`server-context`, `mood`, `relationships`, `boredom`, `supervision`) are added with comments. The database schema is upgraded to v2 the next time the database is opened (only when long-term memory or relationships are enabled); existing notes are untouched. Nothing changes until you enable something.

## From 2.0 to 2.1

The chat feature sections and long-term memory are added, all off. Then follow the 3.x notes above.

## From 1.x

2.0.0 was a rewrite. The folder `plugins/ai-friend/` stays the same, so your settings are kept.

- Old keys are migrated on the first start (backup `config.yml.bak-v0`): `chatbot_name` -> `chatbot-name`, `preffered_language` -> `language`, `timer_interval` -> `interval-seconds`, `player_dissinterest` -> `stop-when-ignored`. `enabled` and `personalization` keep their names.
- `openai_api_key` and `gpt_version` are **not** migrated (they belonged to the removed relay). Enter your key under `provider.providers.openai`.
- **Paper is required.** Stay on 1.4.1 if you run Spigot.
- `/aifriend` now has subcommands; `/aifriendreload` still works. Commands use permissions instead of op status.
