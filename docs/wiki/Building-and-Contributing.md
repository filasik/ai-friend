# Building and Contributing

## Build from source

You need **JDK 25**. The Gradle wrapper is included.

```bash
git clone https://github.com/filasik/ai-friend.git
cd ai-friend
./gradlew build         # compiles with warnings as errors, runs the tests, jar in build/libs
./gradlew test          # unit tests only
./gradlew runServer     # local Paper test server (-PrunDir=<path> reuses an existing server directory)
```

The release artifact is the shaded jar in `build/libs/`. Only bStats is shaded (relocated to `me.skerik.aifriend.libs.bstats`); SQLite, MariaDB and HikariCP are resolved at runtime by the plugin loader from `src/main/resources/libraries.txt`.

Versions are pinned in `gradle.properties` (`pluginVersion`, `paperApiVersion`, `minecraftVersion`, library versions).

## Smoke test

```bash
./scripts/smoke-test.sh
```

Builds the plugin, starts a clean Paper server with it, checks that it enabled without errors and stops the server. CI runs it on every push (with `CI=true`, which accepts the EULA).

## Project layout

| Package | Contents |
| :--- | :--- |
| `me.skerik.aifriend` | Plugin main class, loader, scheduler abstraction (Paper / Folia) |
| `ai` | `ChatBot`, prompt building, reply parsing, note extraction |
| `ai.provider` | OpenAI, Anthropic, Ollama providers, failover, cache, embedders |
| `chat` | Personas, memory, rate limiter, cost, reply filter, mentions, opt-out, events, mood, relationships, boredom, supervision |
| `command` | Brigadier commands (`/aifriend`, `/ai`, `/aifriendreload`, `learned`) |
| `config` | Settings, config updater, messages |
| `knowledge` | Knowledge base, self-knowledge, learned facts |
| `longmemory` | Long-term memory, embeddings, sensitive-data filter |
| `storage` | Database, migrations, notes and relationship stores |
| `world` | Server context collector |
| `gui` | Learned-facts review GUI |
| `listener` | Chat and game event listeners |
| `papi` | PlaceholderAPI expansion |
| `api` | Public API (`AiFriendApi`, `AiFriendResponseEvent`) |

## Contributing

- Open an issue before larger changes.
- Code is Paper-only and must stay Folia-friendly: no `BukkitScheduler`, no blocking calls on the main thread.
- The build uses `-Xlint:all -Werror`: no deprecated API without an explicit, commented suppression.
- Keep PRs focused and update `CHANGELOG.md`.
- Changes to what is sent to the AI provider must also update the privacy sections (README, `docs/modrinth.md`, `docs/spigot.bbcode.txt`, and the [Privacy and Data](Privacy-and-Data) wiki page).
- By contributing you agree your code is licensed under the project license (MIT).

## Releases

Pushing a tag `v*` runs the release workflow: smoke test, build, GitHub release and publishing to Modrinth and Hangar. `docs/RELEASE_CHECKLIST.md` lists the manual steps.

## Security

See [SECURITY.md](https://github.com/filasik/ai-friend/blob/main/SECURITY.md) for how to report a vulnerability.
