package org.Aayush.routing.traits.temporal;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.core.time.TimeUtils;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Immutable hot-path temporal resolver bound once at startup.
 */
public final class TemporalContextResolver {
    private final TemporalResolutionStrategy strategy;
    private final ZoneId zoneId;
    private final TemporalOffsetCache offsetCache;

    /**
     * Creates a pre-bound temporal resolver.
     *
     * @param strategy selected strategy implementation.
     * @param zoneId selected zone id (nullable for non-calendar strategies).
     * @param offsetCache optional offset cache.
     */
    public TemporalContextResolver(
            TemporalResolutionStrategy strategy,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        if (this.strategy.dayMaskAware() && zoneId == null) {
            throw new IllegalArgumentException("zoneId is required for day-mask-aware temporal strategy");
        }
        if (offsetCache != null && zoneId == null) {
            throw new IllegalArgumentException("zoneId is required when offsetCache is provided");
        }
        this.zoneId = zoneId;
        this.offsetCache = offsetCache;
    }

    /**
     * Resolves day-of-week for one timestamp.
     */
    public int resolveDayOfWeek(long entryTicks, TimeUtils.EngineTimeUnit unit) {
        try {
            return strategy.resolveDayOfWeek(entryTicks, unit, zoneId, offsetCache);
        } catch (RuntimeException ex) {
            throw new TemporalResolutionException("failed to resolve day-of-week", ex);
        }
    }

    /**
     * Resolves integer bucket index for one timestamp.
     */
    public int resolveBucketIndex(long entryTicks, int bucketSizeSeconds, TimeUtils.EngineTimeUnit unit) {
        try {
            return strategy.resolveBucketIndex(entryTicks, bucketSizeSeconds, unit, zoneId, offsetCache);
        } catch (RuntimeException ex) {
            throw new TemporalResolutionException("failed to resolve bucket index", ex);
        }
    }

    /**
     * Resolves fractional bucket coordinate for one timestamp.
     */
    public double resolveFractionalBucket(long entryTicks, long bucketSizeTicks, TimeUtils.EngineTimeUnit unit) {
        try {
            return strategy.resolveFractionalBucket(entryTicks, bucketSizeTicks, unit, zoneId, offsetCache);
        } catch (RuntimeException ex) {
            throw new TemporalResolutionException("failed to resolve fractional bucket", ex);
        }
    }

    /**
     * Returns whether this resolver is day-mask aware.
     */
    public boolean dayMaskAware() {
        return strategy.dayMaskAware();
    }

    /**
     * Returns bound strategy id.
     */
    public String strategyId() {
        return strategy.id();
    }

    /**
     * Returns bound zone id, or {@code null} when not applicable.
     */
    public ZoneId zoneId() {
        return zoneId;
    }

    /**
     * Returns bound offset cache, or {@code null} when not used.
     */
    public TemporalOffsetCache offsetCache() {
        return offsetCache;
    }

    /**
     * Runtime temporal resolution failure.
     */
    @Getter
    @Accessors(fluent = true)
    public static final class TemporalResolutionException extends RuntimeException {
        private final String reasonCode = "H16_TEMPORAL_RESOLUTION_FAILURE";

        /**
         * Creates a deterministic temporal resolution failure.
         */
        public TemporalResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
