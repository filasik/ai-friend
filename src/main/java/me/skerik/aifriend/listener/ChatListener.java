package me.skerik.aifriend.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.skerik.aifriend.ai.ChatBot;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Hands public chat to the bot. Runs last and skips cancelled messages (muted players, chat plugins). */
public final class ChatListener implements Listener {

    private final ChatBot bot;

    public ChatListener(ChatBot bot) {
        this.bot = bot;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        bot.onChat(event.getPlayer().getName(), PlainTextComponentSerializer.plainText().serialize(event.message()));
    }
}
