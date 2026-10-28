package me.skerik.aifriend.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.skerik.aifriend.AIFriend;
import me.skerik.aifriend.Context;
import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.config.Messages;
import me.skerik.aifriend.config.Settings;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** /aifriend (info), /aifriend status, /aifriend reload (Brigadier, works from the console too). Aliases come from config.yml. */
public final class MainCommand {

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
                Messages.text("model", s.model()),
                Messages.text("language", s.language()),
                Messages.text("key", s.maskedKey()),
                Messages.text("interval", Integer.toString(s.intervalSeconds())),
                Messages.text("queued", Integer.toString(st.queued())),
                Messages.text("max-queued", Integer.toString(s.maxQueuedMessages())),
                Messages.text("failures", Integer.toString(st.failures())))) {
            sender.sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> c) {
        plugin.reloadSettings();
        c.getSource().getSender().sendMessage(plugin.messages().get("command.reloaded"));
        return Command.SINGLE_SUCCESS;
    }
}
