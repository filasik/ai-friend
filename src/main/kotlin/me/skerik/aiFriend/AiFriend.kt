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
import java.net.InetAddress
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.schedule

class AiFriend : JavaPlugin(), Listener {
    private var config: FileConfiguration = getConfig()
    private val playerMessages: MutableList<String> = mutableListOf()
    private var lastMessage: String = ""
    private var serverIP: String = "unknown"

    private var timer: Timer? = null


    private val ANSI_GREEN = "\u001B[32m"
    private val ANSI_YELLOW = "\u001B[33m"
    private val ANSI_RESET = "\u001B[0m"
    private val ANSI_RED = "\u001B[31m"

    private var openaiApiKey = config.getString("openai_api_key");
    private var enabled = config.getBoolean("enabled");
    private var playerDisinterest = config.getBoolean("player_dissinterest");
    private var timerInterval = config.getInt("timer_interval");
    private var openaiGptVersion = config.getString("gpt_version");
    private var openaiChatbotName = config.getString("chatbot_name");
    private var openaiPrefferedLanguage = config.getString("preffered_language");
    private var openaiPersonalization = config.getString("personalization");

    override fun onEnable() {
        // Plugin startup logic
        logger.info("${ANSI_GREEN}AI Friend is here for you!${ANSI_RESET}")

        // Vytvoření konfiguračního souboru
        config.addDefault("openai_api_key", "your_openai_api_key_here");
        config.addDefault("enabled", true);
        config.addDefault("player_dissinterest", true);
        config.addDefault("timer_interval", 60);
        config.addDefault("gpt_version", "gpt-4o-mini");
        config.addDefault("chatbot_name", "AI Friend");
        config.addDefault("preffered_language", "English");
        config.addDefault("personalization", "")
        config.options().copyDefaults(true);
        saveConfig();

        // Add comments to the config.yml file
        val configFile = File(dataFolder, "config.yml")
        if (configFile.exists()) {
            val configContent = """
            # OpenAI API key for accessing the OpenAI services
            # You can get an API key at https://platform.openai.com/signup
            # Trial API key is available for free, but has limited functionality
            # To use trial API key, set the value to "trial" (3 days of free trial)
            openai_api_key: ${config.getString("openai_api_key")}
    
            # Enable or disable the AI Friend plugin
            enabled: ${config.getBoolean("enabled")}
            
            # Disable the AI Friend plugin when players are not interested in talking to AI
            player_dissinterest: ${config.getBoolean("player_dissinterest")}
    
            # Interval in seconds for the timer to call the OpenAI API
            # Must be positive, determines how often the AI Friend will respond to player messages
            timer_interval: ${config.getInt("timer_interval")}
    
            # Version of the GPT model to use gpt-4o-mini, gpt-4o, ...
            # Changes doesn't apply if you use trial API key.
            gpt_version: ${config.getString("gpt_version")}
    
            # Name of the OpenAI chatbot
            chatbot_name: ${config.getString("chatbot_name")}
    
            # Preferred language for the chatbot responses - English, German, Czech, ...
            preffered_language: ${config.getString("preffered_language")}
            
            # Personalization for the chatbot responses
            # You can add some personalization for the chatbot responses
            # For example: Our website is https://www.example.com and sometimes tell a joke.
            # Can be empty
            personalization: ${config.getString("personalization")}
            """.trimIndent()

            configFile.writeText(configContent)
        }

        //get server IP address and save it to var
        serverIP = InetAddress.getLocalHost().toString();

        Bukkit.getPluginManager().registerEvents(UpdateChecker(), this)

        // Log - pokud není nastaven API klíč
        if (openaiApiKey == "your_openai_api_key_here") {
            logger.warning("API Key not set. Please set your OpenAI API key in the config file.")
        }

        if(openaiApiKey != "your_openai_api_key_here") {
            Bukkit.getPluginManager().registerEvents(this, this)

            // Ensure the timer interval is positive
            if (timerInterval > 0) {

                timer = Timer()

                // Timer val is in seconds so we need to convert it to milliseconds
                val timerIntervalMillis = timerInterval * 1000L

                // Add a timer that calls the OpenAI API every timerIntervalMillis
                timer?.schedule(0L, timerIntervalMillis) {
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
            if(playerDisinterest) {
                append("this is the most important thing: If it looks like players are no longer interested in talking to AI in their language $openaiPrefferedLanguage (people are angry about AI, telling something like stop AI etc.) respond only with STOP MESSAGING (we will catch that and stop you for a while).")
            }
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
            "gpt_version": "$openaiGptVersion",
            "server_ip": "$serverIP"
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
                logger.info("${ANSI_GREEN}AI Friend is going to sleep for 30 minutes. Players are not interested in talking to me.${ANSI_RESET}")
                timer = Timer()
                timer?.schedule(1800000) {
                    enabled = true
                    logger.info("${ANSI_GREEN}AI Friend is awake. Let's talk!${ANSI_RESET}")
                }
            } else {
                Bukkit.broadcastMessage("$openaiChatbotName: $responseUTF8Clean")
                lastMessage = responseUTF8Clean // save last message for context
            }

            playerMessages.clear()
        } else {
            logger.severe("Error communicating with server: ${conn.responseCode}")

            // if error is 401 (Unauthorized) send more info to the log
            if (conn.responseCode == 401) {
                logger.severe("${ANSI_RED}Unauthorized. Please check your OpenAI API key.${ANSI_RESET}")
                if (openaiApiKey == "your_openai_api_key_here") {
                    logger.severe("${ANSI_RED}API Key not set. Please set your OpenAI API key in the config file.${ANSI_RESET}")
                }
                if (openaiApiKey == "trial") {
                    logger.severe("${ANSI_RED}Your trial API key expired. Please use a paid API key to access the full functionality.${ANSI_RESET}")
                    logger.severe("${ANSI_RED}You can get an API key at https://platform.openai.com/signup${ANSI_RESET}")
                    logger.severe("${ANSI_RED}If you want to use our solution, contact developers on Discord.${ANSI_RESET}")
                }
            }
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
            sender.sendMessage("${ChatColor.YELLOW}Personalization: ${ChatColor.WHITE}$openaiPersonalization")
            sender.sendMessage("${ChatColor.YELLOW}Timer Interval: ${ChatColor.WHITE}$timerInterval seconds")
            sender.sendMessage("${ChatColor.YELLOW}GPT Version: ${ChatColor.WHITE}$openaiGptVersion")
            sender.sendMessage("${ChatColor.YELLOW}Enabled: ${ChatColor.WHITE}$enabled")
            sender.sendMessage("${ChatColor.YELLOW}Player Disinterest: ${ChatColor.WHITE}$playerDisinterest")
            sender.sendMessage("${ChatColor.YELLOW}Reload: ${ChatColor.WHITE}/aifriendreload")
        } else {
            sender.sendMessage("${ChatColor.RED}You must be a server operator to use this command.")
        }
        return true
    } else if (command.name.equals("aifriendreload", ignoreCase = true)) {
        if (sender is Player && sender.isOp) {
            reloadConfig()
            config = getConfig()
            enabled = config.getBoolean("enabled")
            playerDisinterest = config.getBoolean("player_dissinterest")
            timerInterval = config.getInt("timer_interval")
            openaiApiKey = config.getString("openai_api_key")
            openaiGptVersion = config.getString("gpt_version")
            openaiChatbotName = config.getString("chatbot_name")
            openaiPrefferedLanguage = config.getString("preffered_language")
            openaiPersonalization = config.getString("personalization")
            sender.sendMessage("${ChatColor.GREEN}AI Friend configuration reloaded.")
            //log to console
            logger.info("${ANSI_GREEN}AI Friend configuration reloaded.${ANSI_RESET}")

            // Reset the times, but i setill want to keep the functionallity
            timer?.cancel()
            timer = Timer()
            timer?.schedule(0L, timerInterval * 1000L) {
                if (Bukkit.getOnlinePlayers().isNotEmpty() && playerMessages.isNotEmpty() && enabled) {
                    performOpenAICall()
                }
            }

            // Re-register events and restart timers
            Bukkit.getPluginManager().registerEvents(UpdateChecker(), this)
            Bukkit.getPluginManager().registerEvents(this, this)
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
