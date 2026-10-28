package me.skerik.aifriend.storage;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * @param sqliteFile null means a private in-memory SQLite database (tests)
 */
public record DbConfig(
        Dialect dialect, @Nullable Path sqliteFile, String host, int port, String database,
        String user, String password, int poolSize, String sslMode) {

    public enum Dialect { SQLITE, MARIADB }

    public static DbConfig sqlite(Path file, int poolSize) {
        return new DbConfig(Dialect.SQLITE, file, "", 0, "", "", "", poolSize, "");
    }

    public static DbConfig memory() {
        return new DbConfig(Dialect.SQLITE, null, "", 0, "", "", "", 1, "");
    }
}
