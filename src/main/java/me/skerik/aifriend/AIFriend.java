package me.skerik.aifriend;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.ai.provider.EmbedderFactory;
import me.skerik.aifriend.ai.provider.HttpOptions;
import me.skerik.aifriend.ai.provider.ProviderFactory;
import me.skerik.aifriend.api.AiFriendApi;
import me.skerik.aifriend.api.AiFriendResponseEvent;
import me.skerik.aifriend.chat.OptOutStore;
import me.skerik.aifriend.longmemory.LongTermMemory;
import me.skerik.aifriend.storage.Database;
import me.skerik.aifriend.storage.Migrations;
import me.skerik.aifriend.storage.PlayerNotesStore;
import me.skerik.aifriend.storage.RelationshipStore;
import me.skerik.aifriend.world.ContextCollector;
import java.sql.SQLException;
import java.util.logging.Level;
import me.skerik.aifriend.papi.AiFriendExpansion;
import org.bukkit.plugin.ServicePriority;
import me.skerik.aifriend.command.MainCommand;
import me.skerik.aifriend.config.ConfigUpdater;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.config.Settings;
import me.skerik.aifriend.listener.ChatListener;
import me.skerik.aifriend.listener.GameEventListener;
import me.skerik.aifriend.util.Banner;
import me.skerik.aifriend.util.Stats;
import me.skerik.aifriend.util.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
 * The database (only used by long-term memory, off by default) is opened and migrated on an async task before
 * {@code finishStartup}; if that fails, one line is logged and only that feature stays off. Everything else that touches
 * the network (AI provider, update check) is async as well.
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
    private @Nullable Database database;
    private Settings.@Nullable Storage openStorage;
    private int schemaVersion;

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
            event.registrar().register(command.buildAsk(), "Ask the AI Friend a question");
        });
        getServer().getPluginManager().registerEvents(this, this);
        if (needsDatabase(settings)) {
            scheduler.runAsync(() -> {
                openDatabase();
                scheduler.runGlobal(this::finishStartup);
            });
        } else {
            scheduler.runGlobal(this::finishStartup);
        }
    }

    /** The database is only opened for the features that keep data in it. */
    private static boolean needsDatabase(Settings s) {
        return s.features().longMemory().enabled() || s.features().awareness().relationships().enabled();
    }

    /** Opens the pool and runs the migrations (blocking, async threads only). A failure disables the data features only. */
    private synchronized boolean openDatabase() {
        if (database != null) return true;
        Settings.Storage storage = settings.storage();
        try {
            Database db = new Database(storage.dbConfig(getDataFolder().toPath()));
            try {
                schemaVersion = Migrations.migrate(db);
            } catch (SQLException | RuntimeException e) {
                db.close();
                throw e;
            }
            database = db;
            openStorage = storage;
            return true;
        } catch (SQLException | RuntimeException e) {
            String hint = String.valueOf(e.getMessage()).contains("Self signed")
                    ? " Hint: set storage.mariadb.ssl-mode to \"trust\" in config.yml (MySQL with a self-signed certificate)."
                    : "";
            getLogger().log(Level.SEVERE, "Long-term memory and saved relationships are switched off: cannot open the database (" + storage.describe()
                    + "): " + e.getMessage() + ". Check storage in config.yml." + hint + " The chatbot keeps running.");
            return false;
        }
    }

    private synchronized void closeDatabase() {
        Database db = database;
        database = null;
        openStorage = null;
        if (db != null) db.close();
    }

    private HttpOptions httpOptions(Settings s) {
        return new HttpOptions(Duration.ofSeconds(s.connectTimeoutSeconds()), Duration.ofSeconds(s.readTimeoutSeconds()),
                "Mozilla/5.0 (compatible; AIFriend/" + getPluginMeta().getVersion() + ")");
    }

    /**
     * Makes the data features of the bot (long-term memory, relationships) match the current settings (async thread):
     * opens or closes the database when the features or {@code storage} changed, rebuilds them with the new values.
     */
    private synchronized void syncStorage() {
        Context ctx = context;
        if (shuttingDown || ctx == null) return;
        Settings s = settings;
        boolean want = needsDatabase(s);
        if (database != null && (!want || !s.storage().equals(openStorage))) {
            ctx.bot.flush(); // pending relationship changes still need the old database
            ctx.bot.setLongMemory(null);
            ctx.bot.relationships().attach(null);
            closeDatabase();
        }
        if (want && database == null) openDatabase();
        installLongMemory(ctx.bot);
    }

    /** Hands the bot a {@link LongTermMemory} and the relationship store for the open database, or none. No I/O (safe on the main thread). */
    private synchronized void installLongMemory(ChatBot bot) {
        Settings s = settings;
        Database db = database;
        if (s.features().longMemory().enabled() && db != null) {
            bot.setLongMemory(new LongTermMemory(new PlayerNotesStore(db),
                    EmbedderFactory.create(s, httpOptions(s), getLogger()).orElse(null),
                    s.features().longMemory(), System::currentTimeMillis, getLogger()));
        } else {
            bot.setLongMemory(null);
        }
        boolean rel = s.features().awareness().relationships().enabled() && db != null;
        boolean alreadyAttached = bot.relationships().persistent();
        bot.relationships().attach(rel ? new RelationshipStore(db) : null);
        if (rel && !alreadyAttached) {
            scheduler.runAsync(() -> {
                try {
                    bot.relationships().load();
                } catch (SQLException | RuntimeException e) {
                    getLogger().warning("Relationships: could not read the saved records (" + e.getMessage() + ")");
                }
            });
        }
    }

    /** Runs on the first tick: every other plugin has enabled by now. */
    private void finishStartup() {
        if (shuttingDown) return;
        String version = getPluginMeta().getVersion();
        Path mutedFile = getDataFolder().toPath().resolve(settings.features().optOut().file());
        ChatBot bot = new ChatBot(getLogger(), scheduler,
                s -> ProviderFactory.create(s.provider(), httpOptions(s), Clock.systemUTC(), getLogger()),
                (name, text) -> scheduler.runGlobal(() -> Bukkit.getServer().sendMessage(messages.get("chat.format",
                        Messages.text("bot", name), Messages.text("text", text)))),
                new ChatBot.Notifier() {
                    @Override
                    public void tell(UUID player, String key, Map<String, String> placeholders) {
                        Player p = Bukkit.getPlayer(player);
                        if (p != null) scheduler.runForEntity(p, () -> p.sendMessage(messages.get(key, resolvers(placeholders))), null);
                    }

                    @Override
                    public void tellPermission(String permission, String key, Map<String, String> placeholders) {
                        scheduler.runGlobal(() -> {
                            Component text = messages.get(key, resolvers(placeholders));
                            Bukkit.getConsoleSender().sendMessage(text);
                            for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(permission)) p.sendMessage(text);
                        });
                    }
                },
                () -> {
                    var w = Bukkit.getWorlds().getFirst();
                    return new ChatBot.WorldView(w.getName(), w.getTime());
                },
                () -> Bukkit.getOnlinePlayers().size(), System::currentTimeMillis, new OptOutStore(mutedFile, getLogger()), settings);
        context = new Context(this, scheduler, settings, messages, bot);
        getServer().getPluginManager().registerEvents(new ChatListener(bot), this);
        getServer().getPluginManager().registerEvents(new GameEventListener(bot), this);
        ContextCollector collector = new ContextCollector(scheduler, () -> settings.features().awareness().context(),
                System::currentTimeMillis);
        bot.setContextSource(collector::snapshot);
        bot.setInterceptor((name, text, asker, source) -> {
            AiFriendResponseEvent event = new AiFriendResponseEvent(name, text, asker,
                    source == ChatBot.Source.DIRECT ? AiFriendResponseEvent.Source.DIRECT : AiFriendResponseEvent.Source.TIMED);
            getServer().getPluginManager().callEvent(event);
            return event.isCancelled() ? null : event.text();
        });
        getServer().getServicesManager().register(AiFriendApi.class, new AiFriendApi() {
            @Override
            public State state() {
                return State.valueOf(bot.status().state().name());
            }

            @Override
            public String lastMessage() {
                return bot.lastMessage();
            }

            @Override
            public String provider() {
                return bot.status().provider();
            }

            @Override
            public int queuedLines() {
                return bot.status().queued();
            }

            @Override
            public void sleep(Duration duration) {
                bot.sleep(duration);
            }

            @Override
            public boolean wake() {
                return bot.wake();
            }
        }, this, ServicePriority.Normal);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                && new AiFriendExpansion(this).register()) {
            getLogger().info("PlaceholderAPI expansion registered (%aifriend_...%)");
        }
        installLongMemory(bot);
        bot.start();
        timers.add(bot::stop);
        // world picture for the prompt (does the work only when a refresh is due) and saving the relationships
        timers.add(scheduler.runAsyncTimer(collector::tick, 100L, 200L));
        timers.add(scheduler.runAsyncTimer(bot::flushRelationships, 60L * 20, 60L * 20));
        // retention-days and max-notes-per-player, once a day (and a few minutes after the start)
        timers.add(scheduler.runAsyncTimer(() -> {
            LongTermMemory ltm = bot.longMemory();
            if (ltm != null) {
                ltm.prune().whenComplete((n, e) -> {
                    if (e != null) getLogger().warning("Long-term memory: cleanup failed (" + e.getClass().getSimpleName() + ")");
                    else if (n > 0) getLogger().info("Long-term memory: cleanup removed " + n + " old note(s)");
                });
            }
        }, 5L * 60 * 20, 24L * 60 * 60 * 20));

        if (settings.updateCheck()) {
            updater = new UpdateChecker(version, getLogger());
            scheduler.runAsync(updater::check);
            timers.add(scheduler.runAsyncTimer(updater::check, 12L * 60 * 60 * 20, 12L * 60 * 60 * 20));
        }
        boolean stats = Stats.start(this, settings);
        if (!stats) getLogger().fine("bStats not started (disabled in config.yml or no bStats id in this build)");

        ChatBot.Status status = bot.status();
        if (status.state() == ChatBot.State.NO_KEY) {
            if (settings.provider().retiredProxy()) {
                getLogger().warning("config.yml still uses the removed provider \"proxy\" (or the old trial key): AI Friend 3.0 only talks to openai, anthropic, ollama or custom. The bot stays inactive and sends nothing until you set provider.primary and that provider's api-key.");
            } else {
                getLogger().warning("No usable AI provider: set provider.primary and its api-key in plugins/ai-friend/config.yml. The bot stays silent until then and sends nothing.");
            }
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Bot", !settings.enabled() ? "disabled in config.yml"
                : status.state() == ChatBot.State.NO_KEY ? "idle, no usable provider"
                : settings.chatbotName() + " via " + status.provider() + " (every " + settings.intervalSeconds() + " s)");
        if (needsDatabase(settings)) {
            details.put("Memory", database == null ? "long-term memory / relationships OFF (database unavailable)"
                    : "data in " + openStorage.describe() + " (schema v" + schemaVersion + ")");
        }
        var aw = settings.features().awareness();
        List<String> awake = new java.util.ArrayList<>();
        if (aw.context().enabled()) awake.add("context");
        if (aw.mood().enabled()) awake.add("mood");
        if (aw.relationships().enabled()) awake.add("relationships");
        if (aw.boredom().enabled()) awake.add("boredom");
        if (aw.supervision().enabled()) awake.add("supervision");
        if (!awake.isEmpty()) details.put("Awareness", String.join(", ", awake));
        details.put("Language", settings.language() + " / messages " + settings.locale() + "   Updates " + (settings.updateCheck() ? "on" : "off"));
        Banner.print(getComponentLogger(), version, getServer().getMinecraftVersion(), details,
                (System.nanoTime() - enableStarted) / 1_000_000);
    }

    private static TagResolver[] resolvers(Map<String, String> placeholders) {
        return placeholders.entrySet().stream().map(e -> Messages.text(e.getKey(), e.getValue())).toArray(TagResolver[]::new);
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
            scheduler.runAsync(this::syncStorage);
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
        Context ctx = context;
        if (ctx != null) {
            ctx.bot.flush();
            ctx.bot.setLongMemory(null);
            ctx.bot.relationships().attach(null);
        }
        closeDatabase();
        context = null;
        getComponentLogger().info(Component.text("AI Friend disabled. Goodbye!", NamedTextColor.GRAY));
    }
}
