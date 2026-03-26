package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Future Route Objective Planner Weakness Finder Tests")
class FutureRouteObjectivePlannerWeaknessFinderTest {
    private static final double OBJECTIVE_TOLERANCE = 1.0e-6d;

    @Test
    @DisplayName("Oracle: planner matches exhaustive expected and robust winners on the aggregate compromise fixture")
    void testPlannerMatchesOracleOnAggregateCompromiseFixture() {
        RouteCore routeCore = createRouteCore(compromiseFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N4", 0L);
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

        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA);
        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90);
    }

    @Test
    @DisplayName("Oracle: planner matches exhaustive winners when expected and robust routes diverge")
    void testPlannerMatchesOracleOnExpectedVsRobustDivergenceFixture() {
        RouteCore routeCore = createRouteCore(alternativeFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N3", 0L);
        List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios = List.of(
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.6d, routeCore.costEngineContract()),
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.4d, costEngineWithUpdates(routeCore, List.of(
                        LiveUpdate.of(2, 0.4f, 10_000L)
                )))
        );

        OracleWinner expectedOracle = oracleWinner(
                routeCore,
                normalized,
                scenarios,
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );
        OracleWinner robustOracle = oracleWinner(
                routeCore,
                normalized,
                scenarios,
                FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90
        );

        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA);
        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90);
        assertTrue(compareOracle(expectedOracle, robustOracle) != 0, "expected and robust oracle winners should differ here");
    }

    @Test
    @DisplayName("Oracle: planner honors arrival and terminal-edge tie-breaks on equal aggregate objectives")
    void testPlannerMatchesOracleOnEqualObjectiveTieBreakFixture() {
        RouteCore routeCore = createRouteCore(tieBreakFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N2", 0L);
        List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios = List.of(
                new FutureRouteObjectivePlanner.ScenarioCostSurface(1.0d, routeCore.costEngineContract())
        );

        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA);
        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90);
    }

    @Test
    @DisplayName("Oracle: dense same-terminal-edge families preserve the best continuation")
    void testPlannerMatchesOracleOnDenseSameTerminalEdgeFamily() {
        RouteCore routeCore = createRouteCore(sameTerminalDenseFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N5", 0L);
        List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios = List.of(
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.4d, routeCore.costEngineContract()),
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.3d, costEngineWithUpdates(routeCore, List.of(
                        LiveUpdate.of(0, 0.5f, 10_000L),
                        LiveUpdate.of(3, 0.5f, 10_000L)
                ))),
                new FutureRouteObjectivePlanner.ScenarioCostSurface(0.3d, costEngineWithUpdates(routeCore, List.of(
                        LiveUpdate.of(2, 0.4f, 10_000L),
                        LiveUpdate.of(5, 0.4f, 10_000L)
                )))
        );

        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA);
        assertPlannerMatchesOracle(routeCore, normalized, scenarios, FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90);
    }

    @Test
    @DisplayName("Budget: frontier overflow reports the deterministic reason code")
    void testFrontierBudgetReasonCode() {
        RouteCore routeCore = createRouteCore(compromiseFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N4", 0L);
        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner(
                SearchBudget.of(SearchBudget.UNBOUNDED, SearchBudget.UNBOUNDED, 1),
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
        assertEquals(SearchBudget.REASON_FRONTIER_EXCEEDED, ex.reasonCode());
    }

    @Test
    @DisplayName("Budget: label overflow reports the deterministic reason code")
    void testLabelBudgetReasonCode() {
        RouteCore routeCore = createRouteCore(compromiseFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N4", 0L);
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
    @DisplayName("Budget: settled-state overflow reports the deterministic reason code")
    void testSettledBudgetReasonCode() {
        RouteCore routeCore = createRouteCore(compromiseFixture());
        RequestNormalizer.NormalizedRouteRequest normalized = normalize(routeCore, "N0", "N4", 0L);
        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner(
                SearchBudget.of(1, SearchBudget.UNBOUNDED, SearchBudget.UNBOUNDED),
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
        assertEquals(SearchBudget.REASON_SETTLED_EXCEEDED, ex.reasonCode());
    }

    private void assertPlannerMatchesOracle(
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios,
            FutureRouteObjectivePlanner.ObjectiveMode mode
    ) {
        FutureRouteObjectivePlanner planner = new FutureRouteObjectivePlanner();
        InternalRoutePlan plan = planner.compute(routeCore, normalized.getInternalRequest(), scenarios, mode);
        OracleWinner oracle = oracleWinner(routeCore, normalized, scenarios, mode);

        assertArrayEquals(oracle.edgePath(), plan.edgePath());
        assertEquals(oracle.maxArrivalTicks(), plan.arrivalTicks());
        assertEquals((float) oracle.objectiveValue(), plan.totalCost(), 0.0001f);
    }

    private OracleWinner oracleWinner(
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios,
            FutureRouteObjectivePlanner.ObjectiveMode mode
    ) {
        List<int[]> paths = enumerateSimplePaths(
                routeCore.edgeGraphContract(),
                normalized.getInternalRequest().sourceNodeId(),
                normalized.getInternalRequest().targetNodeId()
        );
        ArrayList<OracleWinner> candidates = new ArrayList<>(paths.size());
        for (int[] edgePath : paths) {
            candidates.add(evaluatePath(routeCore, normalized, scenarios, mode, edgePath));
        }
        return candidates.stream()
                .min(this::compareOracle)
                .orElseThrow();
    }

    private int compareOracle(OracleWinner lhs, OracleWinner rhs) {
        if (lhs.objectiveValue() + OBJECTIVE_TOLERANCE < rhs.objectiveValue()) {
            return -1;
        }
        if (rhs.objectiveValue() + OBJECTIVE_TOLERANCE < lhs.objectiveValue()) {
            return 1;
        }
        int byArrival = Long.compare(lhs.maxArrivalTicks(), rhs.maxArrivalTicks());
        if (byArrival != 0) {
            return byArrival;
        }
        return Integer.compare(lhs.terminalEdgeId(), rhs.terminalEdgeId());
    }

    private OracleWinner evaluatePath(
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarios,
            FutureRouteObjectivePlanner.ObjectiveMode mode,
            int[] edgePath
    ) {
        ArrayList<PathSample> samples = new ArrayList<>(scenarios.size());
        for (FutureRouteObjectivePlanner.ScenarioCostSurface scenario : scenarios) {
            try {
                PathEvaluator.Evaluation evaluation = new PathEvaluator().evaluateEdgePath(
                        scenario.costEngine(),
                        edgePath,
                        normalized.getInternalRequest().departureTicks(),
                        normalized.getInternalRequest().temporalContext(),
                        normalized.getInternalRequest().transitionContext()
                );
                samples.add(new PathSample(scenario.probability(), evaluation.totalCost(), evaluation.arrivalTicks(), true));
            } catch (PathEvaluator.PathEvaluationException ex) {
                samples.add(new PathSample(scenario.probability(), Float.POSITIVE_INFINITY, Long.MAX_VALUE, false));
            }
        }
        return new OracleWinner(
                edgePath,
                objectiveValue(samples, mode),
                maxArrival(samples),
                edgePath[edgePath.length - 1]
        );
    }

    private double objectiveValue(
            List<PathSample> samples,
            FutureRouteObjectivePlanner.ObjectiveMode mode
    ) {
        return switch (mode) {
            case EXPECTED_ETA -> expectedValue(samples);
            case ROBUST_P90 -> percentileValue(samples, 0.90d);
        };
    }

    private double expectedValue(List<PathSample> samples) {
        double weighted = 0.0d;
        for (PathSample sample : samples) {
            if (!Float.isFinite(sample.cost()) && sample.probability() > 0.0d) {
                return Double.POSITIVE_INFINITY;
            }
            weighted += sample.probability() * sample.cost();
        }
        return weighted;
    }

    private double percentileValue(List<PathSample> samples, double percentile) {
        ArrayList<PathSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(PathSample::cost));

        double totalProbability = 0.0d;
        for (PathSample sample : sorted) {
            totalProbability += sample.probability();
        }
        double threshold = percentile * totalProbability;
        double cumulative = 0.0d;
        for (PathSample sample : sorted) {
            cumulative += sample.probability();
            if (cumulative + OBJECTIVE_TOLERANCE >= threshold) {
                return sample.cost();
            }
        }
        return sorted.get(sorted.size() - 1).cost();
    }

    private long maxArrival(List<PathSample> samples) {
        long max = 0L;
        for (PathSample sample : samples) {
            if (!sample.reachable()) {
                return Long.MAX_VALUE;
            }
            max = Math.max(max, sample.arrivalTicks());
        }
        return max;
    }

    private List<int[]> enumerateSimplePaths(EdgeGraph edgeGraph, int sourceNodeId, int targetNodeId) {
        ArrayList<int[]> paths = new ArrayList<>();
        boolean[] visitedNodes = new boolean[edgeGraph.nodeCount()];
        visitedNodes[sourceNodeId] = true;
        dfs(edgeGraph, sourceNodeId, targetNodeId, edgeGraph.nodeCount(), visitedNodes, new IntArrayList(), paths);
        assertTrue(!paths.isEmpty(), "oracle fixtures should expose at least one simple path");
        return List.copyOf(paths);
    }

    private void dfs(
            EdgeGraph edgeGraph,
            int currentNodeId,
            int targetNodeId,
            int maxDepth,
            boolean[] visitedNodes,
            IntArrayList currentPath,
            List<int[]> paths
    ) {
        if (currentPath.size() >= maxDepth) {
            return;
        }
        EdgeGraph.EdgeIterator iterator = edgeGraph.iterator().resetForNode(currentNodeId);
        while (iterator.hasNext()) {
            int edgeId = iterator.next();
            int nextNodeId = edgeGraph.getEdgeDestination(edgeId);
            if (visitedNodes[nextNodeId]) {
                continue;
            }
            currentPath.add(edgeId);
            if (nextNodeId == targetNodeId) {
                paths.add(currentPath.toIntArray());
            } else {
                visitedNodes[nextNodeId] = true;
                dfs(edgeGraph, nextNodeId, targetNodeId, maxDepth, visitedNodes, currentPath, paths);
                visitedNodes[nextNodeId] = false;
            }
            currentPath.removeInt(currentPath.size() - 1);
        }
    }

    private RequestNormalizer.NormalizedRouteRequest normalize(
            RouteCore routeCore,
            String sourceExternalId,
            String targetExternalId,
            long departureTicks
    ) {
        return routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build()
        );
    }

    private CostEngine costEngineWithUpdates(RouteCore routeCore, List<LiveUpdate> updates) {
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

    private RouteCore createRouteCore(RoutingFixtureFactory.Fixture fixture) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private RoutingFixtureFactory.Fixture alternativeFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        1.0d, 1.0d,
                        2.0d, 0.5d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture compromiseFixture() {
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

    private RoutingFixtureFactory.Fixture tieBreakFixture() {
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

    private RoutingFixtureFactory.Fixture sameTerminalDenseFixture() {
        return RoutingFixtureFactory.createFixture(
                6,
                new int[]{0, 3, 4, 5, 6, 7, 7},
                new int[]{1, 2, 3, 4, 4, 4, 5},
                new int[]{0, 0, 0, 1, 2, 3, 4},
                new float[]{1.5f, 2.0f, 2.5f, 1.5f, 1.0f, 0.8f, 1.0f},
                new int[]{1, 1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, 0.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d,
                        3.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private record PathSample(double probability, float cost, long arrivalTicks, boolean reachable) {
    }

    private record OracleWinner(int[] edgePath, double objectiveValue, long maxArrivalTicks, int terminalEdgeId) {
    }
}
