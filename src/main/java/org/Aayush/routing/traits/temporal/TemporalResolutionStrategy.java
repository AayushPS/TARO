package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;

import java.time.ZoneId;

/**
 * Strategy contract for temporal-coordinate resolution.
 *
 * <p>Implementations translate entry ticks into day/bucket/fractional-bucket
 * coordinates under one temporal mode.</p>
 */
public interface TemporalResolutionStrategy {

    /**
     * Returns stable strategy identifier.
     */
    String id();

    /**
     * Returns whether this strategy applies day-mask aware profile sampling.
     */
    boolean dayMaskAware();

    /**
     * Resolves day-of-week for one entry timestamp.
     *
     * @param entryTicks entry timestamp in engine ticks.
     * @param unit engine tick unit.
     * @param zoneId optional zone context (required for calendar-like strategies).
     * @param offsetCache optional deterministic zone offset cache.
     * @return day index where Monday=0 and Sunday=6.
     */
    int resolveDayOfWeek(
            long entryTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    );

    /**
     * Resolves integer bucket index for one entry timestamp.
     *
     * @param entryTicks entry timestamp in engine ticks.
     * @param bucketSizeSeconds profile bucket size in seconds.
     * @param unit engine tick unit.
     * @param zoneId optional zone context (required for calendar-like strategies).
     * @param offsetCache optional deterministic zone offset cache.
     * @return bucket index in range {@code [0, bucketsPerDay - 1]}.
     */
    int resolveBucketIndex(
            long entryTicks,
            int bucketSizeSeconds,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    );

    /**
     * Resolves fractional bucket coordinate for interpolation.
     *
     * @param entryTicks entry timestamp in engine ticks.
     * @param bucketSizeTicks bucket size in engine ticks.
     * @param unit engine tick unit.
     * @param zoneId optional zone context (required for calendar-like strategies).
     * @param offsetCache optional deterministic zone offset cache.
     * @return fractional bucket coordinate in cyclic day domain.
     */
    double resolveFractionalBucket(
            long entryTicks,
            long bucketSizeTicks,
            TimeUtils.EngineTimeUnit unit,
            ZoneId zoneId,
            TemporalOffsetCache offsetCache
    );
}
