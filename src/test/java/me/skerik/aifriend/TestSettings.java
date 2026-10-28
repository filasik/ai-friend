package me.skerik.aifriend;

import java.util.List;
import me.skerik.aifriend.config.Settings;

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

    public static TestSettings defaults() {
        return new TestSettings();
    }

    public Settings build() {
        return new Settings("en", false, false, List.of(), enabled, apiKey, model, chatbotName, language,
                personalization, intervalSeconds, stopWhenIgnored, sleepMinutes, maxQueuedMessages, maxReplyLength,
                10, 30);
    }
}
