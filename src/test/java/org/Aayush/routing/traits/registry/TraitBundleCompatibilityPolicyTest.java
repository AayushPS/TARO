package org.Aayush.routing.traits.registry;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Stage 18 TraitBundleCompatibilityPolicy Tests")
class TraitBundleCompatibilityPolicyTest {

    @Test
    @DisplayName("Coordinate-capable addressing treats blank strategy ids as missing dependency")
    void testCoordinateCapableAddressingRequiresStrategy() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> TraitBundleCompatibilityPolicy.defaults().validate(
                        TraitBundleSpec.builder()
                                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                                .coordinateDistanceStrategyId("   ")
                                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                                .build(),
                        AddressingTraitCatalog.defaultCatalog()
                )
        );

        assertEquals(RouteCore.REASON_MISSING_TRAIT_DEPENDENCY, ex.getReasonCode());
    }

    @Test
    @DisplayName("External-id-only addressing rejects startup coordinate strategy dependency")
    void testExternalIdOnlyAddressingRejectsCoordinateStrategy() {
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> TraitBundleCompatibilityPolicy.defaults().validate(
                        TraitBundleSpec.builder()
                                .addressingTraitId(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY)
                                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                                .build(),
                        AddressingTraitCatalog.defaultCatalog()
                )
        );

        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_INCOMPATIBLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown addressing traits are deferred to the Stage 15 binder")
    void testUnknownAddressingTraitIsDeferred() {
        assertDoesNotThrow(() -> TraitBundleCompatibilityPolicy.defaults().validate(
                TraitBundleSpec.builder()
                        .addressingTraitId("UNKNOWN_TRAIT")
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                        .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                        .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                        .build(),
                AddressingTraitCatalog.defaultCatalog()
        ));
    }
}
