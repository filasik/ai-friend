package me.skerik.aifriend.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import me.skerik.aifriend.Scheduler;
import me.skerik.aifriend.chat.ServerContext;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a {@link ServerContext.Snapshot} up to date for the chat bot. The bot builds its prompt on an async thread, where
 * worlds and entities must not be touched, so this class reads them on the threads that own them (one entity task per
 * player, Folia-safe through {@link Scheduler}) every {@code refresh-seconds} and the bot only
 * reads the finished copy. Nothing is collected while the feature is off or nobody is online. No coordinates are kept.
 */
public final class ContextCollector {

    private record Timed(ServerContext.PlayerView view, long at) {
    }

    private static final int MAX_KINDS = 3;

    private final Scheduler scheduler;
    private final Supplier<ServerContext.Config> config;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, Timed> views = new ConcurrentHashMap<>();
    private volatile long lastRefresh;

    public ContextCollector(Scheduler scheduler, Supplier<ServerContext.Config> config, LongSupplier clock) {
        this.scheduler = scheduler;
        this.config = config;
        this.clock = clock;
    }

    /** Called often (a few seconds apart, any thread); does the work only when a refresh is due. */
    public void tick() {
        ServerContext.Config c = config.get();
        if (!c.enabled()) {
            views.clear();
            return;
        }
        long now = clock.getAsLong();
        if (now - lastRefresh < c.refreshSeconds() * 1000L) return;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            views.clear();
            return;
        }
        lastRefresh = now;
        Set<UUID> ids = new HashSet<>();
        for (Player p : online) ids.add(p.getUniqueId());
        views.keySet().retainAll(ids);

        int n = 0;
        for (Player p : online) {
            if (++n > c.maxPlayers()) break;
            scheduler.runForEntity(p, () -> readPlayer(c, p), null);
        }
    }

    /** What the bot gets: the world picture plus the views refreshed lately (stale ones belong to somebody gone quiet). */
    public ServerContext.Snapshot snapshot() {
        ServerContext.Config c = config.get();
        if (!c.enabled()) return ServerContext.Snapshot.empty();
        long fresh = clock.getAsLong() - Math.max(c.refreshSeconds() * 5000L, 60_000L);
        List<ServerContext.PlayerView> list = new ArrayList<>();
        for (Timed t : views.values()) if (t.at() >= fresh) list.add(t.view());
        return new ServerContext.Snapshot(common(list, ServerContext.PlayerView::time), common(list, ServerContext.PlayerView::weather),
                Bukkit.getOnlinePlayers().size(), list);
    }

    /** The value every player has, or null when there are none or they differ (other worlds, Residence, ...). */
    private static @Nullable String common(List<ServerContext.PlayerView> views, java.util.function.Function<ServerContext.PlayerView, @Nullable String> field) {
        String value = null;
        for (ServerContext.PlayerView v : views) {
            String f = field.apply(v);
            if (f == null || (value != null && !value.equals(f))) return null;
            value = f;
        }
        return value;
    }

    private void readPlayer(ServerContext.Config c, Player p) {
        World w = p.getWorld();
        String world = c.world() ? w.getName() : null;
        // only ordinary worlds have a day and weather; the player's own time/weather (Residence, /ptime, /pweather) wins
        boolean cycle = w.getEnvironment() == World.Environment.NORMAL;
        String time = c.time() && cycle ? ServerContext.timeOfDay(p.getPlayerTime()) : null;
        String weather = c.weather() && cycle ? weatherOf(p, w) : null;
        String biome = null;
        if (c.biome()) {
            biome = ServerContext.readable(p.getLocation().getBlock().getBiome().key().value());
        }
        String nearby = c.nearbyMobs() ? nearbyHostiles(p, c.radius()) : null;
        views.put(p.getUniqueId(), new Timed(new ServerContext.PlayerView(p.getName(), world, time, weather, biome, nearby), clock.getAsLong()));
    }

    private static String weatherOf(Player p, World w) {
        WeatherType own = p.getPlayerWeather(); // null while the player follows the world
        if (own != null) return own == WeatherType.DOWNFALL ? "raining" : "clear";
        return w.isThundering() ? "thunderstorm" : w.hasStorm() ? "raining" : "clear";
    }

    /** "2 zombie, 1 skeleton": the most common hostile mobs around the player, or null when there are none. */
    private static @Nullable String nearbyHostiles(Player p, int radius) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Monster) counts.merge(e.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '), 1, Integer::sum);
        }
        if (counts.isEmpty()) return null;
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_KINDS).map(e -> e.getValue() + " " + e.getKey())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
