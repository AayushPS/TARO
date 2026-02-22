package org.Aayush.routing.traits.temporal;

import org.Aayush.routing.core.RouteCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Stage 16 TemporalPolicy Tests")
class TemporalPolicyTest {

    @Test
    @DisplayName("CALENDAR requires timezone policy")
    void testCalendarRequiresTimezonePolicy() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        TemporalPolicy.CompatibilityException ex = assertThrows(
                TemporalPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TemporalTraitCatalog.TRAIT_CALENDAR, TemporalStrategyRegistry.STRATEGY_CALENDAR),
                        new CalendarTemporalResolutionStrategy(),
                        null
                )
        );
        assertEquals(RouteCore.REASON_TIMEZONE_POLICY_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("LINEAR rejects timezone policy")
    void testLinearRejectsTimezonePolicy() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        TemporalPolicy.CompatibilityException ex = assertThrows(
                TemporalPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TemporalTraitCatalog.TRAIT_LINEAR, TemporalStrategyRegistry.STRATEGY_LINEAR),
                        new LinearTemporalResolutionStrategy(),
                        TemporalTimezonePolicyRegistry.POLICY_UTC
                )
        );
        assertEquals(RouteCore.REASON_TIMEZONE_POLICY_NOT_APPLICABLE, ex.reasonCode());
    }

    @Test
    @DisplayName("Incompatible trait and strategy pair is rejected")
    void testIncompatibleTraitAndStrategy() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        TemporalResolutionStrategy dayMaskAgnosticCalendar = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "BAD_CALENDAR";
            }

            @Override
            public boolean dayMaskAware() {
                return false;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    org.Aayush.core.time.TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    org.Aayush.core.time.TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0;
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    org.Aayush.core.time.TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                return 0.0d;
            }
        };

        TemporalPolicy.CompatibilityException ex = assertThrows(
                TemporalPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TemporalTraitCatalog.TRAIT_CALENDAR, TemporalStrategyRegistry.STRATEGY_CALENDAR),
                        dayMaskAgnosticCalendar,
                        TemporalTimezonePolicyRegistry.POLICY_UTC
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("Unknown temporal trait is rejected deterministically")
    void testUnknownTraitRejected() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        TemporalPolicy.CompatibilityException ex = assertThrows(
                TemporalPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait("CUSTOM_TRAIT", "CUSTOM"),
                        new LinearTemporalResolutionStrategy(),
                        null
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("LINEAR with day-mask-aware strategy is rejected")
    void testLinearWithDayMaskAwareStrategyRejected() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        TemporalPolicy.CompatibilityException ex = assertThrows(
                TemporalPolicy.CompatibilityException.class,
                () -> policy.validateCompatibility(
                        trait(TemporalTraitCatalog.TRAIT_LINEAR, TemporalStrategyRegistry.STRATEGY_LINEAR),
                        new CalendarTemporalResolutionStrategy(),
                        null
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE, ex.reasonCode());
    }

    @Test
    @DisplayName("Compatible LINEAR and CALENDAR tuples are accepted")
    void testCompatibleTuplesAccepted() {
        TemporalPolicy policy = TemporalPolicy.defaults();
        assertDoesNotThrow(() -> policy.validateCompatibility(
                trait(TemporalTraitCatalog.TRAIT_LINEAR, TemporalStrategyRegistry.STRATEGY_LINEAR),
                new LinearTemporalResolutionStrategy(),
                null
        ));
        assertDoesNotThrow(() -> policy.validateCompatibility(
                trait(TemporalTraitCatalog.TRAIT_CALENDAR, TemporalStrategyRegistry.STRATEGY_CALENDAR),
                new CalendarTemporalResolutionStrategy(),
                TemporalTimezonePolicyRegistry.POLICY_UTC
        ));
    }

    private static TemporalTrait trait(String id, String strategyId) {
        return new TemporalTrait() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String strategyId() {
                return strategyId;
            }
        };
    }
}
