package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalContextResolver Tests")
class TemporalContextResolverTest {

    @Test
    @DisplayName("Resolver accessors expose bound strategy metadata")
    void testResolverAccessors() {
        TemporalContextResolver resolver = new TemporalContextResolver(
                new LinearTemporalResolutionStrategy(),
                ZoneOffset.UTC,
                null
        );

        assertEquals(TemporalStrategyRegistry.STRATEGY_LINEAR, resolver.strategyId());
        assertTrue(!resolver.dayMaskAware());
        assertEquals(ZoneOffset.UTC, resolver.zoneId());
        assertNull(resolver.offsetCache());
    }

    @Test
    @DisplayName("Resolver wraps strategy runtime failures with H16 temporal resolution code")
    void testResolverWrapsStrategyFailures() {
        TemporalResolutionStrategy failing = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "FAILING";
            }

            @Override
            public boolean dayMaskAware() {
                return true;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    TimeUtils.EngineTimeUnit unit,
                    java.time.ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced day failure");
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    java.time.ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced bucket failure");
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    java.time.ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced fractional failure");
            }
        };

        TemporalContextResolver resolver = new TemporalContextResolver(failing, ZoneOffset.UTC, null);

        TemporalContextResolver.TemporalResolutionException dayEx = assertThrows(
                TemporalContextResolver.TemporalResolutionException.class,
                () -> resolver.resolveDayOfWeek(0L, TimeUtils.EngineTimeUnit.SECONDS)
        );
        assertEquals("H16_TEMPORAL_RESOLUTION_FAILURE", dayEx.reasonCode());
        assertTrue(dayEx.getCause() instanceof IllegalStateException);

        TemporalContextResolver.TemporalResolutionException bucketEx = assertThrows(
                TemporalContextResolver.TemporalResolutionException.class,
                () -> resolver.resolveBucketIndex(0L, 3600, TimeUtils.EngineTimeUnit.SECONDS)
        );
        assertEquals("H16_TEMPORAL_RESOLUTION_FAILURE", bucketEx.reasonCode());
        assertTrue(bucketEx.getCause() instanceof IllegalStateException);

        TemporalContextResolver.TemporalResolutionException fractionalEx = assertThrows(
                TemporalContextResolver.TemporalResolutionException.class,
                () -> resolver.resolveFractionalBucket(0L, 3600L, TimeUtils.EngineTimeUnit.SECONDS)
        );
        assertEquals("H16_TEMPORAL_RESOLUTION_FAILURE", fractionalEx.reasonCode());
        assertTrue(fractionalEx.getCause() instanceof IllegalStateException);
    }

    @Test
    @DisplayName("Day-mask-aware strategy requires non-null zone at construction")
    void testDayMaskAwareStrategyRequiresZone() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalContextResolver(
                        new CalendarTemporalResolutionStrategy(),
                        null,
                        null
                )
        );
        assertTrue(ex.getMessage().contains("zoneId is required"));
    }

    @Test
    @DisplayName("Offset cache cannot be supplied without zone")
    void testOffsetCacheRequiresZone() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalContextResolver(
                        new LinearTemporalResolutionStrategy(),
                        null,
                        new TemporalOffsetCache(ZoneOffset.UTC)
                )
        );
        assertTrue(ex.getMessage().contains("zoneId is required"));
    }
}
