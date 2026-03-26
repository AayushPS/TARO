package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.RouteShape;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Future Route Objective Planner Tests")
class FutureRouteObjectivePlannerTest {

    @Test
    @DisplayName("Source equals target returns an immediate zero-cost plan")
    void testSourceEqualsTargetReturnsImmediatePlan() {
        RoutingFixtureFactory.Fixture fixture = simpleFixture();
        RouteCore routeCore = createRouteCore(fixture, ExecutionRuntimeConfig.dijkstra());
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N0")
                        .departureTicks(7L)
                        .build()
        );

        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner();
        InternalRoutePlan plan = planner.compute(
                routeCore,
                normalized.getInternalRequest(),
                List.of(new FutureRouteObjectivePlanner.ScenarioCostSurface(1.0d, routeCore.costEngineContract())),
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );

        assertTrue(plan.reachable());
        assertEquals(0.0f, plan.totalCost());
        assertEquals(7L, plan.arrivalTicks());
        assertEquals(0, plan.settledStates());
        assertArrayEquals(new int[]{0}, plan.nodePath());
        assertArrayEquals(new int[0], plan.edgePath());
    }

    @Test
    @DisplayName("Unreachable targets return the canonical unreachable plan")
    void testUnreachableTargetReturnsCanonicalPlan() {
        RoutingFixtureFactory.Fixture fixture = disconnectedFixture();
        RouteCore routeCore = createRouteCore(fixture, ExecutionRuntimeConfig.dijkstra());
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(11L)
                        .build()
        );

        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner();
        InternalRoutePlan plan = planner.compute(
                routeCore,
                normalized.getInternalRequest(),
                List.of(new FutureRouteObjectivePlanner.ScenarioCostSurface(1.0d, routeCore.costEngineContract())),
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );

        assertFalse(plan.reachable());
        assertEquals(Float.POSITIVE_INFINITY, plan.totalCost());
        assertEquals(11L, plan.arrivalTicks());
        assertArrayEquals(new int[0], plan.nodePath());
        assertArrayEquals(new int[0], plan.edgePath());
    }

    @Test
    @DisplayName("A-star objective planning still selects the aggregate compromise route")
    void testAStarObjectivePlanningFindsCompromiseRoute() {
        RoutingFixtureFactory.Fixture fixture = compromiseFixture();
        RouteCore routeCore = createRouteCore(fixture, ExecutionRuntimeConfig.aStar(HeuristicType.EUCLIDEAN));
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .build()
        );

        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner();
        List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios = List.of(
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.5d, costEngineWithUpdates(routeCore, List.of(
                        LiveUpdate.of(1, 0.1f, 10_000L),
                        LiveUpdate.of(4, 0.1f, 10_000L)
                ))),
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.5d, costEngineWithUpdates(routeCore, List.of(
                        LiveUpdate.of(0, 0.1f, 10_000L),
                        LiveUpdate.of(3, 0.1f, 10_000L)
                )))
        );

        InternalRoutePlan expectedPlan = planner.compute(
                routeCore,
                normalized.getInternalRequest(),
                scenarios,
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );
        InternalRoutePlan robustPlan = planner.compute(
                routeCore,
                normalized.getInternalRequest(),
                scenarios,
                FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90
        );

        RouteShape expectedShape = RouteShape.fromRouteResponse(routeCore.buildRouteResponse(normalized, expectedPlan));
        RouteShape robustShape = RouteShape.fromRouteResponse(routeCore.buildRouteResponse(normalized, robustPlan));
        assertEquals(List.of("N0", "N3", "N4"), expectedShape.getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), robustShape.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Equal aggregate objectives break ties by lower arrival then terminal edge")
    void testEqualAggregateObjectivesPreferLowerArrival() {
        RoutingFixtureFactory.Fixture fixture = tieBreakFixture();
        RouteCore routeCore = createRouteCore(fixture, ExecutionRuntimeConfig.dijkstra());
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(0L)
                        .build()
        );

        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner();
        InternalRoutePlan plan = planner.compute(
                routeCore,
                normalized.getInternalRequest(),
                List.of(new FutureRouteObjectivePlanner.ScenarioCostSurface(1.0d, routeCore.costEngineContract())),
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );

        RouteShape shape = RouteShape.fromRouteResponse(routeCore.buildRouteResponse(normalized, plan));
        assertEquals(List.of("N0", "N2"), shape.getPathExternalNodeIds());
        assertEquals(3L, plan.arrivalTicks());
        assertArrayEquals(new int[]{1}, plan.edgePath());
    }

    @Test
    @DisplayName("Budget overflow surfaces the deterministic label reason code")
    void testBudgetOverflowReportsLabelReasonCode() {
        RoutingFixtureFactory.Fixture fixture = compromiseFixture();
        RouteCore routeCore = createRouteCore(fixture, ExecutionRuntimeConfig.dijkstra());
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .build()
        );

        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner(
                SearchBudget.of(SearchBudget.UNBOUNDED, 1, SearchBudget.UNBOUNDED),
                new PathEvaluator()
        );

        SearchBudget.BudgetExceededException ex = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> planner.compute(
                        routeCore,
                        normalized.getInternalRequest(),
                        List.of(new FutureRouteObjectivePlanner.ScenarioCostSurface(1.0d, routeCore.costEngineContract())),
                        FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
                )
        );

        assertEquals(SearchBudget.REASON_LABEL_EXCEEDED, ex.reasonCode());
    }

    @Test
    @DisplayName("Planner utility guards handle heuristic and numeric edge cases")
    void testPlannerUtilityGuards() throws Exception {
        Method priority = FutureRouteObjectivePlanner.class.getDeclaredMethod(
                "priority",
                double.class,
                org.Aayush.routing.heuristic.GoalBoundHeuristic.class,
                int.class
        );
        priority.setAccessible(true);
        double nanPriority = (double) priority.invoke(null, 12.0d, (org.Aayush.routing.heuristic.GoalBoundHeuristic) nodeId -> Double.NaN, 0);
        double negativePriority = (double) priority.invoke(null, 12.0d, (org.Aayush.routing.heuristic.GoalBoundHeuristic) nodeId -> -5.0d, 0);
        assertEquals(12.0d, nanPriority);
        assertEquals(12.0d, negativePriority);

        Method dominates = FutureRouteObjectivePlanner.class.getDeclaredMethod(
                "dominates",
                float[].class,
                long[].class,
                float[].class,
                long[].class
        );
        dominates.setAccessible(true);
        assertTrue((boolean) dominates.invoke(null,
                new float[]{10.0f, 12.0f},
                new long[]{10L, 12L},
                new float[]{10.0f, 15.0f},
                new long[]{10L, 15L}
        ));
        assertFalse((boolean) dominates.invoke(null,
                new float[]{10.0f, 16.0f},
                new long[]{10L, 16L},
                new float[]{10.0f, 15.0f},
                new long[]{10L, 15L}
        ));

        Method isBetterGoal = FutureRouteObjectivePlanner.class.getDeclaredMethod(
                "isBetterGoal",
                double.class,
                long.class,
                int.class,
                double.class,
                long.class,
                int.class
        );
        isBetterGoal.setAccessible(true);
        assertTrue((boolean) isBetterGoal.invoke(null, 10.0d, 20L, 1, 10.0d, 25L, 2));
        assertTrue((boolean) isBetterGoal.invoke(null, 10.0d, 20L, 1, 10.0d, 20L, 2));
        assertFalse((boolean) isBetterGoal.invoke(null, 12.0d, 20L, 1, 10.0d, 20L, 2));

        Method toArrivalTicks = FutureRouteObjectivePlanner.class.getDeclaredMethod("toArrivalTicks", float.class);
        toArrivalTicks.setAccessible(true);
        assertEquals(0L, (long) toArrivalTicks.invoke(null, 0.0f));
        assertEquals(0L, (long) toArrivalTicks.invoke(null, Float.POSITIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, (long) toArrivalTicks.invoke(null, Float.MAX_VALUE));

        Method saturatingAdd = FutureRouteObjectivePlanner.class.getDeclaredMethod("saturatingAdd", long.class, long.class);
        saturatingAdd.setAccessible(true);
        assertEquals(Long.MAX_VALUE, (long) saturatingAdd.invoke(null, Long.MAX_VALUE - 2L, 10L));
        assertEquals(15L, (long) saturatingAdd.invoke(null, 10L, 5L));

        Method buildEdgePath = FutureRouteObjectivePlanner.class.getDeclaredMethod(
                "buildEdgePath",
                Class.forName("org.Aayush.routing.core.FutureRouteObjectivePlanner$AggregateLabelStore"),
                int.class
        );
        buildEdgePath.setAccessible(true);
        Class<?> labelStoreClass = Class.forName("org.Aayush.routing.core.FutureRouteObjectivePlanner$AggregateLabelStore");
        var constructor = labelStoreClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object labelStore = constructor.newInstance();
        Method add = labelStoreClass.getDeclaredMethod(
                "add",
                int.class,
                float[].class,
                long[].class,
                double.class,
                int.class
        );
        add.setAccessible(true);
        int firstLabel = (int) add.invoke(labelStore, 3, new float[]{3.0f}, new long[]{3L}, 3.0d, -1);
        int secondLabel = (int) add.invoke(labelStore, 5, new float[]{5.0f}, new long[]{5L}, 5.0d, firstLabel);
        int[] edgePath = (int[]) buildEdgePath.invoke(null, labelStore, secondLabel);
        assertArrayEquals(new int[]{3, 5}, edgePath);
        assertNotNull(labelStore);
    }

    private static CostEngine costEngineWithUpdates(RouteCore routeCore, List<LiveUpdate> updates) {
        CostEngine base = routeCore.costEngineContract();
        LiveOverlay overlay = base.liveOverlay().copyActiveSnapshot(0L);
        for (LiveUpdate update : updates) {
            overlay.upsert(update, 0L);
        }
        return new CostEngine(
                base.edgeGraph(),
                base.profileStore(),
                overlay,
                base.turnCostMap(),
                base.engineTimeUnit(),
                base.bucketSizeSeconds(),
                base.temporalSamplingPolicy()
        );
    }

    private static RouteCore createRouteCore(RoutingFixtureFactory.Fixture fixture, ExecutionRuntimeConfig executionRuntimeConfig) {
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

    private static RoutingFixtureFactory.Fixture simpleFixture() {
        return RoutingFixtureFactory.createFixture(
                2,
                new int[]{0, 1, 1},
                new int[]{1},
                new int[]{0},
                new float[]{1.0f},
                new int[]{1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private static RoutingFixtureFactory.Fixture disconnectedFixture() {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 1, 1},
                new int[]{1},
                new int[]{0},
                new float[]{1.0f},
                new int[]{1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private static RoutingFixtureFactory.Fixture compromiseFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 3, 4, 5, 6, 6},
                new int[]{1, 2, 3, 4, 4, 4},
                new int[]{0, 0, 0, 1, 2, 3},
                new float[]{5.0f, 5.0f, 20.0f, 5.0f, 5.0f, 20.0f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, 0.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private static RoutingFixtureFactory.Fixture tieBreakFixture() {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 2, 3, 3},
                new int[]{1, 2, 2},
                new int[]{0, 0, 1},
                new float[]{1.1f, 3.0f, 1.9f},
                new int[]{1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d
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
