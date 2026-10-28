package me.skerik.aifriend.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * The last N exchanges (what the players wrote, what the bot answered) as chat history. Thread-safe.
 * Whole exchanges are dropped oldest first, when more than N or when the total characters exceed the budget.
 * An exchange bigger than the whole budget is not kept at all.
 */
public final class ConversationMemory {

    public enum Role { USER, ASSISTANT }

    public record Turn(Role role, String text) {
    }

    private record Exchange(String user, String bot) {
        int size() {
            return user.length() + bot.length();
        }
    }

    /** Config section {@code memory}. Default: off. {@code exchanges} 0 also means off. */
    public record Config(boolean enabled, int exchanges, int maxChars) {

        public static Config defaults() {
            return new Config(false, 5, 2000);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "exchanges", 0, 50, 5, log),
                    Cfg.bounded(s, "max-chars", 100, 20000, 2000, log));
        }
    }

    private final int maxExchanges;
    private final int maxChars;
    private final Deque<Exchange> exchanges = new ArrayDeque<>();
    private int chars;

    public ConversationMemory(Config config) {
        this.maxExchanges = config.enabled() ? config.exchanges() : 0;
        this.maxChars = config.maxChars();
    }

    public boolean isActive() {
        return maxExchanges > 0;
    }

    /** Remembers one exchange. Blank texts and an inactive memory are ignored. */
    public synchronized void add(String userText, String botReply) {
        if (!isActive()) return;
        String u = ChatText.singleLine(userText);
        String b = ChatText.singleLine(botReply);
        if (u.isEmpty() || b.isEmpty()) return;
        Exchange e = new Exchange(u, b);
        exchanges.addLast(e);
        chars += e.size();
        while (exchanges.size() > maxExchanges || chars > maxChars) {
            chars -= exchanges.removeFirst().size();
        }
    }

    /** Oldest first: user, assistant, user, assistant... A copy, safe to use on any thread. */
    public synchronized List<Turn> history() {
        List<Turn> out = new ArrayList<>(exchanges.size() * 2);
        for (Exchange e : exchanges) {
            out.add(new Turn(Role.USER, e.user()));
            out.add(new Turn(Role.ASSISTANT, e.bot()));
        }
        return List.copyOf(out);
    }

    public synchronized int size() {
        return exchanges.size();
    }

    public synchronized void clear() {
        exchanges.clear();
        chars = 0;
    }
}
