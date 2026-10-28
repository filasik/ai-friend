package me.skerik.aifriend.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MiniMessage strings from messages_&lt;locale&gt;.yml. Lookup order per key: the admin's file, the bundled file of
 * the same locale, the bundled English file. So a newer version never shows a missing key, and an admin only
 * has to keep the keys they actually changed.
 */
public final class Messages {

    /** Locales that ship inside the jar (messages_&lt;locale&gt;.yml); each one is copied to the plugin folder once. */
    public static final List<String> BUNDLED_LOCALES = List.of("en", "cs");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YamlConfiguration user;
    private final YamlConfiguration bundledLocale;
    private final YamlConfiguration bundledEnglish;
    private final Logger log;
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    private Messages(YamlConfiguration user, YamlConfiguration bundledLocale, YamlConfiguration bundledEnglish, Logger log) {
        this.user = user;
        this.bundledLocale = bundledLocale;
        this.bundledEnglish = bundledEnglish;
        this.log = log;
    }

    public static Messages load(JavaPlugin plugin, String locale) {
        String safe = locale.toLowerCase(Locale.ROOT).replaceAll("[^a-z_]", "");
        for (String bundled : BUNDLED_LOCALES) {
            File f = new File(plugin.getDataFolder(), "messages_" + bundled + ".yml");
            if (!f.exists()) plugin.saveResource("messages_" + bundled + ".yml", false);
        }
        File file = new File(plugin.getDataFolder(), "messages_" + safe + ".yml");
        YamlConfiguration user = new YamlConfiguration();
        if (file.exists()) {
            try {
                user.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().warning("Cannot read " + file.getName() + ": " + e.getMessage() + "; using built-in text");
            }
        } else {
            plugin.getLogger().warning("messages_" + safe + ".yml not found; using English");
        }
        return new Messages(user, bundled(plugin, safe), bundled(plugin, "en"), plugin.getLogger());
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String locale) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages_" + locale + ".yml")) {
            if (in != null) yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Built-in messages_" + locale + ".yml is broken: " + e.getMessage());
        }
        return yaml;
    }

    /** Raw MiniMessage text of a key. */
    public String raw(String key) {
        for (YamlConfiguration source : List.of(user, bundledLocale, bundledEnglish)) {
            String value = source.getString(key);
            if (value != null) return value;
        }
        if (reportedMissing.add(key)) log.warning("Missing message key: " + key);
        return "<red>[" + key + "]";
    }

    public List<String> rawList(String key) {
        for (YamlConfiguration source : List.of(user, bundledLocale, bundledEnglish)) {
            if (source.isList(key)) return source.getStringList(key);
            if (source.isString(key)) return List.of(source.getString(key, ""));
        }
        if (reportedMissing.add(key)) log.warning("Missing message key: " + key);
        return List.of("<red>[" + key + "]");
    }

    public Component get(String key, TagResolver... resolvers) {
        return render(raw(key), resolvers);
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        return rawList(key).stream().map(line -> render(line, resolvers)).toList();
    }

    private Component render(String text, TagResolver... resolvers) {
        TagResolver prefix = Placeholder.parsed("prefix", raw("prefix"));
        return MM.deserialize(text, TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    /** Message placeholders: plain text, never parsed as MiniMessage (player names, item names). */
    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
