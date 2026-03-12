package org.Aayush.routing.traits.registry;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 18 TraitBundleRuntimeBinder Tests")
class TraitBundleRuntimeBinderTest {

    @Test
    @DisplayName("Named bundle resolution emits resolved context and telemetry")
    void testNamedBundleResolution() {
        TraitBundleRuntimeBinder.Binding binding = new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId("CITY_XY"))
                .traitBundleRegistry(new TraitBundleRegistry(List.of(bundleSpec(
                        "CITY_XY",
                        AddressingTraitCatalog.TRAIT_DEFAULT,
                        CoordinateStrategyRegistry.STRATEGY_XY
                ))))
                .build());

        assertEquals("CITY_XY", binding.getResolvedTraitBundleContext().getBundleId());
        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE, binding.getResolvedTraitBundleContext().getConfigSource());
        assertEquals(AddressingTraitCatalog.TRAIT_DEFAULT, binding.getResolvedTraitBundleContext().getAddressingTraitId());
        assertEquals(CoordinateStrategyRegistry.STRATEGY_XY, binding.getResolvedTraitBundleContext().getCoordinateDistanceStrategyId());
        assertEquals(TransitionTraitCatalog.TRAIT_EDGE_BASED, binding.getResolvedTraitBundleContext().getTransitionTraitId());
        assertNotNull(binding.getResolvedTraitBundleContext().getTraitHash());
        assertEquals(binding.getResolvedTraitBundleContext().getTraitHash(), binding.getTraitBundleTelemetry().getTraitHash());
    }

    @Test
    @DisplayName("Unknown bundle id is rejected deterministically")
    void testUnknownBundleRejected() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId("MISSING"))
                        .traitBundleRegistry(new TraitBundleRegistry())
                        .build())
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TRAIT_BUNDLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Bundle config requires a named or inline selection")
    void testBundleConfigRequired() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.builder().build())
                        .build())
        );
        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Named and inline bundle mismatch is rejected as config conflict")
    void testNamedAndInlineMismatchRejected() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.builder()
                                .traitBundleId("CITY_XY")
                                .inlineTraitBundleSpec(bundleSpec(
                                        null,
                                        AddressingTraitCatalog.TRAIT_DEFAULT,
                                        CoordinateStrategyRegistry.STRATEGY_LAT_LON
                                ))
                                .build())
                        .traitBundleRegistry(new TraitBundleRegistry(List.of(bundleSpec(
                                "CITY_XY",
                                AddressingTraitCatalog.TRAIT_DEFAULT,
                                CoordinateStrategyRegistry.STRATEGY_XY
                        ))))
                        .build())
        );
        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT, ex.getReasonCode());
    }

    @Test
    @DisplayName("Named bundle path conflicts with legacy axis configs when selections differ")
    void testBundlePathConflictsWithLegacyConfigs() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId("CITY_XY"))
                        .traitBundleRegistry(new TraitBundleRegistry(List.of(bundleSpec(
                                "CITY_XY",
                                AddressingTraitCatalog.TRAIT_DEFAULT,
                                CoordinateStrategyRegistry.STRATEGY_XY
                        ))))
                        .addressingRuntimeConfig(AddressingRuntimeConfig.latLonRuntime())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .build())
        );
        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT, ex.getReasonCode());
    }

    @Test
    @DisplayName("Named bundle path accepts matching legacy axis configs")
    void testBundlePathAcceptsMatchingLegacyConfigs() {
        TraitBundleRuntimeBinder.Binding binding = new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId(" CITY_XY "))
                .traitBundleRegistry(new TraitBundleRegistry(List.of(bundleSpec(
                        "CITY_XY",
                        AddressingTraitCatalog.TRAIT_DEFAULT,
                        CoordinateStrategyRegistry.STRATEGY_XY
                ))))
                .addressingRuntimeConfig(AddressingRuntimeConfig.xyRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .build());

        assertEquals("CITY_XY", binding.getResolvedTraitBundleContext().getBundleId());
        assertEquals(
                TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE,
                binding.getResolvedTraitBundleContext().getConfigSource()
        );
        assertEquals(
                binding.getResolvedTraitBundleContext().getTraitHash(),
                binding.getTraitBundleTelemetry().getTraitHash()
        );
    }

    @Test
    @DisplayName("Legacy axis configs synthesize a Stage 18 bundle context")
    void testLegacyAxisConfigsSynthesizeBundleContext() {
        TraitBundleRuntimeBinder.Binding binding = new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                .addressingRuntimeConfig(AddressingRuntimeConfig.xyRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .build());

        assertNull(binding.getResolvedTraitBundleContext().getBundleId());
        assertEquals(
                TraitBundleRuntimeBinder.CONFIG_SOURCE_LEGACY_AXIS_CONFIGS,
                binding.getResolvedTraitBundleContext().getConfigSource()
        );
        assertEquals(AddressingTraitCatalog.TRAIT_DEFAULT, binding.getResolvedTraitBundleContext().getAddressingTraitId());
        assertEquals(
                CoordinateStrategyRegistry.STRATEGY_XY,
                binding.getResolvedTraitBundleContext().getCoordinateDistanceStrategyId()
        );
    }

    @Test
    @DisplayName("Inline bundle resolution preserves explicit inline config source")
    void testInlineBundleResolution() {
        TraitBundleRuntimeBinder.Binding binding = new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.inline(bundleSpec(
                        " INLINE_CITY_XY ",
                        AddressingTraitCatalog.TRAIT_DEFAULT,
                        CoordinateStrategyRegistry.STRATEGY_XY
                )))
                .build());

        assertEquals("INLINE_CITY_XY", binding.getResolvedTraitBundleContext().getBundleId());
        assertEquals(
                TraitBundleRuntimeBinder.CONFIG_SOURCE_INLINE_BUNDLE,
                binding.getResolvedTraitBundleContext().getConfigSource()
        );
    }

    @Test
    @DisplayName("Partial legacy configs preserve existing axis-local required-code failures")
    void testPartialLegacyConfigPreservesAxisLocalRequiredCodes() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .addressingRuntimeConfig(AddressingRuntimeConfig.xyRuntime())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .build())
        );

        assertEquals(RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Coordinate-capable addressing bundle requires startup strategy dependency")
    void testMissingCoordinateStrategyDependencyRejected() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.inline(TraitBundleSpec.builder()
                                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                                .build()))
                        .build())
        );
        assertEquals(RouteCore.REASON_MISSING_TRAIT_DEPENDENCY, ex.getReasonCode());
    }

    @Test
    @DisplayName("External-id-only bundle rejects incompatible coordinate strategy")
    void testIncompatibleCoordinateStrategyRejected() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.inline(bundleSpec(
                                null,
                                AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY,
                                CoordinateStrategyRegistry.STRATEGY_XY
                        )))
                        .build())
        );
        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_INCOMPATIBLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Hasher failures are mapped to H18 trait-hash generation errors")
    void testTraitHashFailureMapped() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new TraitBundleRuntimeBinder().bind(TraitBundleRuntimeBinder.BindInput.builder()
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.inline(bundleSpec(
                                null,
                                AddressingTraitCatalog.TRAIT_DEFAULT,
                                CoordinateStrategyRegistry.STRATEGY_XY
                        )))
                        .traitBundleHasher(new TraitBundleHasher() {
                            @Override
                            public String hash(
                                    TraitBundleSpec bundleSpec,
                                    org.Aayush.routing.traits.temporal.ResolvedTemporalContext temporalContext,
                                    org.Aayush.routing.traits.transition.ResolvedTransitionContext transitionContext
                            ) {
                                throw new IllegalStateException("broken-hash");
                            }
                        })
                        .build())
        );
        assertEquals(RouteCore.REASON_TRAIT_HASH_GENERATION_FAILED, ex.getReasonCode());
        assertTrue(ex.getMessage().contains("broken-hash"));
    }

    private static TraitBundleSpec bundleSpec(
            String bundleId,
            String addressingTraitId,
            String coordinateStrategyId
    ) {
        return TraitBundleSpec.builder()
                .bundleId(bundleId)
                .addressingTraitId(addressingTraitId)
                .coordinateDistanceStrategyId(coordinateStrategyId)
                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                .build();
    }
}
