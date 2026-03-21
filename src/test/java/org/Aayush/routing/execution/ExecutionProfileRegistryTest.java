package org.Aayush.routing.execution;

import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Execution Profile Registry Tests")
class ExecutionProfileRegistryTest {

    @Test
    @DisplayName("Default registry is empty and blank lookups resolve to null")
    void testDefaultRegistryShape() {
        ExecutionProfileRegistry registry = ExecutionProfileRegistry.defaultRegistry();

        assertNull(registry.profile(null));
        assertNull(registry.profile(" "));
        assertEquals(0, registry.profileIds().size());
        assertThrows(UnsupportedOperationException.class, () -> registry.profileIds().add("new"));
    }

    @Test
    @DisplayName("Registry normalizes ids on insert and lookup")
    void testRegistryNormalizesIds() {
        ExecutionProfileRegistry registry = new ExecutionProfileRegistry(List.of(
                ExecutionProfileSpec.builder()
                        .profileId("  fastest  ")
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.EUCLIDEAN)
                        .build()
        ));

        ExecutionProfileSpec profile = registry.profile("fastest");
        assertEquals("fastest", profile.getProfileId());
        assertEquals(RoutingAlgorithm.A_STAR, profile.getAlgorithm());
        assertEquals(HeuristicType.EUCLIDEAN, profile.getHeuristicType());
        assertNull(registry.profile("missing"));
    }

    @Test
    @DisplayName("Registry rejects null entries, blank ids, and duplicates after normalization")
    void testRegistryRejectsInvalidEntries() {
        assertThrows(NullPointerException.class, () -> new ExecutionProfileRegistry(Arrays.asList((ExecutionProfileSpec) null)));
        assertThrows(NullPointerException.class, () -> new ExecutionProfileRegistry(List.of(
                ExecutionProfileSpec.builder()
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build()
        )));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProfileRegistry(List.of(
                ExecutionProfileSpec.builder()
                        .profileId("   ")
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build()
        )));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProfileRegistry(List.of(
                ExecutionProfileSpec.dijkstra("fastest"),
                ExecutionProfileSpec.aStar("  fastest  ", HeuristicType.NONE)
        )));
    }
}
