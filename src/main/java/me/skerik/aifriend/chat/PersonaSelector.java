package me.skerik.aifriend.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/** Picks the persona for one request. Immutable and thread-safe; randomness and world/time come in as plain values. */
public final class PersonaSelector {

    /** Config section {@code personas}. Default: off, no personas (the bot behaves as before). */
    public record Config(boolean enabled, List<Persona> personas) {

        public Config {
            personas = List.copyOf(personas);
        }

        public static Config defaults() {
            return new Config(false, List.of());
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            if (s == null) return defaults();
            List<Persona> list = new ArrayList<>();
            for (Map<?, ?> raw : s.getMapList("list")) {
                Persona p = parse(raw, log);
                if (p != null) list.add(p);
            }
            return new Config(s.getBoolean("enabled", false), list);
        }

        private static @Nullable Persona parse(Map<?, ?> m, Logger log) {
            String name = str(m.get("name"));
            String prompt = str(m.get("prompt"));
            if (name.isEmpty() || prompt.isEmpty()) {
                log.warning("personas.list: an entry needs a name and a prompt; skipped");
                return null;
            }
            Set<String> worlds = new HashSet<>();
            if (m.get("worlds") instanceof List<?> l) {
                for (Object o : l) {
                    String w = str(o).toLowerCase(Locale.ROOT);
                    if (!w.isEmpty()) worlds.add(w);
                }
            }
            Persona.TimeOfDay time = Persona.TimeOfDay.ANY;
            String t = str(m.get("time")).toUpperCase(Locale.ROOT);
            if (!t.isEmpty()) {
                try {
                    time = Persona.TimeOfDay.valueOf(t);
                } catch (IllegalArgumentException e) {
                    log.warning("personas.list." + name + ".time must be any, day or night; using any");
                }
            }
            int weight = 1;
            if (m.get("weight") instanceof Number n) {
                weight = n.intValue();
                if (weight < 0 || weight > 1000) {
                    log.warning("personas.list." + name + ".weight must be between 0 and 1000; using 1");
                    weight = 1;
                }
            }
            return new Persona(name, prompt, worlds, time, weight);
        }

        private static String str(@Nullable Object o) {
            return o == null ? "" : o.toString().strip();
        }
    }

    private final Config config;

    public PersonaSelector(Config config) {
        this.config = config;
    }

    /**
     * The persona for this request, or empty (feature off, no list, nothing matches: then the default prompt is used).
     * Among matching personas the choice is weighted random; the same {@code random} state gives the same result.
     */
    public Optional<Persona> select(String world, long worldTimeTicks, RandomGenerator random) {
        if (!config.enabled()) return Optional.empty();
        List<Persona> candidates = config.personas().stream().filter(p -> p.matches(world, worldTimeTicks)).toList();
        if (candidates.isEmpty()) return Optional.empty();
        int total = candidates.stream().mapToInt(Persona::weight).sum();
        int roll = random.nextInt(total);
        for (Persona p : candidates) {
            roll -= p.weight();
            if (roll < 0) return Optional.of(p);
        }
        return Optional.of(candidates.getLast());
    }
}
