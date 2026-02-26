package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 14 Primitive Guardrail Tests")
class Stage14PrimitiveGuardrailTest {
    private static final String PROP_MAX_ROW_WORK = "taro.routing.stage14.maxRowWorkStates";
    private static final String PROP_MAX_ROW_LABELS = "taro.routing.stage14.maxRowLabels";
    private static final String PROP_MAX_ROW_FRONTIER = "taro.routing.stage14.maxRowFrontierSize";
    private static final String PROP_MAX_REQUEST_WORK = "taro.routing.stage14.maxRequestWorkStates";
    private static final String PROP_MAX_STAGE13_SETTLED = "taro.routing.stage13.maxSettledStates";
    private static final String PROP_MAX_NATIVE_A_STAR_TARGETS = "taro.routing.stage14.maxNativeAStarTargets";
    private static final String PROP_A_STAR_BATCH_TARGETS = "taro.routing.stage14.aStarFallbackBatchTargets";

    @AfterEach
    void restoreBudgetProperties() {
        System.clearProperty(PROP_MAX_ROW_WORK);
        System.clearProperty(PROP_MAX_ROW_LABELS);
        System.clearProperty(PROP_MAX_ROW_FRONTIER);
        System.clearProperty(PROP_MAX_REQUEST_WORK);
        System.clearProperty(PROP_MAX_STAGE13_SETTLED);
        System.clearProperty(PROP_MAX_NATIVE_A_STAR_TARGETS);
        System.clearProperty(PROP_A_STAR_BATCH_TARGETS);
    }

    @Test
    @DisplayName("MatrixSearchBudget defaults honor positive system properties")
    void testMatrixSearchBudgetDefaultsFromProperties() {
        System.setProperty(PROP_MAX_ROW_WORK, "3");
        System.setProperty(PROP_MAX_ROW_LABELS, "4");
        System.setProperty(PROP_MAX_ROW_FRONTIER, "5");
        System.setProperty(PROP_MAX_REQUEST_WORK, "6");

        MatrixSearchBudget budget = MatrixSearchBudget.defaults();

        assertDoesNotThrow(() -> budget.checkRowWorkStates(3));
        MatrixSearchBudget.BudgetExceededException rowWork = assertThrows(
                MatrixSearchBudget.BudgetExceededException.class,
                () -> budget.checkRowWorkStates(4)
        );
        assertEquals(MatrixSearchBudget.REASON_ROW_WORK_EXCEEDED, rowWork.reasonCode());

        assertDoesNotThrow(() -> budget.checkRowLabelCount(4));
        MatrixSearchBudget.BudgetExceededException rowLabels = assertThrows(
                MatrixSearchBudget.BudgetExceededException.class,
                () -> budget.checkRowLabelCount(5)
        );
        assertEquals(MatrixSearchBudget.REASON_ROW_LABEL_EXCEEDED, rowLabels.reasonCode());

        assertDoesNotThrow(() -> budget.checkRowFrontierSize(5));
        MatrixSearchBudget.BudgetExceededException rowFrontier = assertThrows(
                MatrixSearchBudget.BudgetExceededException.class,
                () -> budget.checkRowFrontierSize(6)
        );
        assertEquals(MatrixSearchBudget.REASON_ROW_FRONTIER_EXCEEDED, rowFrontier.reasonCode());

        assertDoesNotThrow(() -> budget.checkRequestWorkStates(6L));
        MatrixSearchBudget.BudgetExceededException requestWork = assertThrows(
                MatrixSearchBudget.BudgetExceededException.class,
                () -> budget.checkRequestWorkStates(7L)
        );
        assertEquals(MatrixSearchBudget.REASON_REQUEST_WORK_EXCEEDED, requestWork.reasonCode());
    }

    @Test
    @DisplayName("MatrixSearchBudget defaults treat blank/invalid/non-positive values as unbounded")
    void testMatrixSearchBudgetDefaultsNormalizeInvalidProperties() {
        System.setProperty(PROP_MAX_ROW_WORK, "-1");
        System.setProperty(PROP_MAX_ROW_LABELS, "0");
        System.setProperty(PROP_MAX_ROW_FRONTIER, "not-a-number");
        System.setProperty(PROP_MAX_REQUEST_WORK, " ");

        MatrixSearchBudget budget = MatrixSearchBudget.defaults();
        assertDoesNotThrow(() -> budget.checkRowWorkStates(1_000_000));
        assertDoesNotThrow(() -> budget.checkRowLabelCount(1_000_000));
        assertDoesNotThrow(() -> budget.checkRowFrontierSize(1_000_000));
        assertDoesNotThrow(() -> budget.checkRequestWorkStates(1_000_000L));
    }

    @Test
    @DisplayName("Stage 14 native planner enforces row frontier budget with deterministic reason code")
    void testStage14PlannerRowFrontierBudgetGuardrail() {
        System.setProperty(PROP_MAX_ROW_FRONTIER, "1");
        RouteCore core = createCore(createTwoBranchFixture(1.0f, 1.0f));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .targetExternalId("N2")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MATRIX_SEARCH_BUDGET_EXCEEDED, ex.getReasonCode());
        assertTrue(ex.getMessage().contains(MatrixSearchBudget.REASON_ROW_FRONTIER_EXCEEDED));
    }

    @Test
    @DisplayName("Stage 14 native planner enforces request-work budget with deterministic reason code")
    void testStage14PlannerRequestWorkBudgetGuardrail() {
        System.setProperty(PROP_MAX_REQUEST_WORK, "1");
        RouteCore core = createCore(createLinearFixture());

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .sourceExternalId("N1")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MATRIX_SEARCH_BUDGET_EXCEEDED, ex.getReasonCode());
        assertTrue(ex.getMessage().contains(MatrixSearchBudget.REASON_REQUEST_WORK_EXCEEDED));
    }

    @Test
    @DisplayName("Matrix compatibility planner remaps route-level Stage-13 budget errors to Stage-14 matrix contract")
    void testMatrixCompatibilityPlannerRemapsRouteBudgetReasonCode() {
        System.setProperty(PROP_MAX_STAGE13_SETTLED, "1");
        RouteCore core = createCore(createLinearFixture(), new TemporaryMatrixPlanner());

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MATRIX_SEARCH_BUDGET_EXCEEDED, ex.getReasonCode());
        assertTrue(ex.getMessage().contains(SearchBudget.REASON_SETTLED_EXCEEDED));
    }

    @Test
    @DisplayName("Matrix A* fallback compatibility path remaps route-level Stage-13 budget errors to Stage-14 matrix contract")
    void testMatrixAStarFallbackRemapsRouteBudgetReasonCode() {
        System.setProperty(PROP_MAX_STAGE13_SETTLED, "1");
        System.setProperty(PROP_MAX_NATIVE_A_STAR_TARGETS, "1");
        System.setProperty(PROP_A_STAR_BATCH_TARGETS, "1");
        RouteCore core = createCore(createLinearFixture());

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MATRIX_SEARCH_BUDGET_EXCEEDED, ex.getReasonCode());
        assertTrue(ex.getMessage().contains(SearchBudget.REASON_SETTLED_EXCEEDED));
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture) {
        return createCore(fixture, null);
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture, MatrixPlanner matrixPlanner) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .matrixPlanner(matrixPlanner)
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
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createTwoBranchFixture(float leftWeight, float rightWeight) {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 2, 2, 2},
                new int[]{1, 2},
                new int[]{0, 0},
                new float[]{leftWeight, rightWeight},
                new int[]{1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }
}
