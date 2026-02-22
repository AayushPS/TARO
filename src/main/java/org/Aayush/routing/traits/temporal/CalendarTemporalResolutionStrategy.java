package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.util.Objects;

/**
 * Stage 16 calendar temporal strategy.
 *
 * <p>This strategy resolves local day and local bucket coordinates using an explicit
 * timezone policy and deterministic DST-aware offset handling.</p>
 */
public final class CalendarTemporalResolutionStrategy implements TemporalResolutionStrategy {

    /**
     * Returns strategy id.
     */
    @Override
    public String id() {
        return TemporalStrategyRegistry.STRATEGY_CALENDAR;
    }

    /**
     * Returns {@code true}; calendar mode is day-mask aware.
     */
    @Override
    public boolean dayMaskAware() {
        return true;
    }

    /**
     * Resolves timezone-aware local day-of-week.
     */
    @Override
    public int resolveDayOfWeek(
            long entryTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        long epochSeconds = TimeUtils.toEpochSeconds(entryTicks, unit);
        int offsetSeconds = resolveOffsetSeconds(epochSeconds, zoneId, offsetCache);
        return TimeUtils.getDayOfWeekWithOffset(epochSeconds, offsetSeconds);
    }

    /**
     * Resolves timezone-aware local bucket index.
     */
    @Override
    public int resolveBucketIndex(
            long entryTicks,
            int bucketSizeSeconds,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        long epochSeconds = TimeUtils.toEpochSeconds(entryTicks, unit);
        int offsetSeconds = resolveOffsetSeconds(epochSeconds, zoneId, offsetCache);
        return TimeUtils.toBucketWithOffset(epochSeconds, bucketSizeSeconds, offsetSeconds);
    }

    /**
     * Resolves timezone-aware local fractional bucket coordinate.
     */
    @Override
    public double resolveFractionalBucket(
            long entryTicks,
            long bucketSizeTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    ) {
        long epochSeconds = TimeUtils.toEpochSeconds(entryTicks, unit);
        int offsetSeconds = resolveOffsetSeconds(epochSeconds, zoneId, offsetCache);
        long timeOfDayTicks = TimeUtils.getTimeOfDayTicksFromEpochSecondsWithOffset(epochSeconds, unit, offsetSeconds);
        return (double) timeOfDayTicks / (double) bucketSizeTicks;
    }

    private int resolveOffsetSeconds(long epochSeconds, ZoneId zoneId, TemporalOffsetCache offsetCache) {
        ZoneId nonNullZoneId = Objects.requireNonNull(zoneId, "zoneId");
        if (offsetCache != null) {
            return offsetCache.offsetSeconds(epochSeconds);
        }
        ZoneRules rules = nonNullZoneId.getRules();
        if (rules.isFixedOffset()) {
            return rules.getOffset(Instant.EPOCH).getTotalSeconds();
        }
        long clampedEpochSeconds = clampEpochSecond(epochSeconds);
        return rules.getOffset(Instant.ofEpochSecond(clampedEpochSeconds)).getTotalSeconds();
    }

    private long clampEpochSecond(long epochSeconds) {
        long min = Instant.MIN.getEpochSecond();
        long max = Instant.MAX.getEpochSecond();
        if (epochSeconds < min) {
            return min;
        }
        if (epochSeconds > max) {
            return max;
        }
        return epochSeconds;
    }
}
