package me.skerik.aifriend.config;

import java.util.logging.Logger;
import me.skerik.aifriend.chat.ConversationMemory;
import me.skerik.aifriend.chat.CostTracker;
import me.skerik.aifriend.chat.EventPrompts;
import me.skerik.aifriend.chat.MentionDetector;
import me.skerik.aifriend.chat.OptOutStore;
import me.skerik.aifriend.chat.PersonaSelector;
import me.skerik.aifriend.chat.RateLimiter;
import me.skerik.aifriend.chat.ReplyFilter;
import me.skerik.aifriend.longmemory.LongTermMemory;
import org.bukkit.configuration.file.FileConfiguration;

/** The optional chat features (all off by default), each parsed by its own class. Part of the {@link Settings} snapshot. */
public record ChatFeatures(
        PersonaSelector.Config personas, ConversationMemory.Config memory, RateLimiter.Config limits,
        CostTracker.Config cost, ReplyFilter.Config replyFilter, MentionDetector.Config mentions,
        OptOutStore.Config optOut, EventPrompts.Config events, LongTermMemory.Config longMemory,
        Awareness awareness
) {

    public static ChatFeatures defaults() {
        return new ChatFeatures(PersonaSelector.Config.defaults(), ConversationMemory.Config.defaults(),
                RateLimiter.Config.defaults(), CostTracker.Config.defaults(), ReplyFilter.Config.defaults(),
                MentionDetector.Config.defaults(), OptOutStore.Config.defaults(), EventPrompts.Config.defaults(),
                LongTermMemory.Config.defaults(), Awareness.defaults());
    }

    public static ChatFeatures load(FileConfiguration c, Logger log) {
        return new ChatFeatures(
                PersonaSelector.Config.load(c.getConfigurationSection("personas"), log),
                ConversationMemory.Config.load(c.getConfigurationSection("memory"), log),
                RateLimiter.Config.load(c.getConfigurationSection("limits"), log),
                CostTracker.Config.load(c.getConfigurationSection("cost"), log),
                ReplyFilter.Config.load(c.getConfigurationSection("reply-filter"), log),
                MentionDetector.Config.load(c.getConfigurationSection("mentions"), log),
                OptOutStore.Config.load(c.getConfigurationSection("opt-out"), log),
                EventPrompts.Config.load(c.getConfigurationSection("events"), log),
                LongTermMemory.Config.load(c.getConfigurationSection("long-term-memory"), log),
                Awareness.load(c, log));
    }
}
