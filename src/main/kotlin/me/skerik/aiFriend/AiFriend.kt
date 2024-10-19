package me.skerik.aiFriend

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.concurrent.schedule


class AiFriend : JavaPlugin(), Listener {

    private var config: FileConfiguration = getConfig()
    private val playerMessages: MutableList<String> = mutableListOf()


    private val openaiApiKey = config.getString("openai_api_key");
    private val openaiChatbotName = config.getString("openai_chatbot_name");
    private val openaiPrefferedLanguage = config.getString("preffered_language");
    private val openaiCustomDatabase = config.getString("customDatabase");

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

        // Add a timer that calls a new function periodically
        val timer = Timer()
        timer.schedule(0L, 10000L) { // 60,000 milliseconds = 60 seconds
            performOpenAICall()}
    }

    private fun performOpenAICall()
    {
        for (message in playerMessages) {
            Bukkit.broadcastMessage(message);
        }
        val size = playerMessages.size
        Bukkit.broadcastMessage(" pocet zprav: $size");
        playerMessages.clear()
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {


        // Získání zprávy z chatu
        val playerMessage = event.message

        // jméno hráče
        val playerName = event.player.name

        playerMessages.add("- $playerName: $playerMessage")
        

        //performOpenAICall()

        //Bukkit.broadcastMessage(openaiChatbotName+"Hráč poslal: $playerMessage, OpenAI klíč: $openaiApiKey");
        // Bukkit.broadcastMessage("Hráč poslal: $playerMessage, OpenAI klíč: $openaiApiKey");
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("aifriend", ignoreCase = true)) {
            if (sender is Player && sender.isOp) {
                sender.sendMessage("${ChatColor.GREEN}AI Friend Configuration:")
                sender.sendMessage("${ChatColor.YELLOW}API Key: ${ChatColor.WHITE}$openaiApiKey")
                sender.sendMessage("${ChatColor.YELLOW}Chatbot Name: ${ChatColor.WHITE}$openaiChatbotName")
                sender.sendMessage("${ChatColor.YELLOW}Preferred Language: ${ChatColor.WHITE}$openaiPrefferedLanguage")
                sender.sendMessage("${ChatColor.YELLOW}Custom Database: ${ChatColor.WHITE}$openaiCustomDatabase")
            } else {
                sender.sendMessage("${ChatColor.RED}You must be a server operator to use this command.")
            }
            return true
        }
        return false
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
