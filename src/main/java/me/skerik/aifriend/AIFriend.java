package me.skerik.aifriend;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import me.skerik.aifriend.ai.ApiClient;
import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.command.MainCommand;
import me.skerik.aifriend.config.ConfigUpdater;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.listener.ChatListener;
import me.skerik.aifriend.util.Banner;
import me.skerik.aifriend.util.Stats;
import me.skerik.aifriend.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Startup order (keep it, it is the point of this class):
 * <ol>
 *   <li>{@code onEnable}, main thread, must stay fast: config (+ automatic update), settings, messages, scheduler,
 *       command registration, listeners.</li>
 *   <li>{@link #finishStartup()} runs on the first tick, after every other plugin has enabled: build the
 *       {@link Context} and the chat bot, start timers, print the banner.</li>
 * </ol>
 * There is no database and nothing blocking at startup: the only network access (AI proxy, update check) is async.
 */
public final class AIFriend extends JavaPlugin implements Listener {

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile @Nullable Context context;
    private Scheduler scheduler;
    private @Nullable UpdateChecker updater;
    /** Cancel handles of repeating tasks; run all of them on disable. */
    private final List<Runnable> timers = new CopyOnWriteArrayList<>();
    private volatile boolean shuttingDown;
    private long enableStarted;

    @Override
    public void onEnable() {
        enableStarted = System.nanoTime();
        saveDefaultConfig();
        if (ConfigUpdater.update(this) > 0) reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.locale());
        scheduler = new PaperScheduler(this);

        List<String> aliases = settings.aliases();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            MainCommand command = new MainCommand(this);
            event.registrar().register(command.build(), "AI Friend commands", aliases);
            event.registrar().register(command.buildLegacyReload(), "Reload AI Friend (same as /aifriend reload)");
        });
        getServer().getPluginManager().registerEvents(this, this);
        scheduler.runGlobal(this::finishStartup);
    }

    /** Runs on the first tick: every other plugin has enabled by now. */
    private void finishStartup() {
        if (shuttingDown) return;
        String version = getPluginMeta().getVersion();
        ChatBot bot = new ChatBot(getLogger(), scheduler, s -> new ApiClient(s, version),
                (name, text) -> scheduler.runGlobal(() -> Bukkit.getServer().sendMessage(messages.get("chat.format",
                        Messages.text("bot", name), Messages.text("text", text)))),
                () -> Bukkit.getOnlinePlayers().size(), System::currentTimeMillis, settings);
        context = new Context(this, scheduler, settings, messages, bot);
        getServer().getPluginManager().registerEvents(new ChatListener(bot), this);
        bot.start();
        timers.add(bot::stop);

        if (settings.updateCheck()) {
            updater = new UpdateChecker(version, getLogger());
            scheduler.runAsync(updater::check);
            timers.add(scheduler.runAsyncTimer(updater::check, 12L * 60 * 60 * 20, 12L * 60 * 60 * 20));
        }
        boolean stats = Stats.start(this, settings);
        if (!stats) getLogger().fine("bStats not started (disabled in config.yml or no bStats id in this build)");

        if (!settings.hasApiKey()) {
            getLogger().warning("api-key is not set in plugins/ai-friend/config.yml: the bot stays silent until you add a key (\"trial\" gives a free 3-day trial).");
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Bot", !settings.enabled() ? "disabled in config.yml"
                : !settings.hasApiKey() ? "idle, api-key not set"
                : settings.chatbotName() + " (" + settings.model() + ", every " + settings.intervalSeconds() + " s)");
        details.put("Language", settings.language() + " / messages " + settings.locale() + "   Updates " + (settings.updateCheck() ? "on" : "off"));
        Banner.print(getComponentLogger(), version, getServer().getMinecraftVersion(), details,
                (System.nanoTime() - enableStarted) / 1_000_000);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Context ctx = context;
        if (ctx == null) return;
        Player player = event.getPlayer();
        UpdateChecker u = updater;
        if (u != null && u.newerVersion() != null && player.hasPermission("aifriend.admin")) {
            scheduler.runForEntity(player, () -> player.sendMessage(ctx.messages().get("update.available",
                    Messages.text("version", String.valueOf(u.newerVersion())),
                    Messages.text("url", String.valueOf(u.url())))), null);
        }
    }

    /** /aifriend reload: config.yml values and messages. Command aliases need a restart. The bot wakes up and restarts its timer. */
    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = Messages.load(this, settings.locale());
        Context ctx = context;
        if (ctx != null) {
            ctx.update(settings, messages);
            ctx.bot.reload(settings);
        }
    }

    public @Nullable Context context() {
        return context;
    }

    public Messages messages() {
        return messages;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        timers.forEach(Runnable::run);
        timers.clear();
        context = null;
        getComponentLogger().info(Component.text("AI Friend disabled. Goodbye!", NamedTextColor.GRAY));
    }
}
