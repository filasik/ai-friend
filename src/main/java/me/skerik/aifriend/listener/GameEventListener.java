package me.skerik.aifriend.listener;

import me.skerik.aifriend.ai.ChatBot;
import me.skerik.aifriend.chat.EventPrompts.Boss;
import me.skerik.aifriend.chat.EventPrompts.GameEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Turns game events into short lines for the bot. Which ones count, and how often, is decided by {@code EventPrompts}. */
public final class GameEventListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ChatBot bot;

    public GameEventListener(ChatBot bot) {
        this.bot = bot;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        // the vanilla death message already names the player and the cause ("Steve fell from a high place")
        bot.onEvent(GameEvent.death(event.getPlayer().getName(), message == null ? "died" : PLAIN.serialize(message)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // recipes and other hidden advancements have no display
        var display = event.getAdvancement().getDisplay();
        if (display == null || !display.doesAnnounceToChat()) return;
        bot.onEvent(GameEvent.advancement(event.getPlayer().getName(), PLAIN.serialize(display.title())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPlayedBefore()) bot.onEvent(GameEvent.firstJoin(event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(EntityDeathEvent event) {
        Boss boss = event.getEntity() instanceof EnderDragon ? Boss.ENDER_DRAGON
                : event.getEntity() instanceof Wither ? Boss.WITHER : null;
        if (boss == null) return;
        Player killer = event.getEntity().getKiller();
        bot.onEvent(GameEvent.bossKilled(boss, killer == null ? "someone" : killer.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(ExplosionPrimeEvent event) {
        bot.onEvent(GameEvent.explosion(event.getEntity().getType().name().toLowerCase(java.util.Locale.ROOT), event.getRadius()));
    }
}
