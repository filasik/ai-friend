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
import java.time.Duration
import java.util.*
import kotlin.concurrent.schedule

import com.theokanning.openai.*
import com.theokanning.openai.completion.CompletionRequest
import com.theokanning.openai.service.OpenAiService


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
            if (Bukkit.getOnlinePlayers().isNotEmpty() && playerMessages.isNotEmpty()) {
                performOpenAICall()}
        }

    }

    private fun performOpenAICall()
    {
        for (message in playerMessages) {
            Bukkit.broadcastMessage(message);
        }

        var prompt = "You are a chatbot on a minecraft server, your name is: $openaiChatbotName " +
                "\nyou will always speak in this language: $openaiPrefferedLanguage" +
                "\nthese are the messages players have sent since your last call in the format [Player name]: [Player message] \n"
        for (message in playerMessages) {
            prompt += message +"\n"
        }
        prompt += "Create a response that may be relevant to the players messages if there is nothing to respond to you may have creative freedom on what to say"

        try {
            // Initialize the OpenAI service with the API key
            val openaiService = OpenAiService(openaiApiKey)

            // Create a completion request with the generated prompt
            val completionRequest = CompletionRequest.builder()
                .model("gpt-3.5-turbo")  // Choose the OpenAI model you prefer (e.g., GPT-4)
                .prompt(prompt)
                .maxTokens(30)  // Adjust max tokens to control the length of the response
                .temperature(0.7)  // Adjust temperature to control randomness/creativity
                .build()

            // Send the request and get the response
            val response = openaiService.createCompletion(completionRequest)

            // Retrieve the generated message from OpenAI
            val openAiResponse = response.choices[0].text.trim()

            // Broadcast the response to all players in the game
            Bukkit.broadcastMessage("$openaiChatbotName: $openAiResponse")
        } catch (e: Exception) {
            // Log any errors that occur during the API call
            logger.severe("Error communicating with OpenAI: ${e.message}")
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
