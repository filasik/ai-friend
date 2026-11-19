# World Awareness and Personality

Added in 2.2.0. All five features are **off by default** and switched on separately; `/aifriend reload` applies them.

| Feature | Section | What it adds |
| :--- | :--- | :--- |
| [Server context](#server-context) | `server-context` | Time, weather, world, biome and nearby mobs of players who wrote |
| [Mood](#mood) | `mood` | A mood that game events push around |
| [Relationships](#relationships) | `relationships` | The bot gets to know players, with nicknames |
| [Boredom](#boredom) | `boredom` | Conversation starters in a quiet chat |
| [Chat supervision](#chat-supervision) | `supervision` | Friendly private notes about caps lock, spam and swearing |

## Server context

With every request the bot gets a short picture of the server: how many players are online and, **only for the players who wrote in the current batch**, their world, biome, the hostile mobs within `radius` blocks (`"2 zombie, 1 creeper"`), and the time of day and weather **that player sees**. So it can say "careful, a creeper is behind you".

```yaml
server-context:
  enabled: true
  refresh-seconds: 30
  max-players: 30
  time: true
  weather: true
  world: true
  biome: true
  nearby-mobs: true
  radius: 16
  share-percent: 10
```

- Time and weather are read **per player**: other worlds (Multiverse) have their own day and weather, and plugins such as Residence, `/ptime` or `/pweather` set them for one player. Worlds without a day cycle (Nether, End) give none.
- It **never** gets coordinates, inventories, IP addresses or anything about players who did not write. Muted players are left out.
- The picture is refreshed every `refresh-seconds` on the threads that own the data (Folia safe); the bot only reads the finished copy.
- **How often surroundings are mentioned.** Only `share-percent` of the requests (default 10) carry the surroundings at all, and the bot is told to use them only when asked or when it clearly matters. When players' own words are about it (where, weather, night, mobs) they are always included. Set `share-percent: 100` for the old behaviour.

## Mood

A number from -100 to 100 that fades back to calm with a half-life (`half-life-minutes`, default 30).

| Event | Effect |
| :--- | :--- |
| Advancement | +8 |
| A player's first join | +10 |
| Ender Dragon or Wither killed | +35 |
| A death | -12 |
| Three deaths within five minutes | an extra -25 |
| Three explosions of power 3+ within two minutes (looks like griefing) | an extra -20 |

The level (**thrilled, cheerful, calm, uneasy, upset**) goes into the system prompt as a hint for the tone; it never changes what the bot may do. Mood works even when `events` reactions are off, shows in `/aifriend status` and as `%aifriend_mood%`, and starts calm after a restart.

## Relationships

The bot counts the answered exchanges a player took part in and treats them differently as it gets to know them:

| Level | Default threshold |
| :--- | :--- |
| stranger | 0 |
| acquaintance | `acquaintance-after: 5` |
| regular | `regular-after: 25` |
| friend | `friend-after: 80` |

Players who are not strangers appear in the data block as `{"relationship":"friend","player":"Alex","known_for_days":12,"nickname":"Ally"}`; the bot is warmer with friends and regulars and uses the nickname.

**Nicknames** (`relationships.nicknames: true`): `/aifriend nickname <text>` (letters, digits, space and `_ - . '`, up to 24 characters, also checked against the reply filter because the bot may say it), `/aifriend nickname clear` removes it, `/aifriend nickname` shows what the bot thinks of you.

Records are kept in the database (table `aifriend_relationships`: UUID, last name, counter, first and last time, nickname), loaded at start and saved in the background every minute and on shutdown. Without a database they live in memory. `/aifriend forget`, `forget <player>`, `forget-all` and muting the bot delete them. The level is also available as `%aifriend_relationship%`.

## Boredom

When nobody (players or the bot) has written for `quiet-minutes`, the bot may start a conversation from `ideas`: `topic`, `quiz`, `challenge`, `joke`, `fact`.

```yaml
boredom:
  enabled: true
  quiet-minutes: 10
  cooldown-minutes: 30
  min-players: 1
  max-per-day: 10
  ideas: [topic, quiz, challenge, joke, fact]
```

- Only when at least `min-players` are online, never on an empty server (the quiet time starts when the first player arrives), never while the bot is asleep, rate limited or backing off, and at most `max-per-day` times a day.
- At least `cooldown-minutes` pass between two starters, and that time doubles (up to 8 times) for every starter nobody reacted to.
- The idea is an instruction in the trusted system part, so a player cannot steer it.
- Starters count against rate limits and the cost estimate like any answer.

## Chat supervision

A friendly **private** note to a player who:

- writes in caps lock (`caps-min-letters` letters, at least `caps-percent` capitals),
- spams (the same line three times, or six lines within ten seconds),
- uses a word from `bad-words` (whole words; accents, look-alikes like `@` or `0` and stretched letters are ignored; the list is empty by default so the plugin does not impose a language).

```yaml
supervision:
  enabled: true
  caps: true
  spam: true
  swearing: true
  bad-words: [idiot, moron]
  caps-min-letters: 12
  caps-percent: 70
  cooldown-seconds: 60
```

Nothing else happens: no kick, mute or ban, nobody else sees the note, no chat is stored, and no AI request is made. The same problem is mentioned at most every `cooldown-seconds`. Players with `aifriend.bypass-supervision` and players who muted the bot are skipped. The texts are `supervision.caps`, `supervision.spam` and `supervision.language` in `messages_<locale>.yml`.
