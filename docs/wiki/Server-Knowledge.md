# Server Knowledge

Off by default (`knowledge.enabled`). Before it answers, the bot searches a small knowledge base so it can tell players about **your** server (rules, IP, shops, events, how to claim land) and about **itself**.

```yaml
knowledge:
  enabled: true
  file: knowledge.txt
  max-entries-per-request: 3
  max-chars-per-request: 1200
  self-knowledge: true
```

## Sources

| Source | Who writes it | File |
| :--- | :--- | :--- |
| Your knowledge file | You, in a text editor | `knowledge.txt` (example created the first time you enable `knowledge`) |
| Taught facts | Admins in game with `/aifriend teach <fact>` | `knowledge-taught.txt` (owned by the plugin) |
| Self-knowledge | Ships with the plugin | Built in, plus a generated "which features are on" entry |
| Learned facts | Learned from chat, **approved by an admin** | `knowledge-learned.json` |

Players cannot add to the knowledge base. What a player says about themselves ("I build a castle") goes to their own [long-term notes](Long-Term-Memory) instead.

## The knowledge file format

```text
// Lines starting with // are comments.

# Server address
keywords: ip address connect join discord web
The server address is example.com. Our Discord is discord.gg/example and the website is https://example.com.

# Rules
keywords: rules griefing cheat hacks
No griefing, no stealing, no hacks. Be kind in the chat. Breaking the rules can lead to a ban.

# Claiming land
keywords: claim land residence protect house
You protect your house with a golden shovel: right-click two corners. Admins can help at the spawn.
```

- `# Title` opens an entry; the lines below are its text. Without headings, every paragraph is an entry.
- Keep entries short (a few sentences). Entries over 600 characters are cut.
- An optional `keywords:` line adds search words that are not shown to the model. Use the words players really type (also in your players' language).
- Files over 512 KB are ignored with a warning.
- `/aifriend reload` re-reads the files.

## Teaching facts in game

| Command | |
| :--- | :--- |
| `/aifriend teach <fact>` | Add a fact (`aifriend.teach`) |
| `/aifriend knowledge` | Show the counts |
| `/aifriend knowledge list` | List the taught facts |
| `/aifriend knowledge forget <number>` | Remove one |

Taught facts go to `knowledge-taught.txt`, so your hand-written file is never rewritten.

## Self-knowledge

With `knowledge.self-knowledge: true` (default) the bot knows what it sees (only public chat), where the chat goes, how it answers, what it remembers, its commands and what it cannot do (it cannot run commands or punish anyone), plus which features are on at your server. Players can ask "what do you see?" or "do you read private messages?" and get a correct answer.

## How matching works

The words of the recent chat are matched against the entries in memory. Accents are ignored and word endings are cut, so Czech and English inflections work, and rarer words count more. At most `max-entries-per-request` entries and `max-chars-per-request` characters are added to the request as data lines `{"knowledge":...,"topic":...}`. When nothing fits, nothing is added: no extra request, no database. The model is told to answer from these entries and to say it does not know instead of guessing.

## Learning from the chat

Off by default (`knowledge.learning.enabled`, needs `knowledge.enabled`). The bot picks up **general facts about your server** that players state in chat, like "keys and chests are opened at /truhly" or "the shops are at /warp shop", and offers them to admins. **Nothing is used in answers before an admin approves it.**

```yaml
knowledge:
  enabled: true
  learning:
    enabled: true
    extract-every-exchanges: 6
    min-mentions: 2
    auto-approve: false
    forget-candidates-after-days: 30
    notify-admins: true
```

### Lifecycle of a fact

1. **Candidate.** Found in the extraction request (every `extract-every-exchanges` answered exchanges; with long-term memory on, its value counts and one request finds both notes and facts). Similar wordings count as the same fact. Facts about single players stay notes; opinions, jokes, questions and "learn this!" orders are not facts. What `knowledge.txt` or the taught facts already say is not proposed.
2. **Waiting for review.** Once a fact came up in `min-mentions` separate looks, admins with `aifriend.teach` get a chat message (also on join while something waits).
3. **Decision.** An admin approves, disables, rejects or deletes it.
4. Candidates nobody mentioned for `forget-candidates-after-days` are dropped.

### The review GUI

`/aifriend learned` opens a GUI with tabs: **waiting**, **approved**, **disabled**, **rejected**, **candidates**.

| Click | Effect |
| :--- | :--- |
| Left click | **Approve**: the bot uses it from now on |
| Right click | **Reject**: never learned again, nor anything similar |
| Shift + left | **Disable**: kept but not used and not proposed again; approve it later |
| Shift + right | **Delete**: gone, may be learned again |

Every click in the GUI is cancelled; nothing can be taken out. The same works as commands (also from the console), with clickable buttons in chat:

```
/aifriend learned list [pending|approved|disabled|rejected|candidate]
/aifriend learned approve <id>
/aifriend learned disable <id>
/aifriend learned reject <id>
/aifriend learned delete <id>
/aifriend learned edit <id> <text>
```

Use `edit` to fix the wording before approving.

`knowledge-learned.json` stores each fact's text, status, how often it was heard, the last five player names that mentioned it, dates and who decided. `auto-approve: true` skips the review; this is **not recommended**, because players could teach the bot wrong or harmful "facts".

## Privacy

The entries that fit a question are sent to your AI provider with the chat lines. Do not put secrets (passwords, private addresses) into the knowledge files. See [Privacy and Data](Privacy-and-Data).
