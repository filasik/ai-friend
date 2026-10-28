package me.skerik.aifriend.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Token totals (today and since start) and an estimated cost from configurable prices per 1M tokens.
 * Thread-safe, in memory only (totals restart with the server). The day ends at local midnight of the injected zone.
 */
public final class CostTracker {

    /** Config section {@code cost}. Default: off. Prices in any currency; {@code warnDailyCost} 0 = no warning. */
    public record Config(boolean enabled, double inputPricePerMillion, double outputPricePerMillion,
                         double warnDailyCost) {

        public static Config defaults() {
            return new Config(false, 0.15, 0.60, 0);
        }

        public static Config load(@Nullable ConfigurationSection s, Logger log) {
            return new Config(Cfg.bool(s, "enabled", false),
                    Cfg.boundedDouble(s, "input-price-per-million", 0, 100000, 0.15, log),
                    Cfg.boundedDouble(s, "output-price-per-million", 0, 100000, 0.60, log),
                    Cfg.boundedDouble(s, "warn-daily-cost", 0, 1_000_000, 0, log));
        }
    }

    public record Totals(long todayInput, long todayOutput, long totalInput, long totalOutput,
                         double todayCost, double totalCost, double warnDailyCost) {
    }

    private final Config config;
    private final ZoneId zone;
    private LocalDate day = LocalDate.MIN;
    private long todayIn;
    private long todayOut;
    private long totalIn;
    private long totalOut;
    private boolean warned;

    public CostTracker(Config config, ZoneId zone) {
        this.config = config;
        this.zone = zone;
    }

    /**
     * Adds one request. Returns true exactly once per day: when today's estimated cost reaches the warning
     * threshold for the first time. Always false when the feature or the threshold is off.
     */
    public synchronized boolean record(long inputTokens, long outputTokens, Instant now) {
        if (!config.enabled()) return false;
        rollover(now);
        long in = Math.max(0, inputTokens);
        long out = Math.max(0, outputTokens);
        todayIn += in;
        todayOut += out;
        totalIn += in;
        totalOut += out;
        if (!warned && config.warnDailyCost() > 0 && cost(todayIn, todayOut) >= config.warnDailyCost()) {
            warned = true;
            return true;
        }
        return false;
    }

    public synchronized Totals totals(Instant now) {
        rollover(now);
        return new Totals(todayIn, todayOut, totalIn, totalOut, cost(todayIn, todayOut), cost(totalIn, totalOut),
                config.warnDailyCost());
    }

    private double cost(long in, long out) {
        return in / 1_000_000.0 * config.inputPricePerMillion() + out / 1_000_000.0 * config.outputPricePerMillion();
    }

    private void rollover(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        if (!today.equals(day)) {
            day = today;
            todayIn = 0;
            todayOut = 0;
            warned = false;
        }
    }
}
