package me.skerik.aiFriend

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin


class AiFriend : JavaPlugin(), Listener {

    private var config: FileConfiguration = getConfig()

    override fun onEnable() {
        // Plugin startup logic
        logger.info("AI Friend is here for you!")

        // Vytvoření konfiguračního souboru
        config.addDefault("openai_api_key", "your_openai_api_key_here");
        config.addDefault("openai_chatbot_name", "AiFriend");
        config.addDefault("preffered_language", "English");
        config.addDefault("customDatabase", " #if you have some specific answers to some questions, you can add them here");
        config.options().copyDefaults(true);
        saveConfig();

        // Log - pokud není nastaven API klíč
        if (config.getString("openai_api_key") == "your_openai_api_key_here") {

            logger.warning("API Key not set. Please set your OpenAI API key in the config file.")
        }

        // Registrace event listeneru pro chat
        Bukkit.getPluginManager().registerEvents(this, this)
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // API klíč pro OpenAI
        val openaiApiKey = config.getString("openai_api_key");
        val openaiChatbotName = config.getString("openai_chatbot_name");
        val openaiPrefferedLanguage = config.getString("preffered_language");
        val openaiCustomDatabase = config.getString("customDatabase");

        // Získání zprávy z chatu
        val playerMessage = event.message

        Bukkit.broadcastMessage(openaiChatbotName+"Hráč poslal: $playerMessage, OpenAI klíč: $openaiApiKey");
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
