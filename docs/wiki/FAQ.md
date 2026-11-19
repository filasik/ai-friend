# FAQ

**Does chat go through the plugin author's server?**
No. The plugin has no server of its own. Chat goes only to the provider you configure. With Ollama on the same machine nothing leaves your server. See [Privacy and Data](Privacy-and-Data).

**Does the bot read private messages or commands?**
No. It reads public chat only. `/msg`, `/tell`, `/r`, commands and partial channels (staff, party, local) are never collected. `/ai <question>` is the one exception, because the player asks the bot on purpose.

**Which provider should I choose?**
Lowest cost and effort: `openai` with `gpt-4o-mini`, or `anthropic` with the small Claude model. Privacy: a local `ollama` model (needs a capable machine). Choice of many models: `custom` with OpenRouter. See [Provider Setup](Provider-Setup).

**Is there a free key or trial?**
Not any more. The relay and the free 3-day key were removed in 3.0.0. Use your own key, or a local Ollama for free.

**Does it work on Spigot or Bukkit?**
No. 2.0.0 and later need Paper. Use 1.4.1 or move to Paper.

**Does it work on Folia?**
The code uses only region, entity, global and async schedulers (no `BukkitScheduler`) and is declared Folia-supported, but it is primarily tested on Paper.

**Why doesn't the bot answer every message?**
That is on purpose. It stays quiet when players talk among themselves or there is nothing to add, and always answers when spoken to. Set `chattiness: high` for a chattier bot or `low` for one that mostly answers when asked.

**Can I make the bot speak another language?**
Yes: `language: Czech` (or any language the model knows). The plugin's own messages are a separate setting, `locale`. See [Messages and Localization](Messages-and-Localization).

**Can players silence the bot?**
One player can mute it for themselves with `/aifriend mute` (needs `opt-out.enabled`). The bot goes to sleep for everyone only when at least two different players seem annoyed. Admins use `/aifriend sleep`.

**Can players teach the bot wrong facts about my server?**
Not without you. Only admins can `/aifriend teach`, and facts learned from chat wait for admin approval in `/aifriend learned` (unless you turned on `auto-approve`, which is not recommended).

**Can a player make the bot say something bad?**
Player text is sent as data, not instructions, but a creative player can still talk a model around. Use the [reply filter](Chat-Features#reply-filter) (`banned-words`, `block-urls`) and treat the bot like any public chat participant.

**Can the bot run commands, kick or ban?**
No. It can only post chat messages (and private supervision notes, which are fixed texts).

**How do I keep costs under control?**
Raise `interval-seconds`, enable `limits` and `cost` with `warn-daily-cost`, keep `memory` small, use a cheap model and watch `/aifriend cost`.

**Can several servers share the bot's memory?**
Yes, with `storage.type: mariadb` pointed at the same database. See [Storage and Database](Storage-and-Database).

**Can I run several bots at once?**
Not yet. It is on the [roadmap](https://github.com/filasik/ai-friend/blob/main/ROADMAP.md), together with a Dynmap hook and persisting cache, limits and cost totals across restarts.

**How do I add the bot's mood to my scoreboard?**
Install PlaceholderAPI and use `%aifriend_mood%`. See [Developer API and Placeholders](Developer-API-and-Placeholders).
