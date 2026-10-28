package me.skerik.aifriend;

import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.config.Settings;
import java.util.logging.Logger;

/**
 * Everything commands and listeners need, built once startup has finished (so it is null before that and
 * the plugin answers "not ready"). Settings and messages are swapped atomically on /aifriend reload.
 */
public final class Context {

    public final AIFriend plugin;
    public final Scheduler scheduler;
    public final Logger log;
    public final ChatBot bot;

    private volatile Settings settings;
    private volatile Messages messages;

    public Context(AIFriend plugin, Scheduler scheduler, Settings settings, Messages messages, ChatBot bot) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.log = plugin.getLogger();
        this.bot = bot;
        update(settings, messages);
    }

    public void update(Settings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }
}
