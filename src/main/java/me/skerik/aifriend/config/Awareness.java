package me.skerik.aifriend.config;

import java.util.logging.Logger;
import me.skerik.aifriend.chat.BoredomDetector;
import me.skerik.aifriend.chat.ChatSupervisor;
import me.skerik.aifriend.chat.Mood;
import me.skerik.aifriend.chat.Relationships;
import me.skerik.aifriend.chat.ServerContext;
import org.bukkit.configuration.file.FileConfiguration;

/** The "world awareness and personality" options (all off by default). Part of {@link ChatFeatures}. */
public record Awareness(
        ServerContext.Config context, Mood.Config mood, Relationships.Config relationships,
        BoredomDetector.Config boredom, ChatSupervisor.Config supervision
) {

    public static Awareness defaults() {
        return new Awareness(ServerContext.Config.defaults(), Mood.Config.defaults(), Relationships.Config.defaults(),
                BoredomDetector.Config.defaults(), ChatSupervisor.Config.defaults());
    }

    public static Awareness load(FileConfiguration c, Logger log) {
        return new Awareness(
                ServerContext.Config.load(c.getConfigurationSection("server-context"), log),
                Mood.Config.load(c.getConfigurationSection("mood"), log),
                Relationships.Config.load(c.getConfigurationSection("relationships"), log),
                BoredomDetector.Config.load(c.getConfigurationSection("boredom"), log),
                ChatSupervisor.Config.load(c.getConfigurationSection("supervision"), log));
    }
}
