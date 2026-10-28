package me.skerik.aifriend.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.skerik.aifriend.ai.ChatBot;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Hands PUBLIC chat to the bot, nothing else: private messages and commands are not chat events at all, cancelled
 * messages are skipped, and so are messages that a chat plugin narrowed to a few viewers (staff/party/local channels)
 * without cancelling them.
 */
public final class ChatListener implements Listener {

    private final ChatBot bot;

    public ChatListener(ChatBot bot) {
        this.bot = bot;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!isPublic(playersAmong(event.viewers()), Bukkit.getOnlinePlayers().size())) return;
        bot.onChat(player.getUniqueId(), player.getName(), PlainTextComponentSerializer.plainText().serialize(event.message()),
                player.hasPermission("aifriend.ask"), player.hasPermission("aifriend.bypass-limits"),
                player.hasPermission("aifriend.bypass-supervision"));
    }

    private static int playersAmong(Iterable<? extends Audience> viewers) {
        int n = 0;
        for (Audience a : viewers) if (a instanceof Player) n++;
        return n;
    }

    /**
     * A message is public when (nearly) everybody online can read it. A few missing viewers are tolerated for
     * ignore lists: up to 20 % of the online players, so on small servers every online player must see it.
     * When in doubt the message is treated as private.
     */
    public static boolean isPublic(int viewerPlayers, int onlinePlayers) {
        return onlinePlayers - viewerPlayers <= onlinePlayers / 5;
    }
}
