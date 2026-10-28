package me.skerik.aifriend;

import java.util.List;
import me.skerik.aifriend.ai.provider.ProviderSettings;
import me.skerik.aifriend.chat.ConversationMemory;
import me.skerik.aifriend.chat.CostTracker;
import me.skerik.aifriend.chat.EventPrompts;
import me.skerik.aifriend.chat.MentionDetector;
import me.skerik.aifriend.chat.OptOutStore;
import me.skerik.aifriend.chat.PersonaSelector;
import me.skerik.aifriend.chat.RateLimiter;
import me.skerik.aifriend.chat.ReplyFilter;
import me.skerik.aifriend.config.Awareness;
import me.skerik.aifriend.config.ChatFeatures;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.longmemory.LongTermMemory;

/** Builds {@link Settings} for tests without a config file; change a field, then call {@link #build()}. */
public final class TestSettings {

    /** Key of the default openai provider; blank means no usable provider. Ignored when {@link #provider} is set. */
    public String apiKey = "sk-test1234567890";
    public boolean enabled = true;
    public String chatbotName = "AI Friend";
    public String language = "English";
    public String personalization = "";
    public int intervalSeconds = 60;
    public boolean stopWhenIgnored = true;
    public int sleepMinutes = 30;
    public Settings.Chattiness chattiness = Settings.Chattiness.NORMAL;
    public int maxQueuedMessages = 50;
    public int maxReplyLength = 256;
    public ProviderSettings provider;
    public ChatFeatures features = ChatFeatures.defaults();

    public static TestSettings defaults() {
        return new TestSettings();
    }

    public TestSettings personas(PersonaSelector.Config c) {
        features = new ChatFeatures(c, features.memory(), features.limits(), features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings memory(ConversationMemory.Config c) {
        features = new ChatFeatures(features.personas(), c, features.limits(), features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings limits(RateLimiter.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), c, features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings cost(CostTracker.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), c, features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings replyFilter(ReplyFilter.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(), c,
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings mentions(MentionDetector.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), c, features.optOut(), features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings optOut(OptOutStore.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), c, features.events(), features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings events(EventPrompts.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), c, features.longMemory(), features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings longMemory(LongTermMemory.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), features.events(), c, features.awareness(), features.knowledge());
        return this;
    }

    public TestSettings awareness(Awareness a) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), features.events(), features.longMemory(), a, features.knowledge());
        return this;
    }

    private ProviderSettings defaultProvider() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("providers.openai.api-key", apiKey);
        java.util.logging.Logger quiet = java.util.logging.Logger.getAnonymousLogger();
        quiet.setLevel(java.util.logging.Level.OFF);
        return ProviderSettings.load(y, quiet);
    }

    public TestSettings knowledge(me.skerik.aifriend.knowledge.ServerKnowledge.Config k) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), features.events(), features.longMemory(),
                features.awareness(), k);
        return this;
    }

    public Settings build() {
        return new Settings("en", false, false, List.of(), enabled, chatbotName, language,
                personalization, intervalSeconds, stopWhenIgnored, sleepMinutes, chattiness, maxQueuedMessages, maxReplyLength,
                10, 30, provider == null ? defaultProvider() : provider, features, Settings.Storage.defaults());
    }
}
