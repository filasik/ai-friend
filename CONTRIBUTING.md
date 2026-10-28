# Contributing

- Open an issue before larger changes.
- Java 25, Gradle (`./gradlew build`, `./gradlew test`), test on Paper (`./gradlew runServer`).
- Code in `src/` is Paper-only and must stay Folia-friendly: no `BukkitScheduler`, no blocking calls on the main thread.
- Keep PRs focused; update `CHANGELOG.md`.
- By contributing you agree your code is licensed under the project license (MIT).
- Changes to what is sent to the AI backend must also update the "Privacy and data" sections (README, `docs/modrinth.md`, `docs/spigot.bbcode.txt`).
