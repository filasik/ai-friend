package me.skerik.aifriend.util;

import me.skerik.aifriend.config.Settings;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * bStats anonymous statistics. Off when {@code metrics: false} in config.yml, when bStats is disabled globally
 * (plugins/bStats/config.yml), or when this build carries no bStats id (bstatsId=0 in gradle.properties, a local build).
 */
public final class Stats {

    private Stats() {
    }

    /** @return true if reporting was started */
    public static boolean start(JavaPlugin plugin, Settings settings) {
        if (!settings.metrics()) return false;
        int id = pluginId(plugin);
        if (id <= 0) return false;
        Metrics metrics = new Metrics(plugin, id);
        metrics.addCustomChart(new SimplePie("locale", settings::locale));
        return true;
    }

    private static int pluginId(JavaPlugin plugin) {
        Properties props = new Properties();
        try (InputStream in = plugin.getResource("build.properties")) {
            if (in == null) return 0;
            props.load(in);
            return Integer.parseInt(props.getProperty("bstats-id", "0").strip());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }
}
