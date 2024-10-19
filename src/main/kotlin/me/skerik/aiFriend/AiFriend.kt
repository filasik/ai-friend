package me.skerik.aiFriend

import com.theokanning.openai.completion.CompletionRequest
import com.theokanning.openai.completion.CompletionResult
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin


class AiFriend : JavaPlugin(), Listener {

    override fun onEnable() {
        // Plugin startup logic
        logger.info("AI Friend is here for you!")

        // Registrace event listeneru pro chat
        Bukkit.getPluginManager().registerEvents(this, this)
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // Získání zprávy z chatu
        val playerMessage = event.message

        Bukkit.broadcastMessage("Hráč poslal: $playerMessage");
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
