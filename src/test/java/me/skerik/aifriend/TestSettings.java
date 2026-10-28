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

    public String apiKey = "sk-test1234567890";
    public boolean enabled = true;
    public String model = "gpt-4o-mini";
    public String chatbotName = "AI Friend";
    public String language = "English";
    public String personalization = "";
    public int intervalSeconds = 60;
    public boolean stopWhenIgnored = true;
    public int sleepMinutes = 30;
    public int maxQueuedMessages = 50;
    public int maxReplyLength = 256;
    public ProviderSettings provider = ProviderSettings.defaults();
    public ChatFeatures features = ChatFeatures.defaults();

    public static TestSettings defaults() {
        return new TestSettings();
    }

    public TestSettings personas(PersonaSelector.Config c) {
        features = new ChatFeatures(c, features.memory(), features.limits(), features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings memory(ConversationMemory.Config c) {
        features = new ChatFeatures(features.personas(), c, features.limits(), features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings limits(RateLimiter.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), c, features.cost(), features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings cost(CostTracker.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), c, features.replyFilter(),
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings replyFilter(ReplyFilter.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(), c,
                features.mentions(), features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings mentions(MentionDetector.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), c, features.optOut(), features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings optOut(OptOutStore.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), c, features.events(), features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings events(EventPrompts.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), c, features.longMemory(), features.awareness());
        return this;
    }

    public TestSettings longMemory(LongTermMemory.Config c) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), features.events(), c, features.awareness());
        return this;
    }

    public TestSettings awareness(Awareness a) {
        features = new ChatFeatures(features.personas(), features.memory(), features.limits(), features.cost(),
                features.replyFilter(), features.mentions(), features.optOut(), features.events(), features.longMemory(), a);
        return this;
    }

    public Settings build() {
        return new Settings("en", false, false, List.of(), enabled, apiKey, model, chatbotName, language,
                personalization, intervalSeconds, stopWhenIgnored, sleepMinutes, maxQueuedMessages, maxReplyLength,
                10, 30, provider, features, Settings.Storage.defaults());
    }
}
