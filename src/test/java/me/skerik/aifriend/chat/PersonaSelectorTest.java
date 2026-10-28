package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PersonaSelectorTest {

    private static final Logger LOG = Logger.getAnonymousLogger();

    private static Persona p(String name, Set<String> worlds, Persona.TimeOfDay t, int w) {
        return new Persona(name, name + " prompt", worlds, t, w);
    }

    @Test
    void offByDefault() {
        var sel = new PersonaSelector(PersonaSelector.Config.defaults());
        assertTrue(sel.select("world", 0, new Random(1)).isEmpty());
        var disabled = new PersonaSelector(new PersonaSelector.Config(false, List.of(p("a", Set.of(), Persona.TimeOfDay.ANY, 1))));
        assertTrue(disabled.select("world", 0, new Random(1)).isEmpty());
    }

    @Test
    void emptyListGivesNone() {
        var sel = new PersonaSelector(new PersonaSelector.Config(true, List.of()));
        assertTrue(sel.select("world", 0, new Random(1)).isEmpty());
    }

    @Test
    void worldAndTimeConditions() {
        var day = p("day", Set.of("world"), Persona.TimeOfDay.DAY, 1);
        var night = p("night", Set.of("world"), Persona.TimeOfDay.NIGHT, 1);
        var nether = p("nether", Set.of("world_nether"), Persona.TimeOfDay.ANY, 1);
        var sel = new PersonaSelector(new PersonaSelector.Config(true, List.of(day, night, nether)));
        assertEquals("day", sel.select("World", 1000, new Random(1)).orElseThrow().name());
        assertEquals("night", sel.select("world", 14000, new Random(1)).orElseThrow().name());
        assertEquals("nether", sel.select("WORLD_NETHER", 6000, new Random(1)).orElseThrow().name());
        assertTrue(sel.select("the_end", 6000, new Random(1)).isEmpty());
    }

    @Test
    void nightBoundaries() {
        assertEquals(Persona.TimeOfDay.DAY, Persona.TimeOfDay.ofTicks(12999));
        assertEquals(Persona.TimeOfDay.NIGHT, Persona.TimeOfDay.ofTicks(13000));
        assertEquals(Persona.TimeOfDay.NIGHT, Persona.TimeOfDay.ofTicks(22999));
        assertEquals(Persona.TimeOfDay.DAY, Persona.TimeOfDay.ofTicks(23000));
        assertEquals(Persona.TimeOfDay.NIGHT, Persona.TimeOfDay.ofTicks(24000 * 5 + 15000));
        assertEquals(Persona.TimeOfDay.NIGHT, Persona.TimeOfDay.ofTicks(-5000));
    }

    @Test
    void weightsAreRespectedDeterministically() {
        var a = p("a", Set.of(), Persona.TimeOfDay.ANY, 1);
        var b = p("b", Set.of(), Persona.TimeOfDay.ANY, 3);
        var sel = new PersonaSelector(new PersonaSelector.Config(true, List.of(a, b)));
        // a random source that always returns a fixed roll
        assertEquals("a", sel.select("w", 0, fixed(0)).orElseThrow().name());
        assertEquals("b", sel.select("w", 0, fixed(1)).orElseThrow().name());
        assertEquals("b", sel.select("w", 0, fixed(3)).orElseThrow().name());
        // same seed, same choice
        assertEquals(sel.select("w", 0, new Random(42)), sel.select("w", 0, new Random(42)));
    }

    @Test
    void weightZeroIsNeverPicked() {
        var a = p("a", Set.of(), Persona.TimeOfDay.ANY, 0);
        var sel = new PersonaSelector(new PersonaSelector.Config(true, List.of(a)));
        assertEquals(Optional.empty(), sel.select("w", 0, new Random(1)));
    }

    @Test
    void parsesConfigWithFallbacks() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                list:
                  - name: Pirate
                    prompt: Talk like a pirate.
                    worlds: [World, nether]
                    time: night
                    weight: 3
                  - name: Broken
                  - name: BadValues
                    prompt: Hi
                    time: dusk
                    weight: -4
                  - name: Żebrák
                    prompt: Mluv česky.
                """);
        var cfg = PersonaSelector.Config.load(yaml, LOG);
        assertTrue(cfg.enabled());
        assertEquals(3, cfg.personas().size());
        Persona pirate = cfg.personas().get(0);
        assertEquals(Set.of("world", "nether"), pirate.worlds());
        assertEquals(Persona.TimeOfDay.NIGHT, pirate.time());
        assertEquals(3, pirate.weight());
        Persona bad = cfg.personas().get(1);
        assertEquals(Persona.TimeOfDay.ANY, bad.time());
        assertEquals(1, bad.weight());
        assertEquals("Żebrák", cfg.personas().get(2).name());
    }

    @Test
    void nullSectionIsDefaults() {
        assertFalse(PersonaSelector.Config.load(null, LOG).enabled());
    }

    private static java.util.random.RandomGenerator fixed(int roll) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return roll % bound;
            }
        };
    }
}
