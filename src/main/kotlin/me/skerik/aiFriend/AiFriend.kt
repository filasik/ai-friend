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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.schedule

class AiFriend : JavaPlugin(), Listener {
    private var config: FileConfiguration = getConfig()
    private val playerMessages: MutableList<String> = mutableListOf()
    private var lastMessage: String = ""

    private val openaiApiKey = config.getString("openai_api_key");
    private var enabled = config.getBoolean("enabled");
    private val timerInterval = config.getInt("timer_interval");
    private val openaiGptVersion = config.getString("gpt_version");
    private val openaiChatbotName = config.getString("openai_chatbot_name");
    private val openaiPrefferedLanguage = config.getString("preffered_language");
    private val openaiPersonalization = config.getString("personalization");

    override fun onEnable() {
        // Plugin startup logic
        logger.info("AI Friend is here for you!")

        // Vytvoření konfiguračního souboru
        config.addDefault("openai_api_key", "your_openai_api_key_here");
        config.addDefault("enabled", true);
        config.addDefault("timer_interval", 60);
        config.addDefault("gpt_version", "gpt-4o-mini");
        config.addDefault("openai_chatbot_name", "AiFriend");
        config.addDefault("preffered_language", "English");
        config.addDefault("personalization", "")
        config.options().copyDefaults(true);
        saveConfig();

        // Add comments to the config.yml file
        val configFile = File(dataFolder, "config.yml")
        if (configFile.exists()) {
            val configContent = """
            # OpenAI API key for accessing the OpenAI services
            openai_api_key: ${config.getString("openai_api_key")}
    
            # Enable or disable the AI Friend plugin
            enabled: ${config.getBoolean("enabled")}
    
            # Interval in seconds for the timer to call the OpenAI API
            timer_interval: ${config.getInt("timer_interval")}
    
            # Version of the GPT model to use
            gpt_version: ${config.getString("gpt_version")}
    
            # Name of the OpenAI chatbot
            openai_chatbot_name: ${config.getString("openai_chatbot_name")}
    
            # Preferred language for the chatbot responses
            preffered_language: ${config.getString("preffered_language")}
            
            # Personalization for the chatbot responses
            personalization: ${config.getString("personalization")}
            """.trimIndent()

            configFile.writeText(configContent)
        }

        Bukkit.getPluginManager().registerEvents(UpdateChecker(), this)

        // Log - pokud není nastaven API klíč
        if (openaiApiKey == "your_openai_api_key_here") {
            logger.warning("API Key not set. Please set your OpenAI API key in the config file.")
        }

        if(openaiApiKey != "your_openai_api_key_here") {
            Bukkit.getPluginManager().registerEvents(this, this)

            // Ensure the timer interval is positive
            if (timerInterval > 0) {
                // Timer val is in seconds so we need to convert it to milliseconds
                val timerIntervalMillis = timerInterval * 1000L

                // Add a timer that calls the OpenAI API every timerIntervalMillis
                val timer = Timer()
                timer.schedule(0L, timerIntervalMillis) {
                    if (Bukkit.getOnlinePlayers().isNotEmpty() && playerMessages.isNotEmpty() && enabled) {
                        performOpenAICall()
                    }
                }
            } else {
                logger.warning("Timer interval must be positive. Current value: $timerInterval")
            }
        }
    }

    private fun performOpenAICall() {
        // Prepare the prompt based on player messages
        val prompt = buildString {
            append("this is the most important thing: If it looks like players are no longer interested in talking to AI in their language $openaiPrefferedLanguage (people are angry about AI, telling something like stop AI etc.) respond only with STOP MESSAGING (we will catch that and stop you for a while).")
            append("You are a chatbot on a Minecraft server, your name is: $openaiChatbotName .")
            append("you will always and only speak in this language: $openaiPrefferedLanguage. ")
            append("These are the messages players have sent since your last call in the format [Player name]: [Player message]: ")
            for (message in playerMessages) {
                append(message).append(" ")
            }
            append("Your last message was (for context): $lastMessage. ")
            append("Create a response that may be relevant to the players' messages. If there is nothing to respond to, you may have creative freedom on what to say.")
            append("Your answers should be short, like maximally 200 characters.")
            append("Use only emojis like :), :D, :P, :O, :|, :/ and xD. Never use java line breaks or new lines.")
            append("In chat history you see your own last message for context. Its from $openaiChatbotName. Don't use your name in response.")
            if(openaiPersonalization != "") {
                append("This is what server admin wants you to know: $openaiPersonalization")
            }
        }.replace(Regex("[\\r\\n]+"), " ") // Replace newlines and carriage returns with spaces


        val jsonBody = """
        {
            "message": "$prompt",
            "api_key": "$openaiApiKey",
            "gpt_version": "$openaiGptVersion"
        }
        """.trimIndent()
        val url = URL("https://gpt.netuj.eu/api/ai_friend.php")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(jsonBody)
        }

        // Check response
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }

             val responseUTF8 = response.replace("\\\\u([0-9a-fA-F]{4})".toRegex()) { // Translate unicode characters to UTF-8 - escape json
                val charCode = it.groupValues[1].toInt(16)
                charCode.toChar().toString()
            }

            val responseUTF8Clean = responseUTF8.substring(1, responseUTF8.length - 1) // remove " from start and end


            //if there is STOP MESSAGING in the response, add a delay like 30 minutes before sending another API request
            if (responseUTF8Clean.contains("STOP MESSAGING")) {
                enabled = false
                logger.info("AI Friend is going to sleep for 30 minutes. Players are not interested in talking to me.")
                val timer = Timer()
                timer.schedule(1800000) {
                    enabled = true
                    logger.info("AI Friend is awake. Let's talk!")
                }
            } else {
                Bukkit.broadcastMessage("$openaiChatbotName: $responseUTF8Clean")
                lastMessage = responseUTF8Clean // save last message for context
            }

            playerMessages.clear()
        } else {
            logger.severe("Error communicating with server: ${conn.responseCode}")
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val playerMessage = event.message
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
            } else {
                sender.sendMessage("${ChatColor.RED}You must be a server operator to use this command.")
            }
            return true
        }
        return false
    }

    override fun onDisable() {
        logger.info("AI Friend is going to sleep.")
    }
}
