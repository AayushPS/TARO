package org.Aayush.routing.traits.registry;

import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 18 TraitBundleRegistry Tests")
class TraitBundleRegistryTest {

    @Test
    @DisplayName("Registry normalizes bundle ids for storage and lookup")
    void testRegistryNormalizesBundleIds() {
        TraitBundleRegistry registry = new TraitBundleRegistry(List.of(bundleSpec("  CITY_XY  ")));

        TraitBundleSpec resolved = registry.bundle(" CITY_XY ");

        assertNotNull(resolved);
        assertEquals("CITY_XY", resolved.getBundleId());
        assertEquals(List.of("CITY_XY"), registry.bundleIds().stream().sorted().toList());
    }

    @Test
    @DisplayName("Registry rejects duplicate normalized bundle ids")
    void testRegistryRejectsDuplicateBundleIds() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TraitBundleRegistry(List.of(
                        bundleSpec("CITY_XY"),
                        bundleSpec(" CITY_XY ")
                ))
        );

        assertTrue(ex.getMessage().contains("duplicate trait bundle id: CITY_XY"));
    }

    private static TraitBundleSpec bundleSpec(String bundleId) {
        return TraitBundleSpec.builder()
                .bundleId(bundleId)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                .build();
    }
}
