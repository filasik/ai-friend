# Messages and Localization

There are two separate language settings:

| Setting | Controls |
| :--- | :--- |
| `locale` | The plugin's own messages: command output, status, errors, supervision notes |
| `language` | The language **the bot speaks** in chat (free text: `English`, `Czech`, `German`, ...) |

## Message files

Messages live in `plugins/ai-friend/messages_<locale>.yml` and use [MiniMessage](https://docs.advntr.dev/minimessage/format.html). English (`en`) and Czech (`cs`) are bundled and copied to the plugin folder once.

- Edit the files freely; `/aifriend reload` applies them.
- A missing key falls back to the bundled text of that language, then to English.
- Placeholders in `<angle brackets>` (like `<version>`, `<bot>`, `<text>`) are filled in by the plugin. Keep them.

### Sections

| Section | Contains |
| :--- | :--- |
| `prefix` | The prefix used by other messages as `<prefix>` |
| `error` | Not ready, players only, no permission, generic error |
| `chat.format` | How the bot's answer looks in chat |
| `command` | `/aifriend` info, reload, status, states, sleep, mute, limits, cost, ... |
| `memory` | Long-term memory commands |
| `knowledge` | `/aifriend teach` and `/aifriend knowledge` |
| `learned` | The learned-facts GUI and commands |
| `supervision` | `caps`, `spam`, `language` notes |
| `update` | Update notices |

### Restyle the bot's chat line

```yaml
chat:
  format: '<gray><bot><dark_gray>:</dark_gray> <white><text>'
```

`<bot>` is `chatbot-name`, `<text>` is the answer. The answer is never parsed as MiniMessage, so the model cannot inject formatting or click events. For example:

```yaml
chat:
  format: '<gradient:#7ee8fa:#80ff72><bot></gradient> <dark_gray>»</dark_gray> <white><text>'
```

## Add a language

1. Copy `messages_en.yml` to `messages_<code>.yml`, for example `messages_de.yml`.
2. Translate the values.
3. Set `locale: de` in `config.yml` and run `/aifriend reload`.

If the file is missing, the plugin logs a warning and uses English. Pull requests with new translations are welcome; see [Building and Contributing](Building-and-Contributing).
