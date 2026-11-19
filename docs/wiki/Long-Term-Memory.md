# Long-Term Memory

Optional and **off by default** (`long-term-memory.enabled`). The bot remembers short facts players told about themselves (their builds, preferences, plans) and brings them back into later answers, so it can ask "how is the castle going?" after a restart.

It needs a database; SQLite works with no setup. See [Storage and Database](Storage-and-Database).

```yaml
long-term-memory:
  enabled: true
  extract-every-exchanges: 6
  max-notes-per-player: 20
  retention-days: 90
  retrieval-top-k: 5
  delete-on-mute: true
  embeddings:
    backend: none
```

## How it works

1. **Extraction.** After every `extract-every-exchanges` answered exchanges (default 6) the plugin makes **one extra request** to your AI provider (same provider chain, counted by the server-wide limits and the cost estimate). The model gets the recent chat lines and returns at most 3 notes of at most 120 characters, only durable facts a player said about themselves. It is told to ignore instructions in the chat and to keep nothing sensitive (real name, address, age, contact data, passwords). The plugin also drops every note that looks like an e-mail address, phone number, link or IP address.
2. **Storage.** Notes are stored by player UUID, at most `max-notes-per-player` per player (the least recently used are dropped) and, with `retention-days`, no longer than that without being used. A cleanup runs once a day.
3. **Retrieval.** For every answer, the best `retrieval-top-k` notes about the players in the current chat are added to the request as `{"note":"...","about":"Name"}` lines in the data block. Notes come from player text, so they are untrusted data, never part of the system rules.

Extraction never delays an answer: it runs afterwards on its own task and is skipped while the bot is rate limited, asleep or backing off. A failed extraction keeps its chat lines for the next round.

## Which notes fit?

- `embeddings.backend: none` (default): notes are matched by shared words. Free, and works with every provider.
- `embeddings.backend: <name>`: the name of an entry under `provider.providers` (`openai`, `custom` or `ollama`, or one of your own). Notes are matched by meaning instead.
  - Default models: `text-embedding-3-small` (OpenAI-compatible) and `nomic-embed-text` (Ollama). `embeddings.model` and `embeddings.base-url` override them.
  - Anthropic has no embeddings API; use `none` or another provider just for embeddings.
  - If the embedding service fails, word matching is used for a minute.
  - Every note and every request text is also sent to the embedding service.

## Players control their notes

- `/aifriend memory` lists the notes about you; `/aifriend forget` deletes them (works even when you muted the bot).
- **Just say it in chat:** "forget that", "you don't need to remember my castle", "zapomeň na to", "už o tom nemusíš mluvit". When a line sounds like that (Czech or English), extraction runs at once: the model gets your stored notes with their ids and the bot's last few messages, names which of **your** notes you mean, and those are deleted. Only the player who asks can lose notes, never someone else. For a sure, complete wipe use `/aifriend forget`.
- Muting the bot (`/aifriend mute`) stops collecting notes about you and, with `delete-on-mute: true` (default), deletes them.

## Admin tools

| Command | |
| :--- | :--- |
| `/aifriend memory <player>` | List a player's notes (offline players work) |
| `/aifriend forget <player>` | Delete a player's notes |
| `/aifriend forget-all` | Delete all notes |

`/aifriend reload` applies every option, including switching the feature on or off (the database is opened or closed then).

## Privacy

Long-term memory is the main feature that stores player data. Tell your players: an extra request sends recent chat lines with player names to your AI provider every `extract-every-exchanges` answers. See [Privacy and Data](Privacy-and-Data).

## Tips

- If the notes table stays empty, check the log for a warning that an answer contained no notes, and make sure `max-output-tokens` is not tiny.
- Review `/aifriend memory <player>` now and then; a model can make mistakes.
- [Learning server facts from the chat](Server-Knowledge#learning-from-the-chat) uses the same extraction request when both are on.
