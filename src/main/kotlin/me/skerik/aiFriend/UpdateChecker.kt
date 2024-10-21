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
import javax.net.ssl.HttpsURLConnection

class UpdateChecker : Listener {

    private val url = "https://api.spigotmc.org/legacy/update.php?resource="
    private val id = "120292"

    private var isAvailable: Boolean = false

    private val currentVersion = "1.3.0"

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
        Bukkit.getLogger().info("AI Friend is checking for updates...")
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
                Bukkit.getLogger().info("AI Friend is outdated! New version available: $remoteVersion")
                return true
            }
            false
        } catch (e: IOException) {
            false
        }
    }
}