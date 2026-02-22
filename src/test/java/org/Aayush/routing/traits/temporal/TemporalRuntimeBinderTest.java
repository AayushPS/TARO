package org.Aayush.routing.traits.temporal;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 TemporalRuntimeBinder Tests")
class TemporalRuntimeBinderTest {

    @Test
    @DisplayName("Missing runtime config is rejected")
    void testMissingRuntimeConfigRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        null,
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown temporal trait is rejected with deterministic reason code")
    void testUnknownTemporalTraitRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder().temporalTraitId("UNKNOWN").build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TEMPORAL_TRAIT, ex.getReasonCode());
    }

    @Test
    @DisplayName("Blank temporal trait id is rejected as missing config")
    void testBlankTemporalTraitRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder().temporalTraitId("   ").build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown temporal strategy id is rejected deterministically")
    void testUnknownTemporalStrategyRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalTrait brokenCalendarTrait = new TemporalTrait() {
            @Override
            public String id() {
                return TemporalTraitCatalog.TRAIT_CALENDAR;
            }

            @Override
            public String strategyId() {
                return "MISSING_STRATEGY";
            }
        };
        TemporalTraitCatalog catalog = new TemporalTraitCatalog(List.of(brokenCalendarTrait));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.calendarUtc(),
                        catalog,
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TEMPORAL_STRATEGY, ex.getReasonCode());
    }

    @Test
    @DisplayName("CALENDAR requires timezone policy")
    void testCalendarRequiresTimezonePolicy() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TIMEZONE_POLICY_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("CALENDAR blank timezone policy is treated as missing")
    void testCalendarBlankTimezonePolicyRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId("   ")
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TIMEZONE_POLICY_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown timezone policy id is rejected deterministically")
    void testUnknownTimezonePolicyRejected() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId("UNKNOWN_POLICY")
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TIMEZONE_POLICY, ex.getReasonCode());
    }

    @Test
    @DisplayName("LINEAR rejects timezone policy")
    void testLinearRejectsTimezonePolicy() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_LINEAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TIMEZONE_POLICY_NOT_APPLICABLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE rejects missing timezone metadata")
    void testModelTimezoneRequiresMetadata() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE)
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_MODEL_TIMEZONE_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE rejects invalid timezone metadata")
    void testModelTimezoneRejectsInvalidMetadata() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE)
                                .modelProfileTimezone("Invalid/Timezone")
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        TemporalTimezonePolicyRegistry.defaultRegistry(),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_INVALID_MODEL_TIMEZONE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Trait and timezone ids are trimmed before catalog lookup")
    void testIdsTrimmedBeforeLookup() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalRuntimeBinder.Binding binding = binder.bind(
                TemporalRuntimeConfig.builder()
                        .temporalTraitId("  " + TemporalTraitCatalog.TRAIT_CALENDAR + "  ")
                        .timezonePolicyId("  " + TemporalTimezonePolicyRegistry.POLICY_UTC + "  ")
                        .build(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );

        assertEquals(TemporalTraitCatalog.TRAIT_CALENDAR, binding.getResolvedTemporalContext().getTemporalTraitId());
        assertEquals(
                TemporalTimezonePolicyRegistry.POLICY_UTC,
                binding.getResolvedTemporalContext().getTimezonePolicyId()
        );
        assertEquals("Z", binding.getResolvedTemporalContext().getZoneId());
    }

    @Test
    @DisplayName("CALENDAR plus UTC binds deterministic day-mask-aware resolver")
    void testCalendarUtcBinding() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalRuntimeBinder.Binding binding = binder.bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );

        assertNotNull(binding.getTemporalContextResolver());
        assertNotNull(binding.getResolvedTemporalContext());
        assertEquals(TemporalTraitCatalog.TRAIT_CALENDAR, binding.getResolvedTemporalContext().getTemporalTraitId());
        assertTrue(binding.getResolvedTemporalContext().isDayMaskAware());
        assertEquals(
                TemporalTimezonePolicyRegistry.POLICY_UTC,
                binding.getResolvedTemporalContext().getTimezonePolicyId()
        );
    }

    @Test
    @DisplayName("LINEAR binding is day-mask agnostic and has no timezone policy")
    void testLinearBinding() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalRuntimeBinder.Binding binding = binder.bind(
                TemporalRuntimeConfig.linear(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );

        assertEquals(TemporalTraitCatalog.TRAIT_LINEAR, binding.getResolvedTemporalContext().getTemporalTraitId());
        assertTrue(!binding.getResolvedTemporalContext().isDayMaskAware());
        assertNull(binding.getResolvedTemporalContext().getTimezonePolicyId());
        assertNull(binding.getResolvedTemporalContext().getZoneId());
    }

    @Test
    @DisplayName("CALENDAR MODEL_TIMEZONE binds zone, offset cache, and telemetry")
    void testCalendarModelTimezoneBindingContracts() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalRuntimeBinder.Binding binding = binder.bind(
                TemporalRuntimeConfig.calendarModelTimezone("America/New_York"),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        );

        assertEquals("America/New_York", binding.getResolvedTemporalContext().getZoneId());
        assertEquals(
                TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE,
                binding.getResolvedTemporalContext().getTimezonePolicyId()
        );
        assertNotNull(binding.getTemporalContextResolver().zoneId());
        assertNotNull(binding.getTemporalContextResolver().offsetCache());
        assertEquals(ZoneId.of("America/New_York"), binding.getTemporalContextResolver().zoneId());
        assertEquals(TemporalTraitCatalog.TRAIT_CALENDAR, binding.getTemporalTelemetry().getTemporalTraitId());
        assertEquals(
                TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE,
                binding.getTemporalTelemetry().getTimezonePolicyId()
        );
    }

    @Test
    @DisplayName("CALENDAR startup should map unexpected timezone-policy failures deterministically")
    void testCalendarRejectsUnexpectedTimezonePolicyFailure() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalTimezonePolicy explodingPolicy = new TemporalTimezonePolicy() {
            @Override
            public String id() {
                return "EXPLODING_POLICY";
            }

            @Override
            public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
                throw new IllegalStateException("timezone backend unavailable");
            }
        };

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId("EXPLODING_POLICY")
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        new TemporalTimezonePolicyRegistry(List.of(explodingPolicy)),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("CALENDAR startup should fail fast when timezone policy resolves null zone")
    void testCalendarRejectsNullZoneFromTimezonePolicy() {
        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        TemporalTimezonePolicy nullZonePolicy = new TemporalTimezonePolicy() {
            @Override
            public String id() {
                return "NULL_ZONE";
            }

            @Override
            public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
                return null;
            }
        };

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> binder.bind(
                        TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId("NULL_ZONE")
                                .build(),
                        TemporalTraitCatalog.defaultCatalog(),
                        TemporalStrategyRegistry.defaultRegistry(),
                        new TemporalTimezonePolicyRegistry(List.of(nullZonePolicy)),
                        TemporalPolicy.defaults()
                )
        );
        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE, ex.getReasonCode());
    }
}
