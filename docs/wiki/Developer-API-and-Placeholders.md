# Developer API and Placeholders

## Public API

The API lives in the package `me.skerik.aifriend.api`. Add AI Friend as a `compileOnly` dependency (build it from source or use the release jar) and declare it as an optional dependency in your `paper-plugin.yml`.

### `AiFriendResponseEvent`

Fired right before the bot posts an answer.

- **Asynchronous**: it runs off the main thread, so listeners must be thread safe and must not touch the world.
- **Cancellable**: cancel it and nothing is posted.
- `setText(...)` changes what is posted. The result is cleaned and cut to `max-reply-length` again.

| Method | Returns |
| :--- | :--- |
| `botName()` | The configured `chatbot-name` |
| `text()` / `setText(String)` | The answer that will be posted |
| `source()` | `TIMED` (the interval answer) or `DIRECT` (`/ai`, a mention) |
| `asker()` | UUID of the asking player, or `null` |

```java
@EventHandler
public void onAiReply(AiFriendResponseEvent event) {
    if (event.text().toLowerCase().contains("diamond")) {
        event.setText(event.text() + " (no free diamonds, sorry!)");
    }
    // Example: log direct answers to your own system (thread safe!)
    if (event.source() == AiFriendResponseEvent.Source.DIRECT) {
        myAsyncLogger.log(event.asker(), event.text());
    }
}
```

### `AiFriendApi`

Registered in the Bukkit `ServicesManager` once startup has finished.

```java
AiFriendApi api = Bukkit.getServicesManager().load(AiFriendApi.class); // null until AI Friend has started
if (api != null && api.state() == AiFriendApi.State.RUNNING) {
    api.sleep(Duration.ofMinutes(10)); // quiet during an event
}
```

| Method | Description |
| :--- | :--- |
| `state()` | `RUNNING`, `DISABLED`, `NO_KEY`, `SLEEPING`, `BACKOFF` |
| `lastMessage()` | The last thing the bot said |
| `provider()` | Id of the provider that answers |
| `queuedLines()` | Chat lines waiting for the next answer |
| `sleep(Duration)` | Make the bot quiet for a while |
| `wake()` | Wake it up; returns whether it was asleep |

## PlaceholderAPI

A soft dependency with nothing to configure. When [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) is installed, the expansion `aifriend` is registered automatically.

| Placeholder | Value |
| :--- | :--- |
| `%aifriend_state%` | `running`, `disabled`, `no-key`, `sleeping`, `backoff` |
| `%aifriend_mood%` | With `mood.enabled`: `thrilled`, `cheerful`, `calm`, `uneasy`, `upset`. Otherwise `happy` (running), `sleepy` (sleeping), `confused` (waiting after an error), `off` |
| `%aifriend_relationship%` | `stranger`, `acquaintance`, `regular`, `friend` (empty when `relationships` is off) |
| `%aifriend_nickname%` | The nickname the player chose (empty if none) |
| `%aifriend_lastmessage%` | The last thing the bot said |
| `%aifriend_provider%` | Id of the provider that answers (`-` if none) |
| `%aifriend_queued%` | Chat lines waiting for the next answer |
| `%aifriend_muted%` | `true` / `false`: the player has muted the bot |
| `%aifriend_calls_left%` | Questions the player has left today, or `unlimited` |

Example for a scoreboard or tab list: `AI Friend is %aifriend_mood%`.
