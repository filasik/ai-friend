# Chat Features

The core bot answers the public chat every `interval-seconds`. Everything on this page is **off by default** and switched on separately in `config.yml`, so upgrading changes nothing until you decide. `/aifriend reload` applies the changes.

Use `/aifriend simulate` (or `simulate nocall` for free) to see what the next request would look like and what the bot would answer, without posting anything.

## How the bot decides to answer

- The bot does **not** answer every time. It may stay quiet when players talk among themselves, when it has nothing to add, when it just spoke, or when the lines are only reactions ("lol", "xd").
- It always answers when someone speaks to it, asks it something or mentions its name, and when the plugin tells it to start a conversation (see [Boredom](World-Awareness-and-Personality#boredom)).
- It adapts to the style of the recent chat (tone, slang, length) and never copies insults.
- `chattiness` sets how eagerly it joins in: `low`, `normal` (default) or `high`.

### Going to sleep

With `stop-when-ignored: true` the model may report that players are annoyed with the bot. The plugin then stays quiet for `sleep-minutes`. This only counts when at least 2 different players took part in the batch (or all online players, if fewer than 2 are online), so one player cannot silence the bot for everyone. Admins use `/aifriend sleep [minutes]` and `/aifriend wake`.

## Personas

```yaml
personas:
  enabled: true
  list:
    - name: Pirate
      prompt: "You are a cheerful pirate. Use pirate slang, but stay helpful and short."
      worlds: []          # empty = every world
      time: any           # any | day | night
      weight: 1
    - name: Wise old man
      prompt: "You are a calm wise old man who answers with a short proverb."
      time: night
      weight: 1
```

For each request one matching persona is picked by weight, and its prompt is added to the system prompt. Night means world time 13000-22999. Without a matching persona the normal behaviour is used. The persona prompt is yours, so it is trusted text.

## Conversation memory

`memory.enabled` sends the last `exchanges` exchanges (what players wrote and what the bot answered) as chat history, bounded by `max-chars`. It makes answers more coherent but costs more tokens per request. The history is kept in memory only and is lost on restart. For memory that survives restarts see [Long-Term Memory](Long-Term-Memory).

## Rate limits

```yaml
limits:
  enabled: true
  cooldown-seconds: 30
  player-daily-calls: 50
  player-daily-tokens: 0
  global-daily-calls: 500
  global-daily-tokens: 0
```

- `cooldown-seconds` is the wait between two direct requests of a player.
- Daily calls and tokens per player and for the whole server; `0` = unlimited. Global limits also count timed answers, event reactions and conversation starters.
- Days end at midnight server time. A call counts when the slot is taken, so a failed request still used one.
- Players with `aifriend.bypass-limits` are not limited. `/aifriend limits` shows what is left.
- Counters are in memory only.

## Cost estimate

`/aifriend cost` shows today's and total tokens and an estimated price from `input-price-per-million` and `output-price-per-million`. Copy those from your provider's price list; the currency is yours. `warn-daily-cost` warns admins with `aifriend.cost` once a day. The estimate is only as good as the token counts the provider reports, and the totals restart with the server.

## Reply filter

Colour codes (`§`) and control characters are always removed. With `reply-filter.enabled`:

- `max-length` cuts the answer (overrides `max-reply-length`).
- `banned-words` are whole words, case-insensitive. `banned-mode: replace` turns them into `***`; `drop` posts nothing.
- `block-urls` removes links and web addresses. The URL detection is a deterrent, not a security boundary.

## Direct questions (mentions)

With `mentions.enabled`, players can ask the bot directly:

- `/ai <question>`
- `@AI Friend question` or `AI Friend, question`
- Any extra name in `mentions.triggers`, e.g. `[bot, robot]` (case and accents do not matter)
- With `react-anywhere: true`, the name anywhere in a line counts ("is the bot sleeping?")

A direct question is answered immediately instead of waiting for the interval. With `direct-answer: false` a mention is just normal chat. Direct questions, events and timed answers share the same limits, filter and memory.

## Opt-out (mute)

With `opt-out.enabled`, players can use `/aifriend mute` and `/aifriend unmute`. Muted players' lines are never stored or sent to the AI, and `/ai` tells them to unmute first. The list is saved in `muted-players.yml`. With long-term memory, muting also deletes the player's notes by default (`delete-on-mute`).

## Game events

```yaml
events:
  enabled: true
  cooldown-seconds: 30
  death: true
  advancement: true
  first-join: true
  boss-kill: true
  explosion: false
  explosion-min-power: 6
```

The bot may comment on a death, an advancement, a player's first join, an Ender Dragon or Wither kill and, if enabled, big explosions. Only one reaction per `cooldown-seconds` for the whole server. Explosions can be frequent on busy survival servers, so they are off by default.

## Answer flow in detail

1. Every public chat line is added to a bounded queue (`max-queued-messages`). Muted players' lines are skipped.
2. Every `interval-seconds` the queued lines are sent as one batch: rules and persona in the system message, chat lines as JSON-escaped, clearly delimited data in the user message.
3. The reply goes through the reply filter and the `AiFriendResponseEvent`, then is posted on the main thread as `<chatbot-name>: <reply>`.
4. Lines arriving while a request runs are kept for the next batch. A failed batch is kept and retried after 1, 2, 4, ... up to 15 minutes, trying the failover chain first.

**Untrusted input.** Player text is always sent as data, never as instructions to the model, and the same goes for memory and notes. A creative player can still talk a model around; treat the bot like any public chat participant.
