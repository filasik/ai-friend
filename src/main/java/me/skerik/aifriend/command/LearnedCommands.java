package me.skerik.aifriend.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import me.skerik.aifriend.AIFriend;
import me.skerik.aifriend.Context;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.gui.LearnedGui;
import me.skerik.aifriend.knowledge.LearnedFacts;
import me.skerik.aifriend.knowledge.LearnedFacts.Action;
import me.skerik.aifriend.knowledge.LearnedFacts.Fact;
import me.skerik.aifriend.knowledge.LearnedFacts.Status;
import me.skerik.aifriend.knowledge.ServerKnowledge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /aifriend learned}: review of the server facts the bot picked up from the chat. Without arguments a player gets
 * the GUI, the console the pending list. {@code list [status]}, {@code approve|disable|reject|delete <id>} and
 * {@code edit <id> <text>} work everywhere (the list lines carry clickable buttons). Permission {@code aifriend.teach}.
 */
public final class LearnedCommands {

    static final int MAX_LISTED = 15;

    private final AIFriend plugin;

    public LearnedCommands(AIFriend plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> list = Commands.literal("list")
                .executes(c -> run(c, ctx -> list(ctx, c.getSource().getSender(), Status.PENDING)));
        for (Status s : Status.values()) {
            list.then(Commands.literal(statusName(s)).executes(c -> run(c, ctx -> list(ctx, c.getSource().getSender(), s))));
        }
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("learned")
                .requires(src -> src.getSender().hasPermission("aifriend.teach"))
                .executes(c -> run(c, ctx -> {
                    if (c.getSource().getSender() instanceof Player p) open(ctx, p, Status.PENDING);
                    else list(ctx, c.getSource().getSender(), Status.PENDING);
                }))
                .then(list)
                .then(Commands.literal("edit")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> run(c, ctx -> edit(ctx, c.getSource().getSender(),
                                                IntegerArgumentType.getInteger(c, "id"), StringArgumentType.getString(c, "text")))))));
        for (Action a : Action.values()) {
            root.then(Commands.literal(a.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                            .executes(c -> run(c, ctx -> {
                                CommandSender sender = c.getSource().getSender();
                                apply(ctx, sender, IntegerArgumentType.getInteger(c, "id"), a,
                                        f -> deliver(ctx, sender, done(ctx, a, f)));
                            }))));
        }
        return root;
    }

    private int run(CommandContext<CommandSourceStack> c, Consumer<Context> action) {
        Context ctx = plugin.context();
        if (ctx == null) {
            c.getSource().getSender().sendMessage(plugin.messages().get("error.not-ready"));
            return 0;
        }
        if (learnerOrExplain(ctx, c.getSource().getSender()) == null) return 0;
        action.accept(ctx);
        return Command.SINGLE_SUCCESS;
    }

    /** The knowledge with learning switched on, or null after telling the sender why not. */
    static @Nullable ServerKnowledge learnerOrExplain(Context ctx, CommandSender sender) {
        ServerKnowledge k = ctx.bot.knowledge();
        if (k == null || !ctx.settings().features().knowledge().learningOn()) {
            sender.sendMessage(ctx.messages().get("learned.disabled"));
            return null;
        }
        return k;
    }

    public static String statusName(Status s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    /** Opens the review GUI on the player's own thread. */
    public static void open(Context ctx, Player p, Status status) {
        ServerKnowledge k = learnerOrExplain(ctx, p);
        if (k == null) return;
        ctx.scheduler.runForEntity(p, () -> new LearnedGui(ctx, k.learned(), status, 0).open(p), null);
    }

    /**
     * Applies an admin decision on an async task (it writes the file), refreshes the search index and hands the result
     * (null: no such fact) to {@code then}, still on that task.
     */
    public static void apply(Context ctx, CommandSender admin, int id, Action action, Consumer<@Nullable Fact> then) {
        ServerKnowledge k = learnerOrExplain(ctx, admin);
        if (k == null) return;
        String by = admin instanceof Player p ? p.getName() : "console";
        ctx.scheduler.runAsync(() -> {
            Fact f = k.learned().decide(id, action, by);
            if (f != null) {
                k.refreshLearned();
                ctx.log.info("Knowledge learning: " + by + " " + action.name().toLowerCase(Locale.ROOT) + "d #" + id + ": " + f.text());
            }
            then.accept(f);
        });
    }

    private static List<Component> done(Context ctx, Action a, @Nullable Fact f) {
        Messages m = ctx.messages();
        if (f == null) return List.of(m.get("learned.missing"));
        return List.of(m.get("learned.done." + a.name().toLowerCase(Locale.ROOT),
                Messages.text("id", Integer.toString(f.id())), Messages.text("text", f.text())));
    }

    private void edit(Context ctx, CommandSender sender, int id, String text) {
        ServerKnowledge k = learnerOrExplain(ctx, sender);
        if (k == null) return;
        String by = sender instanceof Player p ? p.getName() : "console";
        ctx.scheduler.runAsync(() -> {
            boolean exists = k.learned().get(id) != null;
            Fact f = k.learned().edit(id, text, by);
            if (f != null) k.refreshLearned();
            Messages m = ctx.messages();
            deliver(ctx, sender, List.of(f != null
                    ? m.get("learned.done.edit", Messages.text("id", Integer.toString(id)), Messages.text("text", f.text()))
                    : exists ? m.get("learned.edit-invalid", Messages.text("min", Integer.toString(LearnedFacts.MIN_FACT_LENGTH)),
                    Messages.text("max", Integer.toString(LearnedFacts.MAX_FACT_LENGTH)))
                    : m.get("learned.missing")));
        });
    }

    private void list(Context ctx, CommandSender sender, Status status) {
        ServerKnowledge k = learnerOrExplain(ctx, sender);
        if (k == null) return;
        Messages m = ctx.messages();
        List<Fact> all = k.learned().list(status);
        List<Component> out = new ArrayList<>();
        Component statusName = m.get("learned.status." + statusName(status));
        if (all.isEmpty()) {
            out.add(m.get("learned.list-empty", Messages.comp("status", statusName)));
        } else {
            out.add(m.get("learned.list-header", Messages.comp("status", statusName), Messages.text("count", Integer.toString(all.size()))));
            for (Fact f : all.stream().limit(MAX_LISTED).toList()) {
                out.add(m.get("learned.list-line", Messages.text("id", Integer.toString(f.id())), Messages.text("text", f.text()),
                        Messages.text("mentions", Integer.toString(f.mentions())),
                        Messages.text("players", f.players().isEmpty() ? "-" : String.join(", ", f.players())),
                        Messages.comp("actions", buttons(m, f))));
            }
            if (all.size() > MAX_LISTED) out.add(m.get("learned.list-more", Messages.text("count", Integer.toString(all.size() - MAX_LISTED))));
        }
        out.add(m.get("learned.list-tabs"));
        deliver(ctx, sender, out);
    }

    /** Clickable [approve] [reject] ... for one fact; the action it already has is left out. */
    private static Component buttons(Messages m, Fact f) {
        List<Component> out = new ArrayList<>();
        for (Action a : Action.values()) {
            if (a == Action.APPROVE && f.status() == Status.APPROVED || a == Action.DISABLE && f.status() == Status.DISABLED
                    || a == Action.REJECT && f.status() == Status.REJECTED) {
                continue;
            }
            String key = a.name().toLowerCase(Locale.ROOT);
            out.add(m.get("learned.button." + key)
                    .clickEvent(ClickEvent.runCommand("/aifriend learned " + key + " " + f.id()))
                    .hoverEvent(HoverEvent.showText(m.get("learned.button-hover." + key))));
        }
        return Component.join(JoinConfiguration.separator(Component.space()), out);
    }

    /** Sends the lines on the thread that owns the sender. */
    static void deliver(Context ctx, CommandSender sender, List<Component> lines) {
        Runnable send = () -> lines.forEach(sender::sendMessage);
        if (sender instanceof Player p) ctx.scheduler.runForEntity(p, send, null);
        else ctx.scheduler.runGlobal(send);
    }
}
