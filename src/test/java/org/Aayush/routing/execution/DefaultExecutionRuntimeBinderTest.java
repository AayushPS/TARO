package org.Aayush.routing.execution;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.heuristic.DefaultHeuristicProviderFactory;
import org.Aayush.routing.heuristic.HeuristicConfigurationException;
import org.Aayush.routing.heuristic.HeuristicFactory;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Execution Runtime Binder Tests")
class DefaultExecutionRuntimeBinderTest {

    @Test
    @DisplayName("Named A* execution profile binds successfully")
    void testNamedAStarProfileBindsSuccessfully() {
        RoutingFixtureFactory.Fixture fixture = createFixtureWithCoordinates();
        ExecutionRuntimeBinder.Binding binding = new DefaultExecutionRuntimeBinder().bind(
                ExecutionRuntimeBinder.BindInput.builder()
                        .executionRuntimeConfig(ExecutionRuntimeConfig.ofProfileId("CITY_ASTAR"))
                        .executionProfileRegistry(new ExecutionProfileRegistry(List.of(
                                ExecutionProfileSpec.aStar("CITY_ASTAR", HeuristicType.EUCLIDEAN)
                        )))
                        .edgeGraph(fixture.edgeGraph())
                        .profileStore(fixture.profileStore())
                        .costEngine(fixture.costEngine())
                        .heuristicProviderFactory(new DefaultHeuristicProviderFactory())
                        .build()
        );

        assertEquals("CITY_ASTAR", binding.getResolvedExecutionProfileContext().getProfileId());
        assertEquals(org.Aayush.routing.core.RoutingAlgorithm.A_STAR, binding.getResolvedExecutionProfileContext().getAlgorithm());
        assertEquals(HeuristicType.EUCLIDEAN, binding.getResolvedExecutionProfileContext().getHeuristicType());
        assertNotNull(binding.getHeuristicProvider());
    }

    @Test
    @DisplayName("Dijkstra execution profile rejects non-NONE heuristic")
    void testDijkstraRejectsNonNoneHeuristic() {
        RoutingFixtureFactory.Fixture fixture = createFixtureWithCoordinates();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new DefaultExecutionRuntimeBinder().bind(
                        ExecutionRuntimeBinder.BindInput.builder()
                                .executionRuntimeConfig(ExecutionRuntimeConfig.inline(
                                        ExecutionProfileSpec.builder()
                                                .algorithm(org.Aayush.routing.core.RoutingAlgorithm.DIJKSTRA)
                                                .heuristicType(HeuristicType.EUCLIDEAN)
                                                .build()
                                ))
                                .edgeGraph(fixture.edgeGraph())
                                .profileStore(fixture.profileStore())
                                .costEngine(fixture.costEngine())
                                .heuristicProviderFactory(new DefaultHeuristicProviderFactory())
                                .build()
                )
        );
        assertEquals(RouteCore.REASON_EXECUTION_PROFILE_INCOMPATIBLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Missing execution runtime config is rejected")
    void testMissingExecutionRuntimeConfigRejected() {
        RoutingFixtureFactory.Fixture fixture = createFixtureWithCoordinates();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new DefaultExecutionRuntimeBinder().bind(
                        ExecutionRuntimeBinder.BindInput.builder()
                                .edgeGraph(fixture.edgeGraph())
                                .profileStore(fixture.profileStore())
                                .costEngine(fixture.costEngine())
                                .heuristicProviderFactory(new DefaultHeuristicProviderFactory())
                                .build()
                )
        );
        assertEquals(RouteCore.REASON_EXECUTION_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("LANDMARK execution profile requires compatible landmark artifacts")
    void testLandmarkExecutionProfileRequiresCompatibleArtifacts() {
        RoutingFixtureFactory.Fixture fixture = createFixtureWithCoordinates();
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> new DefaultExecutionRuntimeBinder().bind(
                        ExecutionRuntimeBinder.BindInput.builder()
                                .executionRuntimeConfig(ExecutionRuntimeConfig.aStar(HeuristicType.LANDMARK))
                                .edgeGraph(fixture.edgeGraph())
                                .profileStore(fixture.profileStore())
                                .costEngine(fixture.costEngine())
                                .heuristicProviderFactory(new DefaultHeuristicProviderFactory())
                                .build()
                )
        );
        assertEquals(RouteCore.REASON_EXECUTION_PROFILE_INCOMPATIBLE, ex.getReasonCode());
        HeuristicConfigurationException cause = assertInstanceOf(HeuristicConfigurationException.class, ex.getCause());
        assertEquals(HeuristicFactory.REASON_LANDMARK_STORE_REQUIRED, cause.reasonCode());
    }

    private RoutingFixtureFactory.Fixture createFixtureWithCoordinates() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d,
                        3.0d, 0.0d,
                        4.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }
}
