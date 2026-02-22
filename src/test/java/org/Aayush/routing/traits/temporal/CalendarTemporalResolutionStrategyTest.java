package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Stage 16 CalendarTemporalResolutionStrategy Tests")
class CalendarTemporalResolutionStrategyTest {

    @Test
    @DisplayName("Fixed-offset zone resolves deterministic day and bucket without cache")
    void testFixedOffsetResolutionWithoutCache() {
        CalendarTemporalResolutionStrategy strategy = new CalendarTemporalResolutionStrategy();
        ZoneOffset zone = ZoneOffset.ofHours(2);
        long epochSeconds = ZonedDateTime.of(2026, 6, 5, 10, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();

        int expectedDay = TimeUtils.getDayOfWeekWithOffset(epochSeconds, zone.getTotalSeconds());
        int expectedBucket = TimeUtils.toBucketWithOffset(epochSeconds, 3_600, zone.getTotalSeconds());

        assertEquals(
                expectedDay,
                strategy.resolveDayOfWeek(epochSeconds, TimeUtils.EngineTimeUnit.SECONDS, zone, null)
        );
        assertEquals(
                expectedBucket,
                strategy.resolveBucketIndex(epochSeconds, 3_600, TimeUtils.EngineTimeUnit.SECONDS, zone, null)
        );
    }

    @Test
    @DisplayName("Offset-cache and direct-rules paths agree across DST transition day")
    void testOffsetCacheAndDirectRulesParity() {
        CalendarTemporalResolutionStrategy strategy = new CalendarTemporalResolutionStrategy();
        ZoneId zoneId = ZoneId.of("America/New_York");
        TemporalOffsetCache cache = new TemporalOffsetCache(zoneId);

        long before = ZonedDateTime.of(2026, 3, 8, 6, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();
        long after = ZonedDateTime.of(2026, 3, 8, 7, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();

        assertEquals(
                strategy.resolveDayOfWeek(before, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null),
                strategy.resolveDayOfWeek(before, TimeUtils.EngineTimeUnit.SECONDS, zoneId, cache)
        );
        assertEquals(
                strategy.resolveBucketIndex(before, 3_600, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null),
                strategy.resolveBucketIndex(before, 3_600, TimeUtils.EngineTimeUnit.SECONDS, zoneId, cache)
        );

        assertEquals(
                strategy.resolveDayOfWeek(after, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null),
                strategy.resolveDayOfWeek(after, TimeUtils.EngineTimeUnit.SECONDS, zoneId, cache)
        );
        assertEquals(
                strategy.resolveBucketIndex(after, 3_600, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null),
                strategy.resolveBucketIndex(after, 3_600, TimeUtils.EngineTimeUnit.SECONDS, zoneId, cache)
        );
    }

    @Test
    @DisplayName("Extreme epoch seconds are clamped for offset lookup deterministically")
    void testExtremeEpochClampDeterminism() {
        CalendarTemporalResolutionStrategy strategy = new CalendarTemporalResolutionStrategy();
        ZoneId zoneId = ZoneId.of("America/New_York");

        long belowMin = Instant.MIN.getEpochSecond() - 1L;
        long aboveMax = Instant.MAX.getEpochSecond() + 1L;

        int minOffset = zoneId.getRules().getOffset(Instant.MIN).getTotalSeconds();
        int maxOffset = zoneId.getRules().getOffset(Instant.MAX).getTotalSeconds();

        int expectedDayBelowMin = TimeUtils.getDayOfWeekWithOffset(belowMin, minOffset);
        int expectedDayAboveMax = TimeUtils.getDayOfWeekWithOffset(aboveMax, maxOffset);

        assertEquals(
                expectedDayBelowMin,
                strategy.resolveDayOfWeek(belowMin, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null)
        );
        assertEquals(
                expectedDayAboveMax,
                strategy.resolveDayOfWeek(aboveMax, TimeUtils.EngineTimeUnit.SECONDS, zoneId, null)
        );
    }
}
