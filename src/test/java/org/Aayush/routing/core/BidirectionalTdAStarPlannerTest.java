package org.Aayush.routing.core;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 13 Bidirectional TD-A* Planner Tests")
class BidirectionalTdAStarPlannerTest {
    private static final GoalBoundHeuristic ZERO_HEURISTIC = nodeId -> 0.0d;

    @Test
    @DisplayName("Bidirectional A* keeps cost parity with edge-based Dijkstra on deterministic fixture")
    void testParityWithDijkstra() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();

        RoutePlanner dijkstraPlanner = new EdgeBasedRoutePlanner(false);
        RoutePlanner bidirectionalPlanner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                10L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan dijkstraPlan = dijkstraPlanner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                request
        );
        InternalRoutePlan bidirectionalPlan = bidirectionalPlanner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                request
        );

        assertTrue(dijkstraPlan.reachable());
        assertTrue(bidirectionalPlan.reachable());
        assertEquals(dijkstraPlan.totalCost(), bidirectionalPlan.totalCost(), 1e-6f);
    }

    @Test
    @DisplayName("Budget fail-fast is deterministic for frontier overrun")
    void testFrontierBudgetExceeded() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(SearchBudget.UNBOUNDED, SearchBudget.UNBOUNDED, 1),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        SearchBudget.BudgetExceededException ex = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> planner.compute(fixture.edgeGraph(), fixture.costEngine(), ZERO_HEURISTIC, request)
        );
        assertEquals(SearchBudget.REASON_FRONTIER_EXCEEDED, ex.reasonCode());
    }

    @Test
    @DisplayName("Settled-state budget accounts for all frontier work including stale labels")
    void testSettledBudgetCountsAllFrontierWork() {
        RoutingFixtureFactory.Fixture fixture = createDominanceStaleFixture();
        CostEngine discreteCostEngine = createDiscreteCostEngine(fixture);
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                discreteCostEngine,
                SearchBudget.of(10, SearchBudget.UNBOUNDED, SearchBudget.UNBOUNDED),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                3_590L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        SearchBudget.BudgetExceededException ex = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> planner.compute(fixture.edgeGraph(), discreteCostEngine, ZERO_HEURISTIC, request)
        );
        assertEquals(SearchBudget.REASON_SETTLED_EXCEEDED, ex.reasonCode());
    }

    @Test
    @DisplayName("Budget fail-fast is deterministic for label overrun")
    void testLabelBudgetExceeded() {
        RoutingFixtureFactory.Fixture fixture = createDominanceStaleFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(SearchBudget.UNBOUNDED, 1, SearchBudget.UNBOUNDED),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        SearchBudget.BudgetExceededException ex = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> planner.compute(fixture.edgeGraph(), fixture.costEngine(), ZERO_HEURISTIC, request)
        );
        assertEquals(SearchBudget.REASON_LABEL_EXCEEDED, ex.reasonCode());
    }

    @Test
    @DisplayName("Planner rejects same-sized but different runtime graph instances")
    void testGraphContractMismatch() {
        RoutingFixtureFactory.Fixture fixtureA = createLinearFixture();
        RoutingFixtureFactory.Fixture fixtureB = createLinearVariantFixture();

        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixtureA.edgeGraph(),
                fixtureA.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> planner.compute(fixtureB.edgeGraph(), fixtureB.costEngine(), ZERO_HEURISTIC, request)
        );
        assertTrue(ex.getMessage().contains("H13_GRAPH_CONTRACT_MISMATCH"));
    }

    @Test
    @DisplayName("Planner rejects mismatched profile instance even when graph is identical")
    void testProfileContractMismatch() {
        RoutingFixtureFactory.Fixture fixtureA = createLinearFixture();
        RoutingFixtureFactory.Fixture profileFixture = RoutingFixtureFactory.createFixture(
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
                        new float[]{2.0f},
                        1.0f
                )
        );
        CostEngine mismatchedCostEngine = new CostEngine(
                fixtureA.edgeGraph(),
                profileFixture.profileStore(),
                new LiveOverlay(32),
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );

        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixtureA.edgeGraph(),
                fixtureA.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> planner.compute(fixtureA.edgeGraph(), mismatchedCostEngine, ZERO_HEURISTIC, request)
        );
        assertTrue(ex.getMessage().contains("H13_PROFILE_CONTRACT_MISMATCH"));
    }

    @Test
    @DisplayName("Dense equal-cost plateaus keep deterministic cost parity with Dijkstra")
    void testDenseEqualCostPlateauParity() {
        RoutingFixtureFactory.Fixture fixture = createEqualCostPlateauFixture();
        RoutePlanner dijkstraPlanner = new EdgeBasedRoutePlanner(false);
        RoutePlanner bidirectionalPlanner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan dijkstraPlan = dijkstraPlanner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                request
        );
        InternalRoutePlan bidirectionalPlan = bidirectionalPlanner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                request
        );

        assertTrue(dijkstraPlan.reachable());
        assertTrue(bidirectionalPlan.reachable());
        assertEquals(dijkstraPlan.totalCost(), bidirectionalPlan.totalCost(), 1e-6f);
    }

    @Test
    @DisplayName("Turn-cost-heavy transitions preserve exact optimal route selection")
    void testTurnCostHeavyTransitions() {
        RoutingFixtureFactory.Fixture fixture = createTurnHeavyFixture();
        CostEngine turnAwareCostEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                new LiveOverlay(16),
                createTurnCostMap(
                        new TurnCostSpec(0, 2, 100.0f),
                        new TurnCostSpec(1, 3, 0.0f)
                ),
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.INTERPOLATED
        );
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                turnAwareCostEngine,
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                3,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan plan = planner.compute(fixture.edgeGraph(), turnAwareCostEngine, ZERO_HEURISTIC, request);
        assertTrue(plan.reachable());
        assertEquals(2.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 2, 3}, plan.nodePath());
    }

    @Test
    @DisplayName("Planner reacts deterministically to live overlay churn and expiry")
    void testLiveOverlayChurn() {
        RoutingFixtureFactory.Fixture fixture = createLiveOverlayFixture();
        LiveOverlay overlay = new LiveOverlay(16);
        CostEngine costEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                overlay,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                costEngine,
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        InternalRouteRequest requestAtZero = new InternalRouteRequest(
                0,
                3,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );
        InternalRouteRequest requestAfterExpiry = new InternalRouteRequest(
                0,
                3,
                11L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan baseline = planner.compute(fixture.edgeGraph(), costEngine, ZERO_HEURISTIC, requestAtZero);
        assertTrue(baseline.reachable());
        assertEquals(2.0f, baseline.totalCost(), 1e-6f);

        overlay.upsert(LiveUpdate.of(0, 0.0f, 10L), 0L);
        InternalRoutePlan blocked = planner.compute(fixture.edgeGraph(), costEngine, ZERO_HEURISTIC, requestAtZero);
        assertTrue(blocked.reachable());
        assertEquals(4.0f, blocked.totalCost(), 1e-6f);

        InternalRoutePlan recovered = planner.compute(fixture.edgeGraph(), costEngine, ZERO_HEURISTIC, requestAfterExpiry);
        assertTrue(recovered.reachable());
        assertEquals(2.0f, recovered.totalCost(), 1e-6f);
    }

    @Test
    @DisplayName("Near-overflow departure ticks saturate arrival safely")
    void testNearOverflowDepartureTicks() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                Long.MAX_VALUE - 2L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan plan = planner.compute(fixture.edgeGraph(), fixture.costEngine(), ZERO_HEURISTIC, request);
        assertTrue(plan.reachable());
        assertEquals(4.0f, plan.totalCost(), 1e-6f);
        assertEquals(Long.MAX_VALUE, plan.arrivalTicks());
    }

    @Test
    @DisplayName("Non-finite and negative heuristic outputs are sanitized without changing optimal result")
    void testHeuristicSanitization() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        InternalRouteRequest request = new InternalRouteRequest(
                0,
                4,
                0L,
                RoutingAlgorithm.A_STAR,
                HeuristicType.NONE
        );

        InternalRoutePlan baseline = planner.compute(fixture.edgeGraph(), fixture.costEngine(), ZERO_HEURISTIC, request);
        List<GoalBoundHeuristic> pathologicalHeuristics = List.of(
                nodeId -> Double.NaN,
                nodeId -> Double.NEGATIVE_INFINITY,
                nodeId -> -42.0d,
                nodeId -> Double.POSITIVE_INFINITY
        );

        for (GoalBoundHeuristic heuristic : pathologicalHeuristics) {
            InternalRoutePlan plan = planner.compute(fixture.edgeGraph(), fixture.costEngine(), heuristic, request);
            assertTrue(plan.reachable());
            assertEquals(baseline.totalCost(), plan.totalCost(), 1e-6f);
            assertEquals(baseline.arrivalTicks(), plan.arrivalTicks());
            assertArrayEquals(baseline.nodePath(), plan.nodePath());
        }
    }

    @Test
    @DisplayName("Forward expansion skips blocked transitions and still finds alternate optimal route")
    void testForwardExpansionSkipsNonFiniteTransitionCost() {
        RoutingFixtureFactory.Fixture fixture = createLiveOverlayFixture();
        LiveOverlay overlay = new LiveOverlay(16);
        CostEngine costEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                overlay,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                costEngine,
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        overlay.upsert(LiveUpdate.of(2, 0.0f, 100L), 0L);

        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                costEngine,
                ZERO_HEURISTIC,
                new InternalRouteRequest(0, 3, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertTrue(plan.reachable());
        assertEquals(4.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 2, 3}, plan.nodePath());
    }

    @Test
    @DisplayName("Goal upper-bound pruning skips expansions that cannot improve current best goal")
    void testGoalUpperBoundPruning() {
        RoutingFixtureFactory.Fixture fixture = createGoalPruningFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                new InternalRouteRequest(0, 4, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertTrue(plan.reachable());
        assertEquals(5.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 1, 4}, plan.nodePath());
    }

    @Test
    @DisplayName("Dominated successor labels on the same edge are rejected deterministically")
    void testDominatedSuccessorLabelRejected() {
        RoutingFixtureFactory.Fixture fixture = createDominatedSuccessorFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        GoalBoundHeuristic biasedHeuristic = nodeId -> nodeId == 3 ? 10.0d : 0.0d;

        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                biasedHeuristic,
                new InternalRouteRequest(0, 3, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertTrue(plan.reachable());
        assertEquals(2.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 1, 3}, plan.nodePath());
    }

    @Test
    @DisplayName("Overflowing successor cumulative cost is ignored while finite route remains discoverable")
    void testOverflowingSuccessorCostSkipped() {
        RoutingFixtureFactory.Fixture fixture = createOverflowNextCostFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        GoalBoundHeuristic heuristic = nodeId -> nodeId == 1 ? 3.0e38d : 0.0d;

        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                heuristic,
                new InternalRouteRequest(0, 3, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertTrue(plan.reachable());
        assertEquals(2.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 1, 3}, plan.nodePath());
    }

    @Test
    @DisplayName("Priority-based successor pruning engages once a finite best-goal upper bound exists")
    void testPriorityBasedSuccessorPruningAfterGoalFound() {
        RoutingFixtureFactory.Fixture fixture = createPriorityPruneFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        GoalBoundHeuristic heuristic = nodeId -> nodeId == 5 ? 10.0d : 0.0d;

        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                heuristic,
                new InternalRouteRequest(0, 4, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertTrue(plan.reachable());
        assertEquals(5.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 1, 4}, plan.nodePath());
    }

    @Test
    @DisplayName("Constructor fails fast when cost-engine graph contract mismatches planner graph")
    void testConstructorGraphContractMismatchFailsFast() {
        RoutingFixtureFactory.Fixture fixtureA = createLinearFixture();
        RoutingFixtureFactory.Fixture fixtureB = createLinearVariantFixture();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new BidirectionalTdAStarPlanner(
                        fixtureA.edgeGraph(),
                        fixtureB.costEngine(),
                        SearchBudget.of(10_000, 10_000, 10_000),
                        TerminationPolicy.defaults(),
                        new PathEvaluator()
                )
        );

        assertTrue(ex.getMessage().contains("H13_GRAPH_CONTRACT_MISMATCH"));
    }

    @Test
    @DisplayName("Priority-pruned successors do not consume label budget after finite best-goal is known")
    void testPriorityPrunedSuccessorsDoNotConsumeLabelBudget() {
        RoutingFixtureFactory.Fixture fixture = createPriorityPrunedLabelBudgetFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 4, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );
        GoalBoundHeuristic heuristic = nodeId -> {
            if (nodeId == 3) {
                return 0.5d;
            }
            if (nodeId == 5) {
                return 1.5d;
            }
            return 0.0d;
        };

        InternalRoutePlan plan = assertDoesNotThrow(() -> planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                heuristic,
                new InternalRouteRequest(0, 4, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        ));

        assertTrue(plan.reachable());
        assertEquals(5.0f, plan.totalCost(), 1e-6f);
        assertArrayEquals(new int[]{0, 1, 4}, plan.nodePath());
    }

    @Test
    @DisplayName("Reverse expansion skips stale frontier states whose distance no longer matches reverse-best")
    void testReverseExpansionSkipsStaleDistanceEntry() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        PlannerQueryContext context = new PlannerQueryContext();
        context.setReverseBest(2, 1.0f);
        context.backwardFrontier().add(new BackwardFrontierState(2, 2.0f));

        int workStates = invokeExpandBackwardOne(
                planner,
                fixture.edgeGraph(),
                context,
                context.backwardFrontier(),
                context.forwardFrontier(),
                0
        );

        assertEquals(1, workStates);
        assertTrue(context.backwardFrontier().isEmpty());
        assertFalse(context.isReverseSettled(2));
    }

    @Test
    @DisplayName("Reverse expansion ignores non-finite cumulative lower-bound distance and continues safely")
    void testReverseExpansionSkipsOverflowDistance() {
        RoutingFixtureFactory.Fixture fixture = createReverseOverflowFixture();
        BidirectionalTdAStarPlanner planner = new BidirectionalTdAStarPlanner(
                fixture.edgeGraph(),
                fixture.costEngine(),
                SearchBudget.of(10_000, 10_000, 10_000),
                TerminationPolicy.defaults(),
                new PathEvaluator()
        );

        InternalRoutePlan plan = planner.compute(
                fixture.edgeGraph(),
                fixture.costEngine(),
                ZERO_HEURISTIC,
                new InternalRouteRequest(0, 3, 0L, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
        );

        assertFalse(plan.reachable());
        assertEquals(Float.POSITIVE_INFINITY, plan.totalCost());
        assertEquals(0L, plan.arrivalTicks());
    }

    @Test
    @DisplayName("Goal comparison helpers preserve deterministic tie-breaking semantics")
    void testGoalComparisonHelpers() {
        assertTrue(invokeIsBetter(4.0f, 9L, 5.0f, 10L));
        assertTrue(invokeIsBetter(5.0f, 9L, 5.0f, 10L));
        assertFalse(invokeIsBetter(5.0f, 10L, 5.0f, 10L));
        assertFalse(invokeIsBetter(6.0f, 8L, 5.0f, 9L));

        assertTrue(invokeCanImproveGoal(1.0f, 1L, Float.POSITIVE_INFINITY, Long.MAX_VALUE));
        assertTrue(invokeCanImproveGoal(4.0f, 9L, 5.0f, 10L));
        assertTrue(invokeCanImproveGoal(5.0f, 9L, 5.0f, 10L));
        assertFalse(invokeCanImproveGoal(5.0f, 10L, 5.0f, 10L));
        assertFalse(invokeCanImproveGoal(6.0f, 1L, 5.0f, 10L));
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

    private RoutingFixtureFactory.Fixture createLinearVariantFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{2, 3, 4, 1},
                new int[]{0, 2, 3, 4},
                new float[]{2.0f, 2.0f, 2.0f, 2.0f},
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

    private RoutingFixtureFactory.Fixture createDominanceStaleFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 2, 3, 4, 5, 5},
                new int[]{1, 2, 3, 3, 4},
                new int[]{0, 0, 1, 2, 3},
                new float[]{1.0f, 10.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1, 2},
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
                        new float[]{1.0f, 1.0f},
                        1.0f
                ),
                new RoutingFixtureFactory.ProfileSpec(
                        2,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1_000.0f, 1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createEqualCostPlateauFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 3, 4, 5, 6, 6},
                new int[]{1, 2, 3, 4, 4, 4},
                new int[]{0, 0, 0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        1.0, 1.0,
                        1.0, -1.0,
                        2.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createLiveOverlayFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 3.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createTurnHeavyFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createGoalPruningFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 2, 3, 4, 5, 5},
                new int[]{1, 2, 4, 3, 4},
                new int[]{0, 0, 1, 2, 3},
                new float[]{2.0f, 1.0f, 3.0f, 1.0f, 10.0f},
                new int[]{1, 1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0,
                        2.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createDominatedSuccessorFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 1},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 1.0f, 5.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createOverflowNextCostFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.5e38f, 1.0f, 2.5e38f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createPriorityPruneFixture() {
        return RoutingFixtureFactory.createFixture(
                6,
                new int[]{0, 2, 3, 4, 6, 6, 6},
                new int[]{1, 2, 4, 3, 4, 5},
                new int[]{0, 0, 1, 2, 3, 3},
                new float[]{2.0f, 1.0f, 3.0f, 1.0f, 10.0f, 1.0f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0,
                        2.0, 0.0,
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

    private RoutingFixtureFactory.Fixture createPriorityPrunedLabelBudgetFixture() {
        return RoutingFixtureFactory.createFixture(
                6,
                new int[]{0, 2, 3, 4, 6, 6, 6},
                new int[]{1, 2, 4, 3, 4, 5},
                new int[]{0, 0, 1, 2, 3, 3},
                new float[]{2.0f, 2.0f, 3.0f, 2.5f, 10.0f, 0.4f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0,
                        2.0, 0.0,
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

    private RoutingFixtureFactory.Fixture createReverseOverflowFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        0.0, 1.0,
                        1.0, 1.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private int invokeExpandBackwardOne(
            BidirectionalTdAStarPlanner planner,
            org.Aayush.routing.graph.EdgeGraph edgeGraph,
            PlannerQueryContext context,
            java.util.PriorityQueue<BackwardFrontierState> backwardFrontier,
            java.util.PriorityQueue<ForwardFrontierState> forwardFrontier,
            int budgetedWorkStates
    ) {
        try {
            Method method = BidirectionalTdAStarPlanner.class.getDeclaredMethod(
                    "expandBackwardOne",
                    org.Aayush.routing.graph.EdgeGraph.class,
                    PlannerQueryContext.class,
                    java.util.PriorityQueue.class,
                    java.util.PriorityQueue.class,
                    int.class
            );
            method.setAccessible(true);
            return (Integer) method.invoke(
                    planner,
                    edgeGraph,
                    context,
                    backwardFrontier,
                    forwardFrontier,
                    budgetedWorkStates
            );
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to invoke expandBackwardOne", ex);
        }
    }

    private boolean invokeIsBetter(float newCost, long newArrival, float currentCost, long currentArrival) {
        try {
            Method method = BidirectionalTdAStarPlanner.class.getDeclaredMethod(
                    "isBetter",
                    float.class,
                    long.class,
                    float.class,
                    long.class
            );
            method.setAccessible(true);
            return (Boolean) method.invoke(null, newCost, newArrival, currentCost, currentArrival);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to invoke isBetter", ex);
        }
    }

    private boolean invokeCanImproveGoal(float nextCost, long nextArrival, float bestGoalCost, long bestGoalArrival) {
        try {
            Method method = BidirectionalTdAStarPlanner.class.getDeclaredMethod(
                    "canImproveGoal",
                    float.class,
                    long.class,
                    float.class,
                    long.class
            );
            method.setAccessible(true);
            return (Boolean) method.invoke(null, nextCost, nextArrival, bestGoalCost, bestGoalArrival);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to invoke canImproveGoal", ex);
        }
    }

    private CostEngine createDiscreteCostEngine(RoutingFixtureFactory.Fixture fixture) {
        return new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                new LiveOverlay(32),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );
    }

    private TurnCostMap createTurnCostMap(TurnCostSpec... specs) {
        try {
            int size = specs.length;
            int capacity = 1;
            while (capacity < (size * 2)) {
                capacity <<= 1;
            }
            long[] keys = new long[capacity];
            float[] values = new float[capacity];
            Arrays.fill(keys, -1L);
            Method mix = TurnCostMap.class.getDeclaredMethod("mix", long.class);
            mix.setAccessible(true);
            int mask = capacity - 1;
            for (TurnCostSpec spec : specs) {
                long key = (((long) spec.fromEdgeId()) << 32) | (spec.toEdgeId() & 0xFFFFFFFFL);
                int index = ((Integer) mix.invoke(null, key)) & mask;
                while (keys[index] != -1L && keys[index] != key) {
                    index = (index + 1) & mask;
                }
                keys[index] = key;
                values[index] = spec.penaltySeconds();
            }
            Constructor<TurnCostMap> ctor = TurnCostMap.class.getDeclaredConstructor(
                    int.class,
                    int.class,
                    long[].class,
                    float[].class
            );
            ctor.setAccessible(true);
            return ctor.newInstance(size, capacity, keys, values);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to create turn-cost map fixture", ex);
        }
    }

    private record TurnCostSpec(int fromEdgeId, int toEdgeId, float penaltySeconds) {
    }
}
