package org.Aayush.routing.core;

import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Execution Profile RouteCore Tests")
class ExecutionProfileRouteCoreTest {

    @Test
    @DisplayName("Route and matrix requests succeed without per-request algorithm or heuristic")
    void testRequestsSucceedWithoutPerRequestExecutionSelectors() {
        RouteCore core = createCore(ExecutionRuntimeConfig.aStar(HeuristicType.NONE));

        RouteResponse routeResponse = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build());
        MatrixResponse matrixResponse = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build());

        assertTrue(routeResponse.isReachable());
        assertEquals(RoutingAlgorithm.A_STAR, routeResponse.getAlgorithm());
        assertEquals(HeuristicType.NONE, routeResponse.getHeuristicType());
        assertTrue(matrixResponse.getReachable()[0][0]);
        assertEquals(RoutingAlgorithm.A_STAR, matrixResponse.getAlgorithm());
        assertEquals(HeuristicType.NONE, matrixResponse.getHeuristicType());
    }

    @Test
    @DisplayName("Startup execution profile is required")
    void testExecutionRuntimeConfigIsRequired() {
        RoutingFixtureFactory.Fixture fixture = createFixture();

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixture.edgeGraph())
                        .profileStore(fixture.profileStore())
                        .costEngine(fixture.costEngine())
                        .nodeIdMapper(fixture.nodeIdMapper())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .build()
        );
        assertEquals(RouteCore.REASON_EXECUTION_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Dijkstra startup execution profile governs request execution")
    void testDijkstraStartupProfileGovernRequestExecution() {
        RouteCore core = createCore(ExecutionRuntimeConfig.dijkstra());

        RouteResponse routeResponse = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build());
        MatrixResponse matrixResponse = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build());

        assertTrue(routeResponse.isReachable());
        assertEquals(RoutingAlgorithm.DIJKSTRA, routeResponse.getAlgorithm());
        assertEquals(HeuristicType.NONE, routeResponse.getHeuristicType());
        assertTrue(matrixResponse.getReachable()[0][0]);
        assertEquals(RoutingAlgorithm.DIJKSTRA, matrixResponse.getAlgorithm());
        assertEquals(HeuristicType.NONE, matrixResponse.getHeuristicType());
    }

    private RouteCore createCore(ExecutionRuntimeConfig executionRuntimeConfig) {
        RoutingFixtureFactory.Fixture fixture = createFixture();
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(executionRuntimeConfig)
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private RoutingFixtureFactory.Fixture createFixture() {
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
