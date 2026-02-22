package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;

import java.time.ZoneId;

/**
 * Stage 16 linear temporal strategy.
 *
 * <p>This strategy uses periodic UTC bucket mapping and intentionally ignores
 * day-mask semantics at profile sampling call sites.</p>
 */
public final class LinearTemporalResolutionStrategy implements TemporalResolutionStrategy {

    /**
     * Returns strategy id.
     */
    @Override
    public String id() {
        return TemporalStrategyRegistry.STRATEGY_LINEAR;
    }

    /**
     * Returns {@code false}; linear mode is not day-mask aware.
     */
    @Override
    public boolean dayMaskAware() {
        return false;
    }

    /**
     * Resolves day-of-week from UTC ticks.
     */
    @Override
    public int resolveDayOfWeek(
            long entryTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        return TimeUtils.getDayOfWeek(entryTicks, unit);
    }

    /**
     * Resolves bucket index from UTC ticks.
     */
    @Override
    public int resolveBucketIndex(
            long entryTicks,
            int bucketSizeSeconds,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        return TimeUtils.toBucket(entryTicks, bucketSizeSeconds, unit);
    }

    /**
     * Resolves fractional bucket in UTC day domain.
     */
    @Override
    public double resolveFractionalBucket(
            long entryTicks,
            long bucketSizeTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        long timeOfDayTicks = TimeUtils.getTimeOfDayTicks(entryTicks, unit);
        return (double) timeOfDayTicks / (double) bucketSizeTicks;
    }
}
