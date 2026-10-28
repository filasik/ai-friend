package me.skerik.aifriend.papi;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.skerik.aifriend.AIFriend;
import me.skerik.aifriend.Context;
import me.skerik.aifriend.ai.ChatBot;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * PlaceholderAPI expansion; it only reads in-memory state, so it is safe on the main thread.
 * <ul>
 *   <li>{@code %aifriend_state%} running, disabled, no-key, sleeping or backoff</li>
 *   <li>{@code %aifriend_mood%} happy (running), sleepy (sleeping), confused (waiting after an error), off</li>
 *   <li>{@code %aifriend_lastmessage%} the last thing the bot said</li>
 *   <li>{@code %aifriend_provider%} id of the provider that answers</li>
 *   <li>{@code %aifriend_queued%} chat lines waiting for the next answer</li>
 *   <li>{@code %aifriend_muted%} true/false: the player has muted the bot</li>
 *   <li>{@code %aifriend_calls_left%} questions the player has left today, or unlimited</li>
 * </ul>
 */
public final class AiFriendExpansion extends PlaceholderExpansion {

    private final AIFriend plugin;

    public AiFriendExpansion(AIFriend plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aifriend";
    }

    @Override
    public String getAuthor() {
        return "filasik";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        Context ctx = plugin.context();
        if (ctx == null) return "";
        ChatBot.Status st = ctx.bot.status();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "state" -> st.state().name().toLowerCase(Locale.ROOT).replace('_', '-');
            case "mood" -> switch (st.state()) {
                case RUNNING -> "happy";
                case SLEEPING -> "sleepy";
                case BACKOFF -> "confused";
                case DISABLED, NO_KEY -> "off";
            };
            case "lastmessage" -> ctx.bot.lastMessage();
            case "provider" -> st.provider();
            case "queued" -> Integer.toString(st.queued());
            case "muted" -> player == null ? "false" : Boolean.toString(ctx.bot.isMuted(player.getUniqueId()));
            case "calls_left" -> {
                if (player == null) yield "";
                long left = ctx.bot.remaining(player.getUniqueId()).playerCalls();
                yield left < 0 ? "unlimited" : Long.toString(left);
            }
            default -> null;
        };
    }
}
