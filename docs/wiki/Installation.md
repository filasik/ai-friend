# Installation

## Requirements

| | |
| :--- | :--- |
| **Server** | Paper **26.3** or newer. Spigot and plain Bukkit are not supported since 2.0.0 |
| **Java** | **25** |
| **Network** | Outbound HTTPS to the provider you use (`api.openai.com`, `api.anthropic.com`, your own endpoint, or nothing outside your network for a local Ollama), plus `api.github.com` for update checks. The **first start** also needs Maven Central (see below) |
| **Key** | An OpenAI key, an Anthropic key, or none for Ollama and keyless local servers |
| **Optional** | [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) for `%aifriend_...%` placeholders |

The plugin is declared `folia-supported` and all scheduling goes through region, entity, global and async schedulers. It is primarily tested on Paper.

The old 1.x jar (Spigot-compatible) is still available on the [GitHub releases](https://github.com/filasik/ai-friend/releases) page.

## Steps

1. Download the jar from [Modrinth](https://modrinth.com/plugin/aifriend), [Hangar](https://hangar.papermc.io/filasik/AIFriend-Personal_Assistant) or [GitHub Releases](https://github.com/filasik/ai-friend/releases).
2. Put it into the `plugins/` folder and start the server. The folder `plugins/ai-friend/` is created with a commented `config.yml`, `messages_en.yml` and `messages_cs.yml`. The example `knowledge.txt` is created the first time you enable `knowledge`.
3. Open `plugins/ai-friend/config.yml`, pick a provider in `provider.primary` and fill in its key under `provider.providers.<name>.api-key`. See [Provider Setup](Provider-Setup).
4. Run `/aifriend reload`, then `/aifriend status` to check that the state is **running**.
5. Write something in chat and wait up to `interval-seconds` (60 by default).

> The default placeholders (`your_openai_api_key_here`, `your_anthropic_api_key_here`) mean "not set": the bot stays silent and nothing is sent anywhere.

## First start and libraries

To keep the jar small, the database drivers and the connection pool are not bundled. On the first start the Paper plugin loader downloads them once from Maven Central:

- `org.xerial:sqlite-jdbc`
- `org.mariadb.jdbc:mariadb-java-client`
- `com.zaxxer:HikariCP`

The server needs internet access for that first start. After that the libraries are cached by Paper.

## Files in the plugin folder

| File | What it is |
| :--- | :--- |
| `config.yml` | All settings. Never overwritten; new options are appended on update. See [Configuration](Configuration) |
| `config.yml.bak-v<N>` | Backup made before the config was upgraded from version `N` |
| `messages_en.yml`, `messages_cs.yml` | Editable plugin messages (MiniMessage). See [Messages and Localization](Messages-and-Localization) |
| `knowledge.txt` | Your server knowledge (an example is created when `knowledge.enabled` is first switched on). See [Server Knowledge](Server-Knowledge) |
| `knowledge-taught.txt` | Facts added with `/aifriend teach` (owned by the plugin) |
| `knowledge-learned.json` | Facts learned from chat and their review status |
| `muted-players.yml` | UUIDs of players who muted the bot (`opt-out`) |
| `ai-friend.db` | SQLite database, only created when long-term memory or relationships are on |

## Next steps

- Shape the bot: `chatbot-name`, `language`, `personalization` and `chattiness` in [Configuration](Configuration).
- Switch on the features you want: [Chat Features](Chat-Features), [Long-Term Memory](Long-Term-Memory), [Server Knowledge](Server-Knowledge), [World Awareness and Personality](World-Awareness-and-Personality).
- Tell your players where their chat goes: [Privacy and Data](Privacy-and-Data).
