package me.skerik.aifriend.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptOutStoreTest {

    private static final Logger LOG = Logger.getAnonymousLogger();

    @TempDir
    Path dir;

    @Test
    void missingFileMeansNobodyMuted() {
        var s = new OptOutStore(dir.resolve("m.yml"), LOG);
        assertFalse(s.isMuted(UUID.randomUUID()));
        assertEquals(0, s.mutedCount());
    }

    @Test
    void muteUnmuteAndPersist() {
        Path f = dir.resolve("sub/m.yml");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var s = new OptOutStore(f, LOG);
        assertTrue(s.setMuted(a, true));
        assertFalse(s.setMuted(a, true));
        assertTrue(s.setMuted(b, true));
        assertTrue(s.flush());
        assertTrue(Files.exists(f));
        assertFalse(Files.exists(dir.resolve("sub/m.yml.tmp")));

        var again = new OptOutStore(f, LOG);
        assertTrue(again.isMuted(a));
        assertTrue(again.isMuted(b));
        assertTrue(again.setMuted(a, false));
        assertFalse(again.setMuted(a, false));
        again.flush();
        var third = new OptOutStore(f, LOG);
        assertFalse(third.isMuted(a));
        assertTrue(third.isMuted(b));
    }

    @Test
    void flushWithoutChangeWritesNothing() {
        Path f = dir.resolve("m.yml");
        var s = new OptOutStore(f, LOG);
        s.isMuted(UUID.randomUUID());
        assertTrue(s.flush());
        assertFalse(Files.exists(f));
    }

    @Test
    void acceptsFalseEntriesAndSkipsGarbageLines() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Path f = dir.resolve("m.yml");
        Files.write(f, List.of("# comment", a + ": true", "\"" + b + "\": false", "nonsense", "not-a-uuid: true",
                "{broken", b + ": maybe"));
        var s = new OptOutStore(f, LOG);
        assertTrue(s.isMuted(a));
        assertFalse(s.isMuted(b));
        assertEquals(1, s.mutedCount());
    }

    @Test
    void corruptBinaryFileIsQuarantined() throws Exception {
        Path f = dir.resolve("m.yml");
        Files.write(f, new byte[]{(byte) 0xff, (byte) 0xfe, (byte) 0xc3, 0x28, 0, 1, 2});
        var s = new OptOutStore(f, LOG);
        assertFalse(s.isMuted(UUID.randomUUID()));
        assertTrue(Files.exists(dir.resolve("m.yml.corrupt")));
        assertFalse(Files.exists(f));
        UUID a = UUID.randomUUID();
        s.setMuted(a, true);
        assertTrue(s.flush());
        assertTrue(new OptOutStore(f, LOG).isMuted(a));
    }

    @Test
    void emptyFileIsFine() throws Exception {
        Path f = dir.resolve("m.yml");
        Files.writeString(f, "");
        assertEquals(0, new OptOutStore(f, LOG).mutedCount());
    }

    @Test
    void failedWriteKeepsStateAndReportsFalse() throws Exception {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        var s = new OptOutStore(blocker.resolve("m.yml"), LOG); // parent is a file
        UUID a = UUID.randomUUID();
        s.setMuted(a, true);
        assertFalse(s.flush());
        assertTrue(s.isMuted(a));
    }

    @Test
    void concurrentMuting() throws Exception {
        Path f = dir.resolve("m.yml");
        var s = new OptOutStore(f, LOG);
        List<UUID> ids = java.util.stream.IntStream.range(0, 400).mapToObj(i -> UUID.randomUUID()).toList();
        ExecutorService ex = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            int part = t;
            ex.submit(() -> {
                for (int i = part; i < ids.size(); i += 8) {
                    s.setMuted(ids.get(i), true);
                    s.isMuted(ids.get(i));
                    if (i % 50 == 0) s.flush();
                }
            });
        }
        ex.shutdown();
        assertTrue(ex.awaitTermination(20, TimeUnit.SECONDS));
        assertEquals(400, s.mutedCount());
        assertTrue(s.flush());
        var reloaded = new OptOutStore(f, LOG);
        assertEquals(400, reloaded.mutedCount());
        ids.forEach(id -> assertTrue(reloaded.isMuted(id)));
    }

    @Test
    void configRejectsPathTricks() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("enabled", true);
        y.set("file", "../evil.yml");
        var c = OptOutStore.Config.load(y, LOG);
        assertEquals("muted-players.yml", c.file());
        assertTrue(c.enabled());
        assertFalse(OptOutStore.Config.load(null, LOG).enabled());
    }
}
