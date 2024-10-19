package me.skerik.aiFriend

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
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
        config.addDefault(
            "customDatabase",
            " #if you have some specific answers to some questions, you can add them here"
        );
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
        timer.schedule(0L, 60000L) { // 60,000 milliseconds = 60 seconds
            if (Bukkit.getOnlinePlayers().isNotEmpty() && playerMessages.isNotEmpty()) {
                performOpenAICall()
            }
        }

    }

    private fun performOpenAICall() {
        // Prepare the prompt based on player messages
        val prompt = buildString {
            append("You are a chatbot on a Minecraft server, your name is: $openaiChatbotName ")
            append("you will always speak in this language: $openaiPrefferedLanguage. ")
            append("These are the messages players have sent since your last call in the format [Player name]: [Player message]: ")
            for (message in playerMessages) {
                append(message).append(" ")
            }
            append("Create a response that may be relevant to the players' messages. If there is nothing to respond to, you may have creative freedom on what to say.")
        }.replace(Regex("[\\r\\n]+"), " ") // Replace newlines and carriage returns with spaces


        val body = "{\"message\": \"$prompt\"}"
        val url = URL("https://gpt.netuj.eu/api/ai_friend.php")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(body)
        }

        // Check response
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            // Process or return the response here

            // Translate unicode characters to UTF-8 - escape json
            val responseUTF8 = response.replace("\\\\u([0-9a-fA-F]{4})".toRegex()) {
                val charCode = it.groupValues[1].toInt(16)
                charCode.toChar().toString()
            }


            Bukkit.broadcastMessage("$openaiChatbotName: $responseUTF8")
        } else {
            logger.severe("Error communicating with server: ${conn.responseCode}")
        }

        playerMessages.clear()
    }



    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {


        // Získání zprávy z chatu
        val playerMessage = event.message

        // jméno hráče
        val playerName = event.player.name

        playerMessages.add("- $playerName: $playerMessage")

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
