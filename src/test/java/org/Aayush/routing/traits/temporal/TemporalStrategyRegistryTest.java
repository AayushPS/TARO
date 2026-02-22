package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalStrategyRegistry Tests")
class TemporalStrategyRegistryTest {

    @Test
    @DisplayName("Default registry exposes LINEAR and CALENDAR strategies")
    void testDefaultRegistryBuiltIns() {
        TemporalStrategyRegistry registry = TemporalStrategyRegistry.defaultRegistry();
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR));
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_CALENDAR));
        assertEquals(
                TemporalStrategyRegistry.STRATEGY_LINEAR,
                registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR).id()
        );
        assertEquals(
                TemporalStrategyRegistry.STRATEGY_CALENDAR,
                registry.strategy(TemporalStrategyRegistry.STRATEGY_CALENDAR).id()
        );
    }

    @Test
    @DisplayName("Custom strategy overrides built-in id deterministically")
    void testCustomStrategyOverride() {
        TemporalResolutionStrategy custom = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return TemporalStrategyRegistry.STRATEGY_LINEAR;
            }

            @Override
            public boolean dayMaskAware() {
                return false;
            }

            @Override
            public int resolveDayOfWeek(long entryTicks, TimeUtils.EngineTimeUnit unit, ZoneId zoneId, TemporalOffsetCache offsetCache) {
                return 0;
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0.0d;
            }
        };

        TemporalStrategyRegistry registry = new TemporalStrategyRegistry(List.of(custom));
        assertTrue(registry.strategyIds().contains(TemporalStrategyRegistry.STRATEGY_LINEAR));
        assertEquals(custom, registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR));
    }

    @Test
    @DisplayName("Null strategy lookup returns null")
    void testNullLookupReturnsNull() {
        TemporalStrategyRegistry registry = TemporalStrategyRegistry.defaultRegistry();
        assertNull(registry.strategy(null));
    }

    @Test
    @DisplayName("Null custom strategy collection falls back to built-ins")
    void testNullCustomCollectionUsesBuiltIns() {
        TemporalStrategyRegistry registry = new TemporalStrategyRegistry((Collection<? extends TemporalResolutionStrategy>) null);
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR));
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_CALENDAR));
    }

    @Test
    @DisplayName("Explicit registry can exclude built-ins")
    void testExplicitRegistryWithoutBuiltIns() {
        TemporalResolutionStrategy custom = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public boolean dayMaskAware() {
                return false;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0.0d;
            }
        };

        TemporalStrategyRegistry registry = new TemporalStrategyRegistry(List.of(custom), false);
        assertNotNull(registry.strategy("CUSTOM"));
        assertNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR));
        assertNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_CALENDAR));
    }

    @Test
    @DisplayName("Explicit registry can include built-ins")
    void testExplicitRegistryWithBuiltIns() {
        TemporalResolutionStrategy custom = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "CUSTOM";
            }

            @Override
            public boolean dayMaskAware() {
                return false;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0.0d;
            }
        };

        TemporalStrategyRegistry registry = new TemporalStrategyRegistry(List.of(custom), true);
        assertNotNull(registry.strategy("CUSTOM"));
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_LINEAR));
        assertNotNull(registry.strategy(TemporalStrategyRegistry.STRATEGY_CALENDAR));
    }

    @Test
    @DisplayName("Registry rejects null strategy entries")
    void testRegistryRejectsNullEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new TemporalStrategyRegistry(java.util.Arrays.asList((TemporalResolutionStrategy) null), false)
        );
    }

    @Test
    @DisplayName("Registry rejects blank strategy id")
    void testRegistryRejectsBlankStrategyId() {
        TemporalResolutionStrategy blank = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "   ";
            }

            @Override
            public boolean dayMaskAware() {
                return false;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0.0d;
            }
        };

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TemporalStrategyRegistry(List.of(blank), false)
        );
        assertTrue(ex.getMessage().contains("strategy.id"));
    }
}
