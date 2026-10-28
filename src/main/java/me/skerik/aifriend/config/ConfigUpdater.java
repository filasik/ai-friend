package me.skerik.aifriend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adds keys that a newer version introduced to an existing config.yml, with their comments, and never touches
 * values the admin already set. Runs only when {@code config-version} in the file is lower than the bundled one.
 */
public final class ConfigUpdater {

    public static final String VERSION_KEY = "config-version";

    /**
     * Keys that were renamed in a later version (old path to new path). When an older config is updated the admin's
     * value moves to the new key. AI Friend 1.x used snake_case names; {@code enabled} and {@code personalization}
     * kept theirs.
     */
    public static final Map<String, String> RENAMES = Map.of(
            "openai_api_key", "api-key",
            "gpt_version", "model",
            "chatbot_name", "chatbot-name",
            "preffered_language", "language",
            "timer_interval", "interval-seconds",
            "player_dissinterest", "stop-when-ignored");

    private ConfigUpdater() {
    }

    /** Updates plugins/ai-friend/config.yml in place (after a backup). Returns the number of keys added. */
    public static int update(JavaPlugin plugin) {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return 0;
            defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Built-in config.yml is broken: " + e.getMessage());
            return 0;
        }
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Cannot read config.yml for the update check: " + e.getMessage());
            return 0;
        }
        int have = user.getInt(VERSION_KEY, 0);
        if (have >= defaults.getInt(VERSION_KEY, 1)) return 0;
        try {
            Files.copy(file, file.resolveSibling("config.yml.bak-v" + have), StandardCopyOption.REPLACE_EXISTING);
            int added = merge(user, defaults) + migrate(user, RENAMES);
            user.save(file.toFile());
            plugin.getLogger().info("config.yml updated to version " + user.getInt(VERSION_KEY) + ": " + added
                    + " new option(s) added (backup: config.yml.bak-v" + have + ")");
            return added;
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot update config.yml: " + e.getMessage() + "; using built-in defaults for new options");
            return 0;
        }
    }

    /** Moves the value of every old key that exists to its new name (the new key was added by {@link #merge} first). */
    public static int migrate(YamlConfiguration user, Map<String, String> renames) {
        int moved = 0;
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            if (!user.contains(rename.getKey(), true)) continue;
            user.set(rename.getValue(), user.get(rename.getKey()));
            user.set(rename.getKey(), null);
            moved++;
        }
        return moved;
    }

    /** Copies every key (and its comments) that {@code user} lacks from {@code defaults}; returns how many. */
    public static int merge(YamlConfiguration user, YamlConfiguration defaults) {
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (key.equals(VERSION_KEY)) continue;
            if (user.contains(key, true)) {
                // an option the admin already has but without any comment (e.g. a renamed 1.x key) gets the documentation
                if (user.getComments(key).isEmpty()) {
                    List<String> comments = new ArrayList<>(defaults.getComments(key));
                    while (!comments.isEmpty() && comments.getFirst() == null) comments.removeFirst(); // no leading blank line
                    user.setComments(key, comments);
                }
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                user.createSection(key);
            } else {
                user.set(key, defaults.get(key));
                added++;
            }
            user.setComments(key, defaults.getComments(key));
            user.setInlineComments(key, defaults.getInlineComments(key));
        }
        user.set(VERSION_KEY, defaults.getInt(VERSION_KEY, 1));
        user.setComments(VERSION_KEY, defaults.getComments(VERSION_KEY));
        return added;
    }
}
