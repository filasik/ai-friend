package me.skerik.aifriend.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Per-player mute list persisted as a tiny YAML file ({@code <uuid>: true}, one per line). Thread-safe.
 * The file is read lazily on first use. A damaged file is moved to {@code <name>.corrupt} and the store starts empty;
 * single unreadable lines are skipped. Only muted players are written (unmuting removes the line).
 * {@link #setMuted} changes memory only; call {@link #flush()} off the main thread (and on disable) to write.
 */
public final class OptOutStore {

    /** Config section {@code opt-out}. Default: off (players cannot mute). */
    public record Config(boolean enabled, String file) {

        public static Config defaults() {
            return new Config(false, "muted-players.yml");
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            String file = Cfg.string(s, "file", "muted-players.yml");
            if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..")) {
                log.warning("opt-out.file must be a plain file name; using muted-players.yml");
                file = "muted-players.yml";
            }
            return new Config(Cfg.bool(s, "enabled", false), file);
        }
    }

    private final Path file;
    private final Logger log;
    private final Object lock = new Object();
    private volatile @Nullable Set<UUID> muted;
    private boolean dirty;

    public OptOutStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public boolean isMuted(UUID player) {
        return set().contains(player);
    }

    /** Returns true when the state changed. Not written to disk until {@link #flush()}. */
    public boolean setMuted(UUID player, boolean mute) {
        Set<UUID> s = set();
        boolean changed = mute ? s.add(player) : s.remove(player);
        if (changed) {
            synchronized (lock) {
                dirty = true;
            }
        }
        return changed;
    }

    public int mutedCount() {
        return set().size();
    }

    /** Writes the file (temp file + atomic move) when something changed. Returns false when writing failed. */
    public boolean flush() {
        Set<UUID> s = set();
        synchronized (lock) {
            if (!dirty) return true;
            List<String> lines = new ArrayList<>();
            lines.add("# AI Friend: players who muted the bot (managed by the plugin)");
            new TreeSet<>(s).forEach(id -> lines.add(id + ": true"));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Path dir = file.toAbsolutePath().getParent();
                if (dir != null) Files.createDirectories(dir);
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                dirty = false;
                return true;
            } catch (IOException e) {
                log.warning("Could not save " + file.getFileName() + ": " + e.getMessage());
                return false;
            }
        }
    }

    private Set<UUID> set() {
        Set<UUID> s = muted;
        if (s != null) return s;
        synchronized (lock) {
            if (muted == null) muted = load();
            return muted;
        }
    }

    private Set<UUID> load() {
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        if (!Files.isRegularFile(file)) return out;
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            log.warning(file.getFileName() + " is unreadable (" + e.getMessage() + "); starting with an empty list");
            quarantine();
            return out;
        }
        int bad = 0;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.lastIndexOf(':');
            if (colon <= 0) {
                bad++;
                continue;
            }
            String key = line.substring(0, colon).strip().replace("\"", "").replace("'", "");
            String value = line.substring(colon + 1).strip();
            try {
                UUID id = UUID.fromString(key);
                if (value.equalsIgnoreCase("true")) out.add(id);
                else if (!value.equalsIgnoreCase("false")) bad++;
            } catch (IllegalArgumentException e) {
                bad++;
            }
        }
        if (bad > 0) log.warning(file.getFileName() + ": skipped " + bad + " unreadable line(s)");
        return out;
    }

    private void quarantine() {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warning("Could not move the damaged file aside: " + e.getMessage());
        }
    }
}
