package me.skerik.aifriend.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Connection pool plus a dedicated executor. The main thread only ever sees futures. */
public final class Database implements AutoCloseable {

    private final DbConfig config;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public Database(DbConfig config) {
        this.config = config;
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("AI-Friend-DB");
        hc.setMaximumPoolSize(config.poolSize());
        switch (config.dialect()) {
            case SQLITE -> {
                String params = "?foreign_keys=on&busy_timeout=10000&transaction_mode=IMMEDIATE";
                if (config.sqliteFile() == null) {
                    // A single pooled connection keeps the private in-memory database alive
                    hc.setJdbcUrl("jdbc:sqlite::memory:" + params);
                    hc.setMaximumPoolSize(1);
                } else {
                    hc.setJdbcUrl("jdbc:sqlite:" + config.sqliteFile().toAbsolutePath() + params
                            + "&journal_mode=WAL&synchronous=NORMAL");
                }
                hc.setDriverClassName("org.sqlite.JDBC");
            }
            case MARIADB -> {
                hc.setJdbcUrl("jdbc:mariadb://" + config.host() + ":" + config.port() + "/" + config.database()
                        + "?useUnicode=true&characterEncoding=utf8"
                        + (config.sslMode().isBlank() ? "" : "&sslMode=" + config.sslMode()));
                hc.setUsername(config.user());
                hc.setPassword(config.password());
                hc.setDriverClassName("org.mariadb.jdbc.Driver");
            }
        }
        this.dataSource = new HikariDataSource(hc);
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(hc.getMaximumPoolSize(), r -> {
            Thread t = new Thread(r, "AI-Friend-DB-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public DbConfig.Dialect dialect() {
        return config.dialect();
    }

    /** Runs on the DB executor; the future fails with the SQLException (wrapped) on error. */
    public <T> CompletableFuture<T> supply(SqlFunction<Connection, T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(work);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Blocking variant for startup (migrations) and tests. Never call from the main thread at runtime. */
    public <T> T call(SqlFunction<Connection, T> work) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return work.apply(c);
        }
    }

    /** Runs work in one transaction (commit on success, rollback on any failure). */
    public static <T> T transaction(Connection c, SqlFunction<Connection, T> work) throws SQLException {
        boolean previous = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.apply(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        } finally {
            c.setAutoCommit(previous);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }
}
