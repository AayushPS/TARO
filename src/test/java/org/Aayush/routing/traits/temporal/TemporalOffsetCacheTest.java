package org.Aayush.routing.traits.temporal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalOffsetCache Tests")
class TemporalOffsetCacheTest {

    @Test
    @DisplayName("Cache rejects null zone id")
    void testCacheRejectsNullZone() {
        assertThrows(NullPointerException.class, () -> new TemporalOffsetCache(null));
    }

    @Test
    @DisplayName("UTC cache always returns zero offset")
    void testUtcOffsetCache() {
        TemporalOffsetCache cache = new TemporalOffsetCache(ZoneOffset.UTC);
        assertEquals(0, cache.offsetSeconds(0L));
        assertEquals(0, cache.offsetSeconds(1_700_000_000L));
        assertTrue(cache.cachedDays() >= 1);
    }

    @Test
    @DisplayName("America/New_York spring-forward day resolves deterministic offset change")
    void testSpringForwardOffsetChange() {
        ZoneId zoneId = ZoneId.of("America/New_York");
        TemporalOffsetCache cache = new TemporalOffsetCache(zoneId);

        long before = ZonedDateTime.of(2026, 3, 8, 1, 59, 59, 0, zoneId).toEpochSecond();
        long after = ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, zoneId).toEpochSecond();

        assertEquals(-18_000, cache.offsetSeconds(before));
        assertEquals(-14_400, cache.offsetSeconds(after));
    }

    @Test
    @DisplayName("America/New_York fall-back day resolves deterministic offset change")
    void testFallBackOffsetChange() {
        ZoneId zoneId = ZoneId.of("America/New_York");
        TemporalOffsetCache cache = new TemporalOffsetCache(zoneId);

        long before = ZonedDateTime.of(2026, 11, 1, 0, 59, 59, 0, zoneId).toEpochSecond();
        long after = ZonedDateTime.of(2026, 11, 1, 2, 0, 0, 0, zoneId).toEpochSecond();

        assertEquals(-14_400, cache.offsetSeconds(before));
        assertEquals(-18_000, cache.offsetSeconds(after));
    }

    @Test
    @DisplayName("Fixed-offset zone remains stable for large epoch range")
    void testFixedOffsetZoneStableAcrossLargeEpochRange() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(5, 30);
        TemporalOffsetCache cache = new TemporalOffsetCache(offset);
        assertEquals(19_800, cache.offsetSeconds(Long.MIN_VALUE));
        assertEquals(19_800, cache.offsetSeconds(0L));
        assertEquals(19_800, cache.offsetSeconds(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Extreme epoch values use deterministic fallback clamping")
    void testExtremeEpochFallbackClamping() {
        ZoneId zoneId = ZoneId.of("America/New_York");
        TemporalOffsetCache cache = new TemporalOffsetCache(zoneId);

        int minExpected = zoneId.getRules().getOffset(Instant.MIN).getTotalSeconds();
        int maxExpected = zoneId.getRules().getOffset(Instant.MAX).getTotalSeconds();

        assertEquals(minExpected, cache.offsetSeconds(Long.MIN_VALUE));
        assertEquals(maxExpected, cache.offsetSeconds(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Clamp helper returns unchanged epoch in in-range path")
    void testClampHelperInRangePath() throws Exception {
        TemporalOffsetCache cache = new TemporalOffsetCache(ZoneOffset.UTC);
        Method clamp = TemporalOffsetCache.class.getDeclaredMethod("clampEpochSecond", long.class);
        clamp.setAccessible(true);
        assertEquals(123_456L, (long) clamp.invoke(cache, 123_456L));
    }

    @Test
    @DisplayName("Day schedule boundary fallback paths are deterministic")
    void testDayScheduleBoundaryFallbackPaths() throws Exception {
        Class<?> scheduleClass = Class.forName("org.Aayush.routing.traits.temporal.TemporalOffsetCache$DayOffsetSchedule");
        Constructor<?> ctor = scheduleClass.getDeclaredConstructor(long[].class, long[].class, int[].class);
        ctor.setAccessible(true);
        Object schedule = ctor.newInstance(
                new long[]{10L, 20L},
                new long[]{20L, 30L},
                new int[]{111, 222}
        );
        Method offsetMethod = scheduleClass.getDeclaredMethod("offsetSeconds", long.class);
        offsetMethod.setAccessible(true);

        assertEquals(111, (int) offsetMethod.invoke(schedule, 5L));   // before first window
        assertEquals(111, (int) offsetMethod.invoke(schedule, 15L));  // inside first window
        assertEquals(222, (int) offsetMethod.invoke(schedule, 25L));  // inside second window
        assertEquals(222, (int) offsetMethod.invoke(schedule, 35L));  // after last window
    }
}
