package me.skerik.aifriend.chat;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * What the bot may know about the server beyond the chat: time of day, weather, who is online and, for the players who
 * just wrote, their world, biome and the hostile mobs around them (never coordinates). Plain values, no Bukkit types;
 * the {@code ContextCollector} fills it on the right threads and {@link Snapshot#forPlayers} picks what goes into a prompt.
 */
public final class ServerContext {

    private ServerContext() {
    }

    /** Config section {@code server-context}. Default: off. Every detail can be switched off on its own. */
    public record Config(boolean enabled, int refreshSeconds, int maxPlayers, boolean time, boolean weather,
                         boolean world, boolean biome, boolean nearbyMobs, int radius, int sharePercent) {

        public static final int DEFAULT_SHARE_PERCENT = 10;

        /** Same with {@code sharePercent} 100: the picture is in every request. */
        public Config(boolean enabled, int refreshSeconds, int maxPlayers, boolean time, boolean weather,
                      boolean world, boolean biome, boolean nearbyMobs, int radius) {
            this(enabled, refreshSeconds, maxPlayers, time, weather, world, biome, nearbyMobs, radius, 100);
        }

        public static Config defaults() {
            return new Config(false, 30, 30, true, true, true, true, true, 16, DEFAULT_SHARE_PERCENT);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.bounded(s, "refresh-seconds", 5, 600, 30, log),
                    Cfg.bounded(s, "max-players", 1, 200, 30, log),
                    Cfg.bool(s, "time", true), Cfg.bool(s, "weather", true), Cfg.bool(s, "world", true),
                    Cfg.bool(s, "biome", true), Cfg.bool(s, "nearby-mobs", true),
                    Cfg.bounded(s, "radius", 4, 48, 16, log),
                    Cfg.bounded(s, "share-percent", 0, 100, DEFAULT_SHARE_PERCENT, log));
        }
    }

    /**
     * One player's surroundings, e.g. {@code biome="dark forest", nearby="2 zombie, 1 skeleton"}. Time and weather are
     * the ones <em>this player</em> sees (their world's, or what a plugin such as Residence set for them); they are null
     * in worlds without a day cycle (Nether, End) and when switched off. Any part may be null.
     */
    public record PlayerView(String name, @Nullable String world, @Nullable String time, @Nullable String weather,
                             @Nullable String biome, @Nullable String nearby) {
    }

    /**
     * The picture of the server. {@code time} and {@code weather} are the general values: set only when every collected
     * player sees the same (several worlds, Multiverse, per-player time all make them differ), otherwise null and the
     * players' own values tell.
     */
    public record Snapshot(@Nullable String time, @Nullable String weather, int online, List<PlayerView> players) {

        public Snapshot {
            players = List.copyOf(players);
        }

        public static Snapshot empty() {
            return new Snapshot(null, null, 0, List.of());
        }

        /** True when there is nothing worth telling the model. */
        public boolean isEmpty() {
            return time == null && weather == null && players.isEmpty();
        }

        /** The same picture, but only the views of the named players (case-insensitive), at most {@code max}. */
        public Snapshot forPlayers(List<String> names, int max) {
            List<PlayerView> picked = players.stream()
                    .filter(v -> names.stream().anyMatch(n -> n.equalsIgnoreCase(v.name())))
                    .limit(max).toList();
            return new Snapshot(time, weather, online, picked);
        }
    }

    private static final java.util.regex.Pattern ASKS_ABOUT_SURROUNDINGS = java.util.regex.Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(kde|where|weather|po[cč]as[ií]|biom|biome|mob|mobs|moby|zombie|zombii|creeper|skeleton|"
                    + "kostlivec|pavouk|spider|enderman|noc|night|rain|pr[sš][ií]|bou[rř]ka|storm|hostile|nep[rř][aá]tel\\p{L}*)"
                    + "(?![\\p{L}\\p{N}])");

    /**
     * Whether the surroundings may go into this request. Telling players where they are all the time is annoying, so
     * normally only {@code sharePercent} of the requests carry them; when the players' own words are about the surroundings
     * (where, weather, day or night, mobs) they always do.
     */
    public static boolean share(Config config, String playersText, java.util.random.RandomGenerator random) {
        if (config.sharePercent() >= 100) return true;
        if (ASKS_ABOUT_SURROUNDINGS.matcher(playersText).find()) return true;
        return config.sharePercent() > 0 && random.nextInt(100) < config.sharePercent();
    }

    /** morning, afternoon, evening, night or dawn, from the world time in ticks (0 = sunrise). */
    public static String timeOfDay(long ticks) {
        long t = Math.floorMod(ticks, 24_000L);
        if (t < 6_000) return "morning";
        if (t < 12_000) return "afternoon";
        if (t < 13_800) return "evening";
        if (t < 22_200) return "night";
        return "dawn";
    }

    /** "dark_forest" or "minecraft:dark_forest" to "dark forest". */
    public static String readable(String key) {
        String k = key;
        int colon = k.indexOf(':');
        if (colon >= 0) k = k.substring(colon + 1);
        return k.replace('_', ' ').toLowerCase(Locale.ROOT).strip();
    }
}
