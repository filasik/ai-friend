# Configuration

Everything lives in `plugins/ai-friend/config.yml`. The file is fully commented and is **never overwritten**:

- When you update the plugin, options added by the new version are appended with their comments. Your values stay, and a backup `config.yml.bak-v<old>` is saved first. `config-version` tracks this (currently **8**).
- Invalid values fall back to the default with a warning in the log.
- `/aifriend reload` applies almost everything, including switching features on and off. Only `command.aliases` needs a restart.

All the feature sections (personas, memory, limits, knowledge, mood, ...) are **off by default**, so an upgrade never changes behaviour until you switch something on.

## General

| Key | Default | Description |
| :--- | :--- | :--- |
| `config-version` | `8` | Managed by the plugin. Do not change |
| `locale` | `en` | Language of the plugin's own messages: `en` or `cs` (or your own file) |
| `update-check` | `true` | Check GitHub releases for a newer version (async, at startup and every 12 hours). Players with `aifriend.admin` are told on join |
| `metrics` | `true` | Anonymous [bStats](https://bstats.org) statistics |
| `command.aliases` | `[]` | Extra aliases for `/aifriend`. Restart required |

## The chatbot

| Key | Default | Description |
| :--- | :--- | :--- |
| `enabled` | `true` | Switch the bot on or off without removing the plugin |
| `chatbot-name` | `AI Friend` | Name shown in front of replies (`<name>: message`) |
| `language` | `English` | Language the bot always answers in (free text: `Czech`, `German`, ...) |
| `personalization` | `""` | Free-text instructions: your website, jokes, house rules, tone |
| `chattiness` | `normal` | `low`: answers almost only when spoken to. `normal`: joins in now and then but stays out of conversations between players. `high`: also joins those with a short remark |
| `interval-seconds` | `60` | How often the bot looks at the chat (5-3600). Nothing is sent when nobody wrote |
| `stop-when-ignored` | `true` | Let the bot go to sleep when players seem annoyed with it (needs at least 2 different players in the batch, or every online player if fewer) |
| `sleep-minutes` | `30` | How long it sleeps then. `/aifriend reload` wakes it |

## Limits and network

| Key | Default | Description |
| :--- | :--- | :--- |
| `max-queued-messages` | `50` | Chat lines kept while waiting for the next answer; oldest dropped first (5-500) |
| `max-reply-length` | `256` | Longer answers are cut (20-1000) |
| `http.connect-timeout-seconds` | `10` | Connect timeout (1-120) |
| `http.read-timeout-seconds` | `30` | Read timeout (1-120) |

## Provider

See [Provider Setup](Provider-Setup) for examples.

| Key | Default | Description |
| :--- | :--- | :--- |
| `provider.primary` | `openai` | `openai`, `anthropic`, `ollama`, `custom` or a name you define |
| `provider.failover` | `[]` | Providers to try, in order, when the primary fails |
| `provider.failover-cooldown-seconds` | `60` | How long a failing provider is skipped |
| `provider.cache.enabled` | `false` | Remember answers to identical requests |
| `provider.cache.max-entries` | `200` | Cache size |
| `provider.cache.ttl-minutes` | `10` | How long a cached answer is used |
| `provider.providers.openai.api-key` | placeholder | OpenAI key |
| `provider.providers.openai.model` | `gpt-4o-mini` | |
| `provider.providers.openai.base-url` | `https://api.openai.com/v1` | Change only for a compatible gateway |
| `provider.providers.anthropic.api-key` | placeholder | Anthropic key |
| `provider.providers.anthropic.model` | `claude-haiku-5-5` | |
| `provider.providers.anthropic.base-url` | `https://api.anthropic.com` | |
| `provider.providers.ollama.model` | `llama3.2` | |
| `provider.providers.ollama.base-url` | `http://localhost:11434` | |
| `provider.providers.custom.model` | `""` | Required for `custom` |
| `provider.providers.custom.base-url` | `""` | Required, e.g. `https://openrouter.ai/api/v1` |
| `provider.providers.custom.api-key` | `""` | Empty for servers without a key |
| `provider.providers.<name>.max-output-tokens` | `400` | Answer size limit (16-4000) |
| `provider.providers.<name>.type` | | For your own names: which kind to behave like, e.g. `custom` |

## Personas

| Key | Default | Description |
| :--- | :--- | :--- |
| `personas.enabled` | `false` | Use personas |
| `personas.list` | two examples | Each entry: `name`, `prompt`, `worlds` (empty = all), `time` (`any` / `day` / `night`), `weight` (0 = never, 1-1000) |

## Conversation memory

| Key | Default | Description |
| :--- | :--- | :--- |
| `memory.enabled` | `false` | Send the last exchanges as chat history |
| `memory.exchanges` | `5` | Remembered exchanges (0-50) |
| `memory.max-chars` | `2000` | Hard limit of remembered characters (100-20000) |

## Rate limits

`0` means unlimited. Days end at midnight of the server's time zone.

| Key | Default | Description |
| :--- | :--- | :--- |
| `limits.enabled` | `false` | Apply rate limits |
| `limits.cooldown-seconds` | `30` | Wait between two direct requests of a player |
| `limits.player-daily-calls` | `50` | Per player and day |
| `limits.player-daily-tokens` | `0` | Per player and day |
| `limits.global-daily-calls` | `0` | Whole server and day (also timed answers and event reactions) |
| `limits.global-daily-tokens` | `0` | Whole server and day |

## Cost estimate

| Key | Default | Description |
| :--- | :--- | :--- |
| `cost.enabled` | `false` | Track tokens and estimate cost (`/aifriend cost`) |
| `cost.input-price-per-million` | `0.15` | Price of 1M input tokens, your currency |
| `cost.output-price-per-million` | `0.60` | Price of 1M output tokens |
| `cost.warn-daily-cost` | `0` | Warn admins once a day at this cost, `0` = never |

## Reply filter

Colour codes (`§`) and control characters are always removed and the answer is always cut to the length limit.

| Key | Default | Description |
| :--- | :--- | :--- |
| `reply-filter.enabled` | `false` | Apply the options below |
| `reply-filter.max-length` | `256` | Overrides `max-reply-length` when enabled (20-1000) |
| `reply-filter.banned-words` | `[]` | Whole words, case-insensitive |
| `reply-filter.banned-mode` | `replace` | `replace` (word becomes `***`) or `drop` (answer is not posted) |
| `reply-filter.block-urls` | `false` | Remove links and web addresses |

## Mentions and `/ai`

| Key | Default | Description |
| :--- | :--- | :--- |
| `mentions.enabled` | `false` | Enable `/ai` and addressing the bot in chat |
| `mentions.direct-answer` | `true` | Answer a mention immediately (limits still apply); `false` = a mention is normal chat |
| `mentions.triggers` | `[]` | More names that call the bot (case and accents ignored), e.g. `[bot, robot]` |
| `mentions.react-anywhere` | `false` | The name anywhere in a line counts, not only `@name` or `name,` |

## Opt-out

| Key | Default | Description |
| :--- | :--- | :--- |
| `opt-out.enabled` | `false` | Allow `/aifriend mute` and `unmute` |
| `opt-out.file` | `muted-players.yml` | Where muted players are saved |

## Game events

| Key | Default | Description |
| :--- | :--- | :--- |
| `events.enabled` | `false` | React to game events |
| `events.cooldown-seconds` | `30` | One reaction per this time, server-wide |
| `events.death` | `true` | Player deaths |
| `events.advancement` | `true` | Advancements announced in chat |
| `events.first-join` | `true` | A player joins for the first time |
| `events.boss-kill` | `true` | Ender Dragon and Wither kills |
| `events.explosion` | `false` | Big explosions (noisy on survival servers) |
| `events.explosion-min-power` | `6` | Smallest explosion that counts |

## Storage

See [Storage and Database](Storage-and-Database).

| Key | Default | Description |
| :--- | :--- | :--- |
| `storage.type` | `sqlite` | `sqlite` or `mariadb` (MySQL works too) |
| `storage.sqlite.file` | `ai-friend.db` | File in the plugin folder |
| `storage.sqlite.pool-size` | `3` | Connections (1-32) |
| `storage.mariadb.host` / `port` | `localhost` / `3306` | |
| `storage.mariadb.database` / `user` / `password` | `aifriend` / `aifriend` / `change-me` | Keep `config.yml` private |
| `storage.mariadb.pool-size` | `6` | Connections (1-64) |
| `storage.mariadb.ssl-mode` | `""` | Driver default. `trust`, `disable`, `verify-ca`, `verify-full` |

## Long-term memory

See [Long-Term Memory](Long-Term-Memory).

| Key | Default | Description |
| :--- | :--- | :--- |
| `long-term-memory.enabled` | `false` | Remember facts players told about themselves |
| `long-term-memory.extract-every-exchanges` | `6` | One extra request per this many answered exchanges (1-1000) |
| `long-term-memory.max-notes-per-player` | `20` | Least recently used notes are dropped (1-500) |
| `long-term-memory.retention-days` | `0` | Delete notes unused for this long, `0` = keep (0-36500) |
| `long-term-memory.retrieval-top-k` | `5` | Notes added to one request (1-50) |
| `long-term-memory.delete-on-mute` | `true` | Delete a player's notes when they mute the bot |
| `long-term-memory.embeddings.backend` | `none` | `none` (word matching) or the name of an entry under `provider.providers` |
| `long-term-memory.embeddings.model` | `""` | Empty = `text-embedding-3-small` (OpenAI-compatible) or `nomic-embed-text` (Ollama) |
| `long-term-memory.embeddings.base-url` | `""` | Empty = the provider's `base-url` |

## Server context

See [World Awareness and Personality](World-Awareness-and-Personality#server-context).

| Key | Default | Description |
| :--- | :--- | :--- |
| `server-context.enabled` | `false` | Send a short picture of the server with every request |
| `server-context.refresh-seconds` | `30` | Refresh interval while somebody is online (5-600) |
| `server-context.max-players` | `30` | Players looked at per refresh (1-200) |
| `server-context.time` / `weather` / `world` / `biome` / `nearby-mobs` | `true` | Switch each part off separately |
| `server-context.radius` | `16` | Radius in blocks for nearby hostile mobs |
| `server-context.share-percent` | `10` | Share of requests that carry the surroundings at all (0-100); always when players ask about them |

## Mood

| Key | Default | Description |
| :--- | :--- | :--- |
| `mood.enabled` | `false` | Game events push the bot's mood around |
| `mood.half-life-minutes` | `30` | After this many minutes half the mood is gone (1-1440) |

## Relationships

| Key | Default | Description |
| :--- | :--- | :--- |
| `relationships.enabled` | `false` | The bot gets to know players |
| `relationships.acquaintance-after` | `5` | Answered exchanges for *acquaintance* |
| `relationships.regular-after` | `25` | ... for *regular* |
| `relationships.friend-after` | `80` | ... for *friend*. Must grow: acquaintance < regular < friend |
| `relationships.nicknames` | `true` | Let players pick a nickname with `/aifriend nickname` |

## Boredom

| Key | Default | Description |
| :--- | :--- | :--- |
| `boredom.enabled` | `false` | Start conversations in a quiet chat |
| `boredom.quiet-minutes` | `10` | Nobody wrote for this long (1-1440) |
| `boredom.cooldown-minutes` | `30` | Minimum time between starters (1-1440); doubles up to 8 times while nobody reacts |
| `boredom.min-players` | `1` | Players that must be online (1-200) |
| `boredom.max-per-day` | `10` | Starters per day (1-200) |
| `boredom.ideas` | `[topic, quiz, challenge, joke, fact]` | What the bot may start with |

## Chat supervision

| Key | Default | Description |
| :--- | :--- | :--- |
| `supervision.enabled` | `false` | Friendly private notes about caps lock, spam and swearing |
| `supervision.caps` / `spam` / `swearing` | `true` | Each check separately |
| `supervision.bad-words` | `[]` | Whole words; accents, look-alikes (`@`, `0`) and repeated letters ignored |
| `supervision.caps-min-letters` | `12` | Letters needed before caps counts (4-200) |
| `supervision.caps-percent` | `70` | Share of capitals (50-100) |
| `supervision.cooldown-seconds` | `60` | Same problem, same player, at most this often (0-3600) |

## Server knowledge

See [Server Knowledge](Server-Knowledge).

| Key | Default | Description |
| :--- | :--- | :--- |
| `knowledge.enabled` | `false` | Search the knowledge base before answering |
| `knowledge.file` | `knowledge.txt` | Your knowledge file in the plugin folder |
| `knowledge.max-entries-per-request` | `3` | Entries added to one request (1-10) |
| `knowledge.max-chars-per-request` | `1200` | Characters added to one request (200-6000) |
| `knowledge.self-knowledge` | `true` | The bot can answer questions about itself |
| `knowledge.learning.enabled` | `false` | Learn general server facts from the chat (admin review) |
| `knowledge.learning.extract-every-exchanges` | `6` | Look for facts after this many answered exchanges (1-1000). With long-term memory on, its value is used and one request serves both |
| `knowledge.learning.min-mentions` | `2` | Separate looks a fact must come up in before admins are asked (1-50) |
| `knowledge.learning.auto-approve` | `false` | Use facts without review. Not recommended |
| `knowledge.learning.forget-candidates-after-days` | `30` | Drop candidates nobody mentioned for this long (1-3650) |
| `knowledge.learning.notify-admins` | `true` | Tell admins with `aifriend.teach` when a fact waits for review |

## A small example

```yaml
enabled: true
chatbot-name: "Buddy"
language: "Czech"
personalization: "You are the friendly helper of the server example.com. Mention /spawn to new players."
chattiness: normal
interval-seconds: 90

provider:
  primary: anthropic
  providers:
    anthropic:
      api-key: "sk-ant-..."

mentions:
  enabled: true
limits:
  enabled: true
  player-daily-calls: 30
```
