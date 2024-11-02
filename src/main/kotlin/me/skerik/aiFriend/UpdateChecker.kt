package me.skerik.aiFriend

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

class UpdateChecker : Listener {

    private val url = "https://api.spigotmc.org/legacy/update.php?resource="
    private val id = "120292"

    private var isAvailable: Boolean = false

    private val currentVersion = "1.4.0"

    // Define ANSI color codes
    private val ANSI_GREEN = "\u001B[32m"
    private val ANSI_YELLOW = "\u001B[33m"
    private val ANSI_RESET = "\u001B[0m"

    init {
        check()
    }

    private fun isAvailable(): Boolean {
        return isAvailable
    }

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        if (event.player.isOp && isAvailable()) {

            val connection = URL("$url$id").openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            val raw = BufferedReader(InputStreamReader(connection.inputStream)).readLine()

            event.player.sendMessage("${ChatColor.YELLOW}-------------------------------------")
            event.player.sendMessage("${ChatColor.GREEN}Update available for AI Friend! Download it at ${ChatColor.AQUA}https://www.spigotmc.org/resources/$id/")
            event.player.sendMessage("${ChatColor.YELLOW}Your version: ${ChatColor.WHITE}$currentVersion")
            event.player.sendMessage("${ChatColor.YELLOW}New version: ${ChatColor.WHITE}$raw")
            event.player.sendMessage("${ChatColor.YELLOW}-------------------------------------")
        }
    }

    private fun check() {
        isAvailable = checkUpdate()
    }

    private fun checkUpdate(): Boolean {
        Bukkit.getLogger().info("${ANSI_GREEN}AI Friend is checking for updates...${ANSI_RESET}")
        return try {
            var localVersion = currentVersion
            val connection = URL("$url$id").openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            val raw = BufferedReader(InputStreamReader(connection.inputStream)).readLine()

            val remoteVersion = if (raw.contains("-")) {
                raw.split("-")[0].trim()
            } else {
                raw
            }

            //trim - from the local version string
            localVersion = localVersion.split("-")[0].trim()

            //if local != remote return true
            if (localVersion != remoteVersion) {
                //logger without colors
                Bukkit.getLogger().info("${ANSI_YELLOW}-------------------------------------${ANSI_RESET}")
                Bukkit.getLogger().info("${ANSI_GREEN}Update available for AI Friend! Download it at ${ANSI_YELLOW}https://www.spigotmc.org/resources/$id/${ANSI_RESET}")
                Bukkit.getLogger().info("${ANSI_GREEN}Your version: ${ANSI_YELLOW}$currentVersion${ANSI_RESET}")
                Bukkit.getLogger().info("${ANSI_GREEN}New version: ${ANSI_YELLOW}$raw${ANSI_RESET}")
                Bukkit.getLogger().info("${ANSI_YELLOW}-------------------------------------${ANSI_RESET}")
                return true
            }
            false
        } catch (e: IOException) {
            false
        }
    }
}