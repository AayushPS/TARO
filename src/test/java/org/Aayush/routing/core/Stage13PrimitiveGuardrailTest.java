package org.Aayush.routing.core;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 13 Primitive Guardrail Tests")
class Stage13PrimitiveGuardrailTest {
    private static final String PROP_MAX_SETTLED = "taro.routing.stage13.maxSettledStates";
    private static final String PROP_MAX_LABELS = "taro.routing.stage13.maxLabels";
    private static final String PROP_MAX_FRONTIER = "taro.routing.stage13.maxFrontierSize";

    @AfterEach
    void restoreBudgetProperties() {
        System.clearProperty(PROP_MAX_SETTLED);
        System.clearProperty(PROP_MAX_LABELS);
        System.clearProperty(PROP_MAX_FRONTIER);
    }

    @Test
    @DisplayName("SearchBudget defaults honor positive system properties")
    void testSearchBudgetDefaultsFromProperties() {
        System.setProperty(PROP_MAX_SETTLED, "3");
        System.setProperty(PROP_MAX_LABELS, "4");
        System.setProperty(PROP_MAX_FRONTIER, "5");

        SearchBudget budget = SearchBudget.defaults();

        assertDoesNotThrow(() -> budget.checkSettledStates(3));
        SearchBudget.BudgetExceededException settled = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> budget.checkSettledStates(4)
        );
        assertEquals(SearchBudget.REASON_SETTLED_EXCEEDED, settled.reasonCode());

        assertDoesNotThrow(() -> budget.checkLabelCount(4));
        SearchBudget.BudgetExceededException labels = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> budget.checkLabelCount(5)
        );
        assertEquals(SearchBudget.REASON_LABEL_EXCEEDED, labels.reasonCode());

        assertDoesNotThrow(() -> budget.checkFrontierSize(5));
        SearchBudget.BudgetExceededException frontier = assertThrows(
                SearchBudget.BudgetExceededException.class,
                () -> budget.checkFrontierSize(6)
        );
        assertEquals(SearchBudget.REASON_FRONTIER_EXCEEDED, frontier.reasonCode());
    }

    @Test
    @DisplayName("SearchBudget defaults treat blank/invalid/non-positive values as unbounded")
    void testSearchBudgetDefaultsNormalizeInvalidProperties() {
        System.setProperty(PROP_MAX_SETTLED, "-1");
        System.setProperty(PROP_MAX_LABELS, "0");
        System.setProperty(PROP_MAX_FRONTIER, "not-a-number");

        SearchBudget budget = SearchBudget.defaults();
        assertDoesNotThrow(() -> budget.checkSettledStates(1_000_000));
        assertDoesNotThrow(() -> budget.checkLabelCount(1_000_000));
        assertDoesNotThrow(() -> budget.checkFrontierSize(1_000_000));
    }

    @Test
    @DisplayName("TerminationPolicy enforces finite and non-negative frontier priority")
    void testTerminationPolicyNumericSafety() {
        TerminationPolicy policy = TerminationPolicy.defaults();
        assertTrue(!policy.shouldTerminate(Float.POSITIVE_INFINITY, 10.0d));
        assertTrue(!policy.shouldTerminate(5.0f, 5.0d));
        assertTrue(policy.shouldTerminate(5.0f, 5.000_001d));

        TerminationPolicy.NumericSafetyException nonFinite = assertThrows(
                TerminationPolicy.NumericSafetyException.class,
                () -> policy.shouldTerminate(1.0f, Double.NaN)
        );
        assertEquals(TerminationPolicy.REASON_NON_FINITE_PRIORITY, nonFinite.reasonCode());

        TerminationPolicy.NumericSafetyException negative = assertThrows(
                TerminationPolicy.NumericSafetyException.class,
                () -> policy.shouldTerminate(1.0f, -0.01d)
        );
        assertEquals(TerminationPolicy.REASON_NEGATIVE_PRIORITY, negative.reasonCode());
    }

    @Test
    @DisplayName("PathEvaluator fails fast for blocked edge replay with deterministic reason code")
    void testPathEvaluatorRejectsNonFiniteEdgeCost() {
        RoutingFixtureFactory.Fixture fixture = createTwoEdgeChainFixture(1.0f, 1.0f);
        LiveOverlay overlay = new LiveOverlay(8);
        CostEngine costEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                overlay,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );
        overlay.upsert(LiveUpdate.of(0, 0.0f, 100L), 0L);

        PathEvaluator.PathEvaluationException ex = assertThrows(
                PathEvaluator.PathEvaluationException.class,
                () -> new PathEvaluator().evaluateEdgePath(costEngine, new int[]{0}, 0L)
        );
        assertEquals(PathEvaluator.REASON_NON_FINITE_EDGE_COST, ex.reasonCode());
    }

    @Test
    @DisplayName("PathEvaluator fails fast when finite transitions overflow cumulative path cost")
    void testPathEvaluatorRejectsNonFinitePathCost() {
        RoutingFixtureFactory.Fixture fixture = createTwoEdgeChainFixture(2.5e38f, 2.5e38f);

        PathEvaluator.PathEvaluationException ex = assertThrows(
                PathEvaluator.PathEvaluationException.class,
                () -> new PathEvaluator().evaluateEdgePath(fixture.costEngine(), new int[]{0, 1}, 0L)
        );
        assertEquals(PathEvaluator.REASON_NON_FINITE_PATH_COST, ex.reasonCode());
    }

    @Test
    @DisplayName("PathEvaluator node reconstruction rejects non-contiguous edge path")
    void testPathEvaluatorNodeReconstructionGuard() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
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

        PathEvaluator.PathEvaluationException ex = assertThrows(
                PathEvaluator.PathEvaluationException.class,
                () -> new PathEvaluator().toNodePath(fixture.edgeGraph(), 0, new int[]{0, 1})
        );
        assertEquals(PathEvaluator.REASON_NODE_PATH_RECONSTRUCTION, ex.reasonCode());
    }

    @Test
    @DisplayName("Forward frontier deterministic ordering tie-breaks by arrival, edge, then label")
    void testForwardFrontierStateOrdering() {
        ForwardFrontierState earliest = new ForwardFrontierState(10, 5, 100L, 3.0d);
        ForwardFrontierState laterArrival = new ForwardFrontierState(11, 4, 101L, 3.0d);
        ForwardFrontierState higherEdge = new ForwardFrontierState(12, 6, 100L, 3.0d);
        ForwardFrontierState higherLabel = new ForwardFrontierState(13, 5, 100L, 3.0d);

        assertTrue(earliest.compareTo(laterArrival) < 0);
        assertTrue(earliest.compareTo(higherEdge) < 0);
        assertTrue(earliest.compareTo(higherLabel) < 0);
    }

    @Test
    @DisplayName("ReverseEdgeIndex exposes node count and validates node bounds")
    void testReverseEdgeIndexBoundsAndNodeCount() {
        RoutingFixtureFactory.Fixture fixture = createTwoEdgeChainFixture(1.0f, 1.0f);
        ReverseEdgeIndex reverse = ReverseEdgeIndex.build(fixture.edgeGraph());

        assertEquals(3, reverse.nodeCount());
        assertEquals(0, reverse.incomingStart(0));
        assertEquals(1, reverse.incomingEnd(1));
        assertEquals(2, reverse.incomingEnd(2));
        assertEquals(0, reverse.incomingEdgeIdAt(0));
        assertEquals(1, reverse.incomingEdgeIdAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> reverse.incomingStart(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> reverse.incomingEnd(3));
    }

    @Test
    @DisplayName("PlannerQueryContext reset clears touched buffers and reuses edge label lists safely")
    void testPlannerQueryContextReset() {
        PlannerQueryContext context = new PlannerQueryContext();
        DominanceLabelStore labelStore = context.labelStore();
        int labelId = labelStore.add(7, 3.0f, 10L, -1);
        context.activeLabelsForEdge(7).add(labelId);
        context.setReverseBest(4, 9.0f);
        context.markReverseSettled(4);
        context.forwardFrontier().add(new ForwardFrontierState(labelId, 7, 10L, 3.0d));
        context.backwardFrontier().add(new BackwardFrontierState(4, 9.0f));

        context.reset();

        assertEquals(0, labelStore.size());
        assertTrue(context.activeLabelsForEdge(7).isEmpty());
        assertEquals(Float.POSITIVE_INFINITY, context.reverseBest(4));
        assertTrue(!context.isReverseSettled(4));
        assertTrue(context.forwardFrontier().isEmpty());
        assertTrue(context.backwardFrontier().isEmpty());
    }

    private RoutingFixtureFactory.Fixture createTwoEdgeChainFixture(float firstWeight, float secondWeight) {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{firstWeight, secondWeight},
                new int[]{1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
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
}
