package me.skerik.aiFriend

import org.bukkit.Bukkit
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
    private val id = "resourceId"

    private var isAvailable: Boolean = false

    init {
        check()
    }

    fun isAvailable(): Boolean {
        return isAvailable
    }

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        if (event.player.isOp && isAvailable) {
            event.player.sendMessage("-------------------------------------")
            event.player.sendMessage("Update available for AiFriend! Download it at https://www.spigotmc.org/resources/$id/")
            event.player.sendMessage("-------------------------------------")
        }
    }

    private fun check() {
        isAvailable = checkUpdate()
    }

    private fun checkUpdate(): Boolean {
        Bukkit.getLogger().info("AI Friend is checking for updates...")
        return try {
            val localVersion = AiFriend::class.java.`package`.implementationVersion
            val connection = URL("$url$id").openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            val raw = BufferedReader(InputStreamReader(connection.inputStream)).readLine()

            val remoteVersion = if (raw.contains("-")) {
                raw.split("-")[0].trim()
            } else {
                raw
            }

            !localVersion.equals(remoteVersion, ignoreCase = true)
        } catch (e: IOException) {
            false
        }
    }
}