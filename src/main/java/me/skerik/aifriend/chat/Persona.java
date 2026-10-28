package me.skerik.aifriend.chat;

import java.util.Locale;
import java.util.Set;

/**
 * One personality of the bot. {@code prompt} is admin-trusted text that goes into the system prompt.
 * Empty {@code worlds} = any world, {@code time} ANY = any time, {@code weight} 0 = never picked.
 */
public record Persona(String name, String prompt, Set<String> worlds, TimeOfDay time, int weight) {

    public enum TimeOfDay {
        ANY, DAY, NIGHT;

        /** Minecraft day time in ticks: 13000..22999 is night, the rest is day. Negative and huge values wrap. */
        public static TimeOfDay ofTicks(long ticks) {
            long t = ((ticks % 24000) + 24000) % 24000;
            return t >= 13000 && t < 23000 ? NIGHT : DAY;
        }
    }

    public Persona {
        worlds = Set.copyOf(worlds);
    }

    /** True when this persona may be used in {@code world} at {@code ticks}. {@code worlds} must be lower case. */
    public boolean matches(String world, long ticks) {
        if (weight <= 0) return false;
        if (!worlds.isEmpty() && !worlds.contains(world.toLowerCase(Locale.ROOT))) return false;
        return time == TimeOfDay.ANY || time == TimeOfDay.ofTicks(ticks);
    }
}
