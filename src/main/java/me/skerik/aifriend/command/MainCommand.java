package me.skerik.aifriend.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.skerik.aifriend.AIFriend;
import me.skerik.aifriend.Context;
import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.config.Settings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.skerik.aifriend.longmemory.LongTermMemory;
import me.skerik.aifriend.storage.PlayerNotesStore;
import org.bukkit.Bukkit;
import java.util.Locale;
import java.util.function.BiConsumer;
import me.skerik.aifriend.chat.CostTracker;
import me.skerik.aifriend.chat.RateLimiter;
import me.skerik.aifriend.chat.SimulationResult;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** /aifriend (info), /aifriend status, /aifriend reload (Brigadier, works from the console too). Aliases come from config.yml. */
public final class MainCommand {

    private static final int MAX_LISTED_NOTES = 30;

    private final AIFriend plugin;

    public MainCommand(AIFriend plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("aifriend")
                .requires(src -> src.getSender().hasPermission("aifriend.use"))
                .executes(this::info)
                .then(Commands.literal("status")
                        .requires(src -> src.getSender().hasPermission("aifriend.admin"))
                        .executes(this::status))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("aifriend.reload"))
                        .executes(this::reload))
                .then(Commands.literal("mute")
                        .requires(src -> src.getSender().hasPermission("aifriend.mute"))
                        .executes(c -> player(c, (ctx, p) -> mute(ctx, p, true))))
                .then(Commands.literal("unmute")
                        .requires(src -> src.getSender().hasPermission("aifriend.mute"))
                        .executes(c -> player(c, (ctx, p) -> mute(ctx, p, false))))
                .then(Commands.literal("sleep")
                        .requires(src -> src.getSender().hasPermission("aifriend.sleep"))
                        .executes(c -> sleep(c, -1))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(c -> sleep(c, IntegerArgumentType.getInteger(c, "minutes")))))
                .then(Commands.literal("wake")
                        .requires(src -> src.getSender().hasPermission("aifriend.sleep"))
                        .executes(this::wake))
                .then(Commands.literal("simulate")
                        .requires(src -> src.getSender().hasPermission("aifriend.simulate"))
                        .executes(c -> simulate(c, true))
                        .then(Commands.literal("nocall").executes(c -> simulate(c, false))))
                .then(Commands.literal("cost")
                        .requires(src -> src.getSender().hasPermission("aifriend.cost"))
                        .executes(this::cost))
                .then(Commands.literal("forget")
                        .requires(src -> src.getSender().hasPermission("aifriend.mute"))
                        .executes(c -> player(c, this::forgetOwn))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission("aifriend.admin"))
                                .suggests(this::suggestPlayers)
                                .executes(c -> withContext(c, ctx -> forgetPlayer(ctx, c.getSource().getSender(),
                                        StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("forget-all")
                        .requires(src -> src.getSender().hasPermission("aifriend.admin"))
                        .executes(c -> withContext(c, ctx -> forgetAll(ctx, c.getSource().getSender()))))
                .then(Commands.literal("memory")
                        .requires(src -> src.getSender().hasPermission("aifriend.mute"))
                        .executes(c -> player(c, this::memoryOwn))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission("aifriend.admin"))
                                .suggests(this::suggestPlayers)
                                .executes(c -> withContext(c, ctx -> memoryOf(ctx, c.getSource().getSender(),
                                        StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("nickname")
                        .requires(src -> src.getSender().hasPermission("aifriend.nickname"))
                        .executes(c -> player(c, this::nicknameInfo))
                        .then(Commands.literal("clear").executes(c -> player(c, (ctx, p) -> nickname(ctx, p, ""))))
                        .then(Commands.argument("nickname", StringArgumentType.greedyString())
                                .executes(c -> player(c, (ctx, p) -> nickname(ctx, p, StringArgumentType.getString(c, "nickname"))))))
                .then(Commands.literal("limits")
                        .requires(src -> src.getSender().hasPermission("aifriend.ask"))
                        .executes(c -> player(c, this::limits)))
                .build();
    }

    /** /ai &lt;question&gt;: ask the bot directly. */
    public LiteralCommandNode<CommandSourceStack> buildAsk() {
        return Commands.literal("ai")
                .requires(src -> src.getSender().hasPermission("aifriend.ask"))
                .executes(c -> {
                    c.getSource().getSender().sendMessage(plugin.messages().get("command.ask.usage"));
                    return 0;
                })
                .then(Commands.argument("question", StringArgumentType.greedyString())
                        .executes(c -> player(c, (ctx, p) -> {
                            p.sendMessage(ctx.messages().get("command.ask.thinking"));
                            ctx.bot.askCommand(p.getUniqueId(), p.getName(), StringArgumentType.getString(c, "question"),
                                    p.hasPermission("aifriend.bypass-limits"));
                        })))
                .build();
    }

    /** The 1.x command, kept so existing habits and scripts keep working. */
    public LiteralCommandNode<CommandSourceStack> buildLegacyReload() {
        return Commands.literal("aifriendreload")
                .requires(src -> src.getSender().hasPermission("aifriend.reload"))
                .executes(this::reload)
                .build();
    }

    private int info(CommandContext<CommandSourceStack> c) {
        c.getSource().getSender().sendMessage(plugin.messages().get("command.info",
                Messages.text("version", plugin.getPluginMeta().getVersion())));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        Context ctx = plugin.context();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        Messages m = ctx.messages();
        Settings s = ctx.settings();
        ChatBot.Status st = ctx.bot.status();
        Component state = m.get("command.state." + st.state().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                Messages.text("minutes", Long.toString((st.waitSeconds() + 59) / 60)),
                Messages.text("seconds", Long.toString(st.waitSeconds())));
        for (Component line : m.list("command.status",
                Messages.text("version", plugin.getPluginMeta().getVersion()),
                Messages.comp("state", state),
                Messages.text("bot", s.chatbotName()),
                Messages.text("model", s.activeModel()),
                Messages.text("language", s.language()),
                Messages.text("key", s.activeMaskedKey()),
                Messages.text("interval", Integer.toString(s.intervalSeconds())),
                Messages.text("queued", Integer.toString(st.queued())),
                Messages.text("max-queued", Integer.toString(s.maxQueuedMessages())),
                Messages.text("failures", Integer.toString(st.failures())))) {
            sender.sendMessage(line);
        }
        for (Component line : m.list("command.status-extra",
                Messages.text("provider", st.provider()),
                Messages.text("muted", Integer.toString(st.mutedPlayers())),
                Messages.text("persona", st.persona()),
                Messages.text("turns", Integer.toString(st.memoryTurns())),
                Messages.text("longmemory", st.longMemory()))) {
            sender.sendMessage(line);
        }
        var aw = s.features().awareness();
        Component off = m.get("command.awareness.disabled");
        Component on = m.get("command.awareness.enabled");
        for (Component line : m.list("command.status-awareness",
                Messages.comp("context", aw.context().enabled() ? on : off),
                Messages.text("mood", ctx.bot.mood().map(l -> l.id() + " (" + ctx.bot.moodScore() + ")").orElse(plain(off))),
                Messages.text("relationships", aw.relationships().enabled()
                        ? ctx.bot.relationships().size() + " known" + (ctx.bot.relationships().persistent() ? "" : ", memory only")
                        : plain(off)),
                Messages.comp("boredom", aw.boredom().enabled() ? on : off),
                Messages.comp("supervision", aw.supervision().enabled() ? on : off))) {
            sender.sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Pattern for commands that need the started plugin and a player. */
    private int player(CommandContext<CommandSourceStack> c, BiConsumer<Context, Player> action) {
        CommandSender sender = c.getSource().getSender();
        Context ctx = plugin.context();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ctx.messages().get("error.players-only"));
            return 0;
        }
        action.accept(ctx, p);
        return Command.SINGLE_SUCCESS;
    }

    private int withContext(CommandContext<CommandSourceStack> c, Consumer<Context> action) {
        Context ctx = plugin.context();
        if (ctx == null) {
            c.getSource().getSender().sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        action.accept(ctx);
        return Command.SINGLE_SUCCESS;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        String prefix = b.getRemainingLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) b.suggest(p.getName());
        }
        return b.buildFuture();
    }

    // ---- long-term memory (privacy commands; they work even when the player has muted the bot) ----

    /** The memory, or null after telling the sender why there is none. */
    private LongTermMemory memoryOrExplain(Context ctx, CommandSender sender) {
        LongTermMemory ltm = ctx.bot.longMemory();
        if (ltm == null) {
            sender.sendMessage(ctx.messages().get(ctx.settings().features().longMemory().enabled()
                    ? "memory.unavailable" : "memory.disabled"));
        }
        return ltm;
    }

    /** Sends the lines (built when the message is delivered) on the thread that owns the sender. */
    private void deliver(Context ctx, CommandSender sender, Supplier<List<Component>> lines) {
        Runnable send = () -> lines.get().forEach(sender::sendMessage);
        if (sender instanceof Player p) ctx.scheduler.runForEntity(p, send, null);
        else ctx.scheduler.runGlobal(send);
    }

    private void forgetOwn(Context ctx, Player p) {
        boolean relationship = ctx.bot.forgetRelationship(p.getUniqueId());
        if (relationship && ctx.bot.longMemory() == null) {
            p.sendMessage(ctx.messages().get("memory.forgot-relationship"));
            return;
        }
        LongTermMemory ltm = memoryOrExplain(ctx, p);
        if (ltm == null) return;
        ltm.forget(p.getUniqueId()).whenComplete((n, e) -> deliver(ctx, p, () -> List.of(e != null
                ? failed(ctx, e) : ctx.messages().get("memory.forgot-own", Messages.text("count", Integer.toString(n))))));
    }

    private void forgetPlayer(Context ctx, CommandSender sender, String name) {
        int known = ctx.bot.forgetRelationshipsByName(name);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && ctx.bot.forgetRelationship(online.getUniqueId())) known = Math.max(known, 1);
        if (ctx.bot.longMemory() == null && ctx.bot.relationshipsEnabled()) {
            sender.sendMessage(ctx.messages().get(known > 0 ? "memory.forgot-relationship-player" : "memory.player-unknown",
                    Messages.text("player", name)));
            return;
        }
        LongTermMemory ltm = memoryOrExplain(ctx, sender);
        if (ltm == null) return;
        resolve(ctx, ltm, sender, name, ids -> ltm.forgetMany(ids).whenComplete((n, e) -> deliver(ctx, sender,
                () -> List.of(e != null ? failed(ctx, e) : ctx.messages().get("memory.forgot-player",
                        Messages.text("count", Integer.toString(n)), Messages.text("player", name))))));
    }

    private void forgetAll(Context ctx, CommandSender sender) {
        int known = ctx.bot.forgetAllRelationships();
        if (ctx.bot.longMemory() == null && ctx.bot.relationshipsEnabled()) {
            sender.sendMessage(ctx.messages().get("memory.forgot-relationship-all", Messages.text("count", Integer.toString(known))));
            return;
        }
        LongTermMemory ltm = memoryOrExplain(ctx, sender);
        if (ltm == null) return;
        ltm.forgetAll().whenComplete((n, e) -> deliver(ctx, sender, () -> List.of(e != null
                ? failed(ctx, e) : ctx.messages().get("memory.forgot-all", Messages.text("count", Integer.toString(n))))));
    }

    private void memoryOwn(Context ctx, Player p) {
        LongTermMemory ltm = memoryOrExplain(ctx, p);
        if (ltm == null) return;
        showNotes(ctx, p, p.getName(), ltm, List.of(p.getUniqueId()));
    }

    private void memoryOf(Context ctx, CommandSender sender, String name) {
        LongTermMemory ltm = memoryOrExplain(ctx, sender);
        if (ltm == null) return;
        resolve(ctx, ltm, sender, name, ids -> showNotes(ctx, sender, name, ltm, ids));
    }

    /** An online player is found directly, anybody else through the name stored with the notes (no Mojang lookup). */
    private void resolve(Context ctx, LongTermMemory ltm, CommandSender sender, String name, Consumer<List<UUID>> action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            action.accept(List.of(online.getUniqueId()));
            return;
        }
        ltm.findByName(name).whenComplete((ids, e) -> {
            if (e != null) {
                deliver(ctx, sender, () -> List.of(failed(ctx, e)));
            } else if (ids.isEmpty()) {
                deliver(ctx, sender, () -> List.of(ctx.messages().get("memory.player-unknown", Messages.text("player", name))));
            } else {
                action.accept(ids);
            }
        });
    }

    private void showNotes(Context ctx, CommandSender sender, String name, LongTermMemory ltm, List<UUID> ids) {
        CompletableFuture<List<PlayerNotesStore.Note>> all = CompletableFuture.completedFuture(List.of());
        for (UUID id : ids) {
            all = all.thenCombine(ltm.notesOf(id), (a, b) -> {
                List<PlayerNotesStore.Note> merged = new ArrayList<>(a);
                merged.addAll(b);
                return merged;
            });
        }
        all.whenComplete((notes, e) -> deliver(ctx, sender, () -> {
            Messages m = ctx.messages();
            if (e != null) return List.of(failed(ctx, e));
            if (notes.isEmpty()) return List.of(m.get("memory.list-empty", Messages.text("player", name)));
            List<Component> out = new ArrayList<>();
            out.add(m.get("memory.list-header", Messages.text("player", name), Messages.text("count", Integer.toString(notes.size()))));
            long now = System.currentTimeMillis();
            for (PlayerNotesStore.Note n : notes.stream().limit(MAX_LISTED_NOTES).toList()) {
                out.add(m.get("memory.list-line", Messages.text("note", n.text()),
                        Messages.text("age", Math.max(0, (now - n.createdAt()) / 86_400_000L) + " d")));
            }
            if (notes.size() > MAX_LISTED_NOTES) {
                out.add(m.get("memory.list-more", Messages.text("count", Integer.toString(notes.size() - MAX_LISTED_NOTES))));
            }
            return out;
        }));
    }

    private Component failed(Context ctx, Throwable e) {
        ctx.log.warning("Long-term memory command failed: " + e);
        return ctx.messages().get("memory.failed");
    }

    // ---- nickname (relationships) ----

    private void nicknameInfo(Context ctx, Player p) {
        Messages m = ctx.messages();
        if (!ctx.bot.relationshipsEnabled() || !ctx.settings().features().awareness().relationships().nicknames()) {
            p.sendMessage(m.get("command.nickname.disabled"));
            return;
        }
        var view = ctx.bot.relationshipOf(p.getUniqueId());
        p.sendMessage(m.get("command.nickname.info",
                Messages.comp("level", m.get("command.nickname.level." + view.level().name().toLowerCase(Locale.ROOT))),
                Messages.text("days", Long.toString(view.knownDays())),
                Messages.text("nickname", view.nickname() == null ? "-" : view.nickname())));
    }

    private void nickname(Context ctx, Player p, String raw) {
        ChatBot.NicknameResult r = ctx.bot.setNickname(p.getUniqueId(), p.getName(), raw);
        String key = "command.nickname." + r.name().toLowerCase(Locale.ROOT);
        String nick = ctx.bot.relationshipOf(p.getUniqueId()).nickname();
        p.sendMessage(ctx.messages().get(key, Messages.text("nickname", nick == null ? "-" : nick)));
    }

    private void mute(Context ctx, Player p, boolean mute) {
        ChatBot.MuteResult r = mute ? ctx.bot.mute(p.getUniqueId(), p.getName()) : ctx.bot.unmute(p.getUniqueId());
        String key = r == ChatBot.MuteResult.DISABLED ? "command.mute.disabled"
                : "command." + (mute ? "mute" : "unmute") + (r == ChatBot.MuteResult.DONE ? ".done" : ".already");
        p.sendMessage(ctx.messages().get(key));
    }

    private int sleep(CommandContext<CommandSourceStack> c, int minutes) {
        Context ctx = plugin.context();
        CommandSender sender = c.getSource().getSender();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        int m = minutes > 0 ? minutes : ctx.settings().sleepMinutes();
        ctx.bot.sleep(Duration.ofMinutes(m));
        sender.sendMessage(ctx.messages().get("command.sleep.done", Messages.text("minutes", Integer.toString(m))));
        return Command.SINGLE_SUCCESS;
    }

    private int wake(CommandContext<CommandSourceStack> c) {
        Context ctx = plugin.context();
        CommandSender sender = c.getSource().getSender();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        sender.sendMessage(ctx.messages().get(ctx.bot.wake() ? "command.wake.done" : "command.wake.already"));
        return Command.SINGLE_SUCCESS;
    }

    private int cost(CommandContext<CommandSourceStack> c) {
        Context ctx = plugin.context();
        CommandSender sender = c.getSource().getSender();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        Messages m = ctx.messages();
        if (!ctx.bot.costEnabled()) {
            sender.sendMessage(m.get("command.cost.disabled"));
            return 0;
        }
        CostTracker.Totals t = ctx.bot.costTotals();
        sender.sendMessage(m.get("command.cost.header"));
        for (Component line : m.list("command.cost.lines",
                Messages.text("today-in", Long.toString(t.todayInput())), Messages.text("today-out", Long.toString(t.todayOutput())),
                Messages.text("today-cost", money(t.todayCost())),
                Messages.text("total-in", Long.toString(t.totalInput())), Messages.text("total-out", Long.toString(t.totalOutput())),
                Messages.text("total-cost", money(t.totalCost())),
                Messages.text("warn", t.warnDailyCost() > 0 ? money(t.warnDailyCost()) : "-"))) {
            sender.sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private void limits(Context ctx, Player p) {
        RateLimiter.Remaining r = ctx.bot.remaining(p.getUniqueId());
        Messages m = ctx.messages();
        String unlimited = plain(m.get("command.limit.unlimited"));
        p.sendMessage(m.get("command.limit.remaining",
                Messages.text("calls", r.playerCalls() < 0 ? unlimited : Long.toString(r.playerCalls())),
                Messages.text("tokens", r.playerTokens() < 0 ? unlimited : Long.toString(r.playerTokens())),
                Messages.text("cooldown", Long.toString(Math.max(0, r.cooldown().toSeconds())))));
    }

    private int simulate(CommandContext<CommandSourceStack> c, boolean callProvider) {
        Context ctx = plugin.context();
        CommandSender sender = c.getSource().getSender();
        if (ctx == null) {
            sender.sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        ctx.bot.simulate(callProvider, result -> ctx.scheduler.runGlobal(() -> showSimulation(ctx, sender, result)));
        return Command.SINGLE_SUCCESS;
    }

    private void showSimulation(Context ctx, CommandSender sender, SimulationResult r) {
        Messages m = ctx.messages();
        String none = plain(m.get("command.simulate.none"));
        List<Component> blockers = new ArrayList<>();
        for (SimulationResult.Blocker b : r.blockers()) blockers.add(m.get("command.simulate.blocker." + b.name()));
        Component blockerText = blockers.isEmpty() ? Component.text(none)
                : Component.join(JoinConfiguration.commas(true), blockers);
        sender.sendMessage(m.get("command.simulate.header"));
        for (Component line : m.list("command.simulate.lines",
                Messages.text("persona", r.persona() == null ? none : r.persona()),
                Messages.text("queued", Integer.toString(r.queuedLines())),
                Messages.text("history", Integer.toString(r.historyTurns())),
                Messages.text("tokens", Integer.toString(r.estimatedInputTokens())),
                Messages.comp("send", m.get(r.wouldSend() ? "command.simulate.answer-yes" : "command.simulate.answer-no")),
                Messages.comp("blockers", blockerText),
                Messages.text("raw", r.rawReply() == null ? none : r.rawReply()),
                Messages.text("filtered", r.filteredReply() == null ? none : r.filteredReply()))) {
            sender.sendMessage(line);
        }
        sender.sendMessage(m.get("command.simulate.request", Messages.text("request", r.request().replace('\n', ' '))));
    }

    private int reload(CommandContext<CommandSourceStack> c) {
        plugin.reloadSettings();
        c.getSource().getSender().sendMessage(plugin.messages().get("command.reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
