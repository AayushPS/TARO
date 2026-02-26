package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.testutil.TemporalTestContexts;
import org.Aayush.routing.testutil.TransitionTestContexts;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 14 NativeOneToManyMatrixPlanner Tests")
class NativeOneToManyMatrixPlannerTest {

    @Test
    @DisplayName("Single-target matrix cell equals route query for Dijkstra")
    void testSingleTargetEquivalenceWithRouteQuery() {
        RouteCore core = createCore(createLinearFixture());
        RouteResponse route = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());

        MatrixResponse matrix = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(route.isReachable());
        assertTrue(matrix.getReachable()[0][0]);
        assertEquals(route.getTotalCost(), matrix.getTotalCosts()[0][0], 1e-6f);
        assertEquals(route.getArrivalTicks(), matrix.getArrivalTicks()[0][0]);
        assertEquals(NativeOneToManyMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE, matrix.getImplementationNote());
    }

    @Test
    @DisplayName("Matrix canonicalizes unreachable targets and keeps duplicate target cells identical")
    void testUnreachableAndDuplicateTargets() {
        RouteCore core = createCore(createDisconnectedFixture());
        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N1")
                .targetExternalId("N3")
                .targetExternalId("N3")
                .departureTicks(7L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(response.getReachable()[0][0]);
        assertEquals(1.0f, response.getTotalCosts()[0][0], 1e-6f);
        assertEquals(8L, response.getArrivalTicks()[0][0]);

        assertFalse(response.getReachable()[0][1]);
        assertEquals(Float.POSITIVE_INFINITY, response.getTotalCosts()[0][1]);
        assertEquals(7L, response.getArrivalTicks()[0][1]);

        assertEquals(response.getReachable()[0][1], response.getReachable()[0][2]);
        assertEquals(response.getTotalCosts()[0][1], response.getTotalCosts()[0][2]);
        assertEquals(response.getArrivalTicks()[0][1], response.getArrivalTicks()[0][2]);
    }

    @Test
    @DisplayName("Repeated identical matrix requests are deterministic on equal-cost plateaus")
    void testDeterministicRepeatability() {
        RouteCore core = createCore(createDiamondFixture());
        MatrixRequest request = MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N3")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();

        MatrixResponse first = core.matrix(request);
        MatrixResponse second = core.matrix(request);

        assertArrayEquals(first.getReachable(), second.getReachable());
        assertArrayEquals(first.getTotalCosts(), second.getTotalCosts());
        assertArrayEquals(first.getArrivalTicks(), second.getArrivalTicks());
    }

    @Test
    @DisplayName("A* matrix requests use native one-to-many path for bounded target sets")
    void testAStarNativePath() {
        RouteCore core = createCore(createLinearFixture());
        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(response.getReachable()[0][0]);
        assertEquals(4.0f, response.getTotalCosts()[0][0], 1e-6f);
        assertEquals(4L, response.getArrivalTicks()[0][0]);
        assertEquals(NativeOneToManyMatrixPlanner.NATIVE_A_STAR_IMPLEMENTATION_NOTE, response.getImplementationNote());
    }

    @Test
    @DisplayName("A* oversized target set falls back to bounded batched compatibility mode")
    void testAStarBatchedFallbackForLargeTargetSet() {
        RouteCore core = createCore(createLinearFixture());
        NativeOneToManyMatrixPlanner planner = new NativeOneToManyMatrixPlanner(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.of(0, 0, 0, 0),
                TerminationPolicy.defaults(),
                1,
                1
        );

        MatrixPlan plan = planner.compute(
                core,
                new InternalMatrixRequest(
                        new int[]{0},
                        new int[]{3, 4},
                        0L,
                        RoutingAlgorithm.A_STAR,
                        HeuristicType.NONE,
                        TemporalTestContexts.calendarUtc(),
                        TransitionTestContexts.edgeBased()
                )
        );

        assertEquals(NativeOneToManyMatrixPlanner.BATCHED_A_STAR_COMPATIBILITY_NOTE, plan.implementationNote());
        assertTrue(plan.reachable()[0][0]);
        assertTrue(plan.reachable()[0][1]);
        assertEquals(3.0f, plan.totalCosts()[0][0], 1e-6f);
        assertEquals(4.0f, plan.totalCosts()[0][1], 1e-6f);
    }

    @Test
    @DisplayName("Row terminates immediately when source already satisfies all unique targets")
    void testSourceTargetEarlyTerminationWithTightBudget() {
        RoutingFixtureFactory.Fixture fixture = createTwoBranchFixture();
        RouteCore core = createCore(fixture);
        NativeOneToManyMatrixPlanner planner = new NativeOneToManyMatrixPlanner(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.of(1, 1, 1, 1),
                TerminationPolicy.defaults()
        );

        MatrixPlan plan = planner.compute(
                core,
                new InternalMatrixRequest(
                        new int[]{0},
                        new int[]{0},
                        5L,
                        RoutingAlgorithm.DIJKSTRA,
                        HeuristicType.NONE,
                        TemporalTestContexts.calendarUtc(),
                        TransitionTestContexts.edgeBased()
                )
        );

        assertTrue(plan.reachable()[0][0]);
        assertEquals(0.0f, plan.totalCosts()[0][0], 1e-6f);
        assertEquals(5L, plan.arrivalTicks()[0][0]);
    }

    @Test
    @DisplayName("Thread-local matrix query context does not retain graph-scale active edge containers across subsequent small requests")
    void testThreadLocalMatrixContextReleasesActiveEdgeContainersAcrossRequests() {
        RoutingFixtureFactory.Fixture fixture = createLongLinearFixture(200);
        RouteCore core = createCore(fixture);
        NativeOneToManyMatrixPlanner planner = new NativeOneToManyMatrixPlanner(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.of(0, 0, 0, 0),
                TerminationPolicy.defaults(),
                512,
                32
        );

        planner.compute(
                core,
                new InternalMatrixRequest(
                        new int[]{0},
                        new int[]{199},
                        0L,
                        RoutingAlgorithm.DIJKSTRA,
                        HeuristicType.NONE,
                        TemporalTestContexts.calendarUtc(),
                        TransitionTestContexts.edgeBased()
                )
        );

        planner.compute(
                core,
                new InternalMatrixRequest(
                        new int[]{0},
                        new int[]{1},
                        0L,
                        RoutingAlgorithm.DIJKSTRA,
                        HeuristicType.NONE,
                        TemporalTestContexts.calendarUtc(),
                        TransitionTestContexts.edgeBased()
                )
        );

        MatrixQueryContext context = queryContextOf(planner);
        assertTrue(
                activeEdgeContainerCount(context) <= 8,
                "active edge containers should shrink after reset for small follow-up matrix requests"
        );
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.defaultRuntime())
                .addressingRuntimeConfig(org.Aayush.routing.traits.addressing.AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private RoutingFixtureFactory.Fixture createLinearFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        2.0, 0.0,
                        3.0, 0.0,
                        4.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createDisconnectedFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 1, 1, 2, 2},
                new int[]{1, 3},
                new int[]{0, 2},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        10.0, 0.0,
                        11.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createDiamondFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 2, 4, 6, 7, 7},
                new int[]{1, 2, 3, 4, 3, 4, 4},
                new int[]{0, 0, 1, 1, 2, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, -1.0,
                        1.0, 1.0,
                        2.0, -1.0,
                        2.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createTwoBranchFixture() {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 2, 2, 2},
                new int[]{1, 2},
                new int[]{0, 0},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createLongLinearFixture(int nodes) {
        int edgeCount = nodes - 1;
        int[] firstEdge = new int[nodes + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfiles = new int[edgeCount];
        double[] coordinates = new double[nodes * 2];
        for (int node = 0; node < nodes; node++) {
            firstEdge[node] = Math.min(node, edgeCount);
            coordinates[node * 2] = node;
            coordinates[node * 2 + 1] = 0.0d;
            if (node < edgeCount) {
                edgeTarget[node] = node + 1;
                edgeOrigin[node] = node;
                baseWeights[node] = 1.0f;
                edgeProfiles[node] = 1;
            }
        }
        firstEdge[nodes] = edgeCount;

        return RoutingFixtureFactory.createFixture(
                nodes,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfiles,
                coordinates,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private MatrixQueryContext queryContextOf(NativeOneToManyMatrixPlanner planner) {
        try {
            Field field = NativeOneToManyMatrixPlanner.class.getDeclaredField("queryContext");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<MatrixQueryContext> threadLocal = (ThreadLocal<MatrixQueryContext>) field.get(planner);
            return threadLocal.get();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to inspect native matrix planner queryContext", ex);
        }
    }

    private int activeEdgeContainerCount(MatrixQueryContext context) {
        try {
            Field field = MatrixQueryContext.class.getDeclaredField("activeLabelsByEdge");
            field.setAccessible(true);
            Object map = field.get(context);
            return (Integer) map.getClass().getMethod("size").invoke(map);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to inspect MatrixQueryContext activeLabelsByEdge", ex);
        }
    }
}
