# Commands and Permissions

The main command is `/aifriend`. Extra aliases can be added with `command.aliases` (restart needed). `/aifriendreload` is kept as a legacy alias for `/aifriend reload`. Admin commands work from the console, and tab completion is available.

## Player commands

| Command | What it does | Permission |
| :--- | :--- | :--- |
| `/aifriend` | Plugin version and short help | `aifriend.use` |
| `/ai <question>` | Ask the bot directly; it answers at once (needs `mentions.enabled`) | `aifriend.ask` |
| `/aifriend limits` | Your remaining calls, tokens and cooldown | `aifriend.ask` |
| `/aifriend mute` / `unmute` | Switch the bot off / on for yourself (needs `opt-out.enabled`) | `aifriend.mute` |
| `/aifriend memory` | List the notes the bot remembers about you (needs long-term memory) | `aifriend.mute` |
| `/aifriend forget` | Delete your own notes and what the bot knows about you. Works even when you muted the bot | `aifriend.mute` |
| `/aifriend nickname [text\|clear]` | Pick the nickname the bot may call you, remove it, or (without argument) see how well the bot knows you (needs `relationships.enabled`) | `aifriend.nickname` |

Players can also address the bot in chat with `@AI Friend question`, `AI Friend, question` or any name in `mentions.triggers` (permission `aifriend.ask`).

## Admin commands

| Command | What it does | Permission |
| :--- | :--- | :--- |
| `/aifriend status` | State (running, disabled, no key, sleeping, backoff), provider, model, persona, memory, mood, muted players, masked API key, queue size | `aifriend.admin` |
| `/aifriend reload` | Reload `config.yml`, messages and knowledge files; wakes the bot and restarts its timer | `aifriend.reload` |
| `/aifriend sleep [minutes]` | Make the bot quiet for 1-1440 minutes (default `sleep-minutes`) | `aifriend.sleep` |
| `/aifriend wake` | Wake the bot up | `aifriend.sleep` |
| `/aifriend simulate [nocall]` | Dry run: persona, request, history size, token estimate, what blocks a reply and the filtered answer. Nothing is posted. `nocall` skips the provider call, so it costs nothing | `aifriend.simulate` |
| `/aifriend cost` | Token totals and estimated cost (needs `cost.enabled`) | `aifriend.cost` |
| `/aifriend memory <player>` | List a player's notes (offline players work too) | `aifriend.admin` |
| `/aifriend forget <player>` | Delete a player's notes and relationship | `aifriend.admin` |
| `/aifriend forget-all` | Delete all notes | `aifriend.admin` |
| `/aifriend teach <fact>` | Add a fact to the bot's knowledge (needs `knowledge.enabled`) | `aifriend.teach` |
| `/aifriend knowledge` | Counts of knowledge entries (file, taught, self, learned) | `aifriend.teach` |
| `/aifriend knowledge list` | List the taught facts with numbers | `aifriend.teach` |
| `/aifriend knowledge forget <number>` | Remove a taught fact | `aifriend.teach` |
| `/aifriend learned` | Open the review GUI for facts learned from chat (console: list what waits) | `aifriend.teach` |
| `/aifriend learned list [pending\|approved\|disabled\|rejected\|candidate]` | List learned facts with clickable buttons | `aifriend.teach` |
| `/aifriend learned approve\|disable\|reject\|delete <id>` | Decide about a learned fact | `aifriend.teach` |
| `/aifriend learned edit <id> <text>` | Fix the wording of a learned fact before approving | `aifriend.teach` |

Player names in `memory <player>` and `forget <player>` are matched against online players or the name stored with the notes, so offline players work.

## Permissions

| Permission | Default | Grants |
| :--- | :--- | :--- |
| `aifriend.use` | everyone | Basic `/aifriend` info |
| `aifriend.ask` | everyone | `/ai`, `/aifriend limits`, addressing the bot in chat |
| `aifriend.mute` | everyone | `/aifriend mute`, `unmute`, and your own `memory` and `forget` |
| `aifriend.nickname` | everyone | `/aifriend nickname` |
| `aifriend.admin` | op | `/aifriend status`, update notices on join, `memory <player>`, `forget <player>`, `forget-all` |
| `aifriend.reload` | op | `/aifriend reload` |
| `aifriend.sleep` | op | `/aifriend sleep`, `wake` |
| `aifriend.simulate` | op | `/aifriend simulate` |
| `aifriend.cost` | op | `/aifriend cost` and the daily cost warning |
| `aifriend.teach` | op | `/aifriend teach`, `knowledge`, `learned` and the messages about facts waiting for review |
| `aifriend.bypass-limits` | op | Not affected by the cooldown and daily limits |
| `aifriend.bypass-supervision` | op | No friendly notes about caps lock, spam or language |

## Example: LuckPerms

```
# Let helpers review learned facts and put the bot to sleep
/lp group helper permission set aifriend.teach true
/lp group helper permission set aifriend.sleep true

# Stop default players from asking the bot directly
/lp group default permission set aifriend.ask false
```
