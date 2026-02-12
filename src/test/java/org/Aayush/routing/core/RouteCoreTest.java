package org.Aayush.routing.core;

import org.Aayush.core.id.IDMapper;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.heuristic.HeuristicConfigurationException;
import org.Aayush.routing.heuristic.HeuristicFactory;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.heuristic.LandmarkArtifact;
import org.Aayush.routing.heuristic.LandmarkPreprocessor;
import org.Aayush.routing.heuristic.LandmarkPreprocessorConfig;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 12 RouteCore Tests")
class RouteCoreTest {

    @Test
    @DisplayName("Validation: route request must be non-null")
    void testRouteRequestRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(RouteCoreException.class, () -> core.route(null));
        assertEquals(RouteCore.REASON_ROUTE_REQUEST_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: matrix request must be non-null")
    void testMatrixRequestRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(RouteCoreException.class, () -> core.matrix(null));
        assertEquals(RouteCore.REASON_MATRIX_REQUEST_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: unknown external node is rejected deterministically")
    void testUnknownExternalNodeRejected() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("UNKNOWN")
                        .targetExternalId("N1")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNKNOWN_EXTERNAL_NODE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: Dijkstra requires NONE heuristic")
    void testDijkstraHeuristicMismatch() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.EUCLIDEAN)
                        .build())
        );
        assertEquals(RouteCore.REASON_DIJKSTRA_HEURISTIC_MISMATCH, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: route sourceExternalId must be non-blank")
    void testRouteSourceExternalIdRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("   ")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SOURCE_EXTERNAL_ID_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: route targetExternalId must be non-blank")
    void testRouteTargetExternalIdRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId(" ")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TARGET_EXTERNAL_ID_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: route algorithm must be specified")
    void testRouteAlgorithmRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_ALGORITHM_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: route heuristicType must be specified")
    void testRouteHeuristicRequired() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .build())
        );
        assertEquals(RouteCore.REASON_HEURISTIC_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Validation: mapped internal node must fall within edgeGraph bounds")
    void testMappedInternalNodeOutOfBounds() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        int invalidNode = fixture.edgeGraph().nodeCount();
        IDMapper mapper = new FixedIdMapper(Map.of("N0", 0, "N4", invalidNode));
        RouteCore core = createCore(fixture, null, mapper, fixture.costEngine(), null);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_INTERNAL_NODE_OUT_OF_BOUNDS, ex.getReasonCode());
    }

    @Test
    @DisplayName("Route response mapping failures are wrapped deterministically")
    void testExternalMappingFailureWrapped() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        IDMapper mapper = new FixedIdMapper(Map.of("N0", 0, "N4", 4), 2);
        RouteCore core = createCore(fixture, null, mapper, fixture.costEngine(), null);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_EXTERNAL_MAPPING_FAILED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Constructor validation: costEngine graph mismatch is rejected")
    void testCostEngineGraphMismatchInConstructor() {
        RoutingFixtureFactory.Fixture fixtureA = createLinearFixture();
        RoutingFixtureFactory.Fixture fixtureB = createDisconnectedFixture();

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixtureA.edgeGraph())
                        .profileStore(fixtureA.profileStore())
                        .costEngine(fixtureB.costEngine())
                        .nodeIdMapper(fixtureA.nodeIdMapper())
                        .build()
        );
        assertEquals(RouteCore.REASON_COST_ENGINE_GRAPH_MISMATCH, ex.getReasonCode());
    }

    @Test
    @DisplayName("Constructor validation: costEngine profile mismatch is rejected")
    void testCostEngineProfileMismatchInConstructor() {
        RoutingFixtureFactory.Fixture fixtureA = createLinearFixture();
        RoutingFixtureFactory.Fixture profileMismatchFixture = RoutingFixtureFactory.createFixture(
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
                profileMismatchFixture.profileStore(),
                new LiveOverlay(16),
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixtureA.edgeGraph())
                        .profileStore(fixtureA.profileStore())
                        .costEngine(mismatchedCostEngine)
                        .nodeIdMapper(fixtureA.nodeIdMapper())
                        .build()
        );
        assertEquals(RouteCore.REASON_COST_ENGINE_PROFILE_MISMATCH, ex.getReasonCode());
    }

    @Test
    @DisplayName("Trivial source==target route returns zero-cost one-node path")
    void testTrivialRoute() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteResponse response = core.route(RouteRequest.builder()
                .sourceExternalId("N2")
                .targetExternalId("N2")
                .departureTicks(123L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(0.0f, response.getTotalCost());
        assertEquals(123L, response.getArrivalTicks());
        assertEquals(List.of("N2"), response.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Reachable route uses external IDs only and deterministic path")
    void testReachableRoutePath() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteResponse response = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(4.0f, response.getTotalCost(), 1e-6f);
        assertEquals(14L, response.getArrivalTicks());
        assertEquals(List.of("N0", "N1", "N2", "N3", "N4"), response.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Unreachable route returns +INF and empty path")
    void testUnreachableRoute() {
        RouteCore core = createCore(createDisconnectedFixture(), null);
        RouteResponse response = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N3")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertFalse(response.isReachable());
        assertEquals(Float.POSITIVE_INFINITY, response.getTotalCost());
        assertTrue(response.getPathExternalNodeIds().isEmpty());
    }

    @Test
    @DisplayName("A* and Dijkstra parity for identical snapshot on deterministic graph")
    void testAStarDijkstraParity() {
        RouteCore core = createCore(createLinearFixture(), null);

        RouteRequest dijkstraRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteRequest aStarRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse dijkstra = core.route(dijkstraRequest);
        RouteResponse aStar = core.route(aStarRequest);

        assertEquals(dijkstra.getTotalCost(), aStar.getTotalCost(), 1e-6f);
        assertEquals(dijkstra.getArrivalTicks(), aStar.getArrivalTicks());
        assertEquals(dijkstra.getPathExternalNodeIds(), aStar.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Regression: time-dependent planner keeps non-dominated labels per edge")
    void testTimeDependentNonOptimalityRegression() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                6,
                new int[]{0, 2, 3, 4, 5, 6, 6},
                new int[]{1, 2, 4, 3, 1, 5},
                new int[]{0, 0, 1, 2, 3, 4},
                new float[]{1.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f},
                new int[]{1, 1, 1, 1, 1, 2},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        2.0, 0.0,
                        3.0, 0.0,
                        4.0, 0.0,
                        5.0, 0.0
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
                        new float[]{1.0f, 1_000.0f},
                        1.0f
                )
        );

        CostEngine discreteCostEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                new LiveOverlay(32),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        RouteCore core = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(discreteCostEngine)
                .nodeIdMapper(fixture.nodeIdMapper())
                .build();

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N5")
                .departureTicks(3_596L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(1.3f, response.getTotalCost(), 1e-6f);
        assertEquals(3_600L, response.getArrivalTicks());
        assertEquals(List.of("N0", "N1", "N4", "N5"), response.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("LANDMARK A* route works when LandmarkStore is configured")
    void testLandmarkRoute() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                LandmarkPreprocessorConfig.builder()
                        .landmarkCount(2)
                        .selectionSeed(12L)
                        .build()
        );
        RouteCore core = createCore(fixture, LandmarkStore.fromArtifact(artifact));

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.LANDMARK)
                .build());

        assertTrue(response.isReachable());
        assertEquals(4.0f, response.getTotalCost(), 1e-6f);
    }

    @Test
    @DisplayName("LANDMARK route rejects mismatched landmark artifact contracts")
    void testLandmarkSignatureMismatchRejectedByRouteCore() {
        RoutingFixtureFactory.Fixture fixtureA = RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 100.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
        RoutingFixtureFactory.Fixture fixtureB = RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 1.0f, 1.0f, 10.0f},
                new int[]{1, 1, 1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );

        LandmarkStore wrongStore = LandmarkStore.fromArtifact(
                LandmarkPreprocessor.preprocess(
                        fixtureA.edgeGraph(),
                        fixtureA.profileStore(),
                        LandmarkPreprocessorConfig.builder()
                                .landmarkCount(4)
                                .selectionSeed(100L)
                                .build()
                )
        );

        RouteCore core = createCore(fixtureB, wrongStore);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.LANDMARK)
                        .build())
        );
        assertEquals(RouteCore.REASON_HEURISTIC_CONFIGURATION_FAILED, ex.getReasonCode());
        assertTrue(ex.getCause() instanceof HeuristicConfigurationException);
        HeuristicConfigurationException cause = (HeuristicConfigurationException) ex.getCause();
        assertEquals(HeuristicFactory.REASON_LANDMARK_SIGNATURE_MISMATCH, cause.reasonCode());
    }

    @Test
    @DisplayName("Matrix is fully wired and includes Stage 14 revisit note")
    void testMatrixWiringAndNote() {
        RouteCore core = createCore(createLinearFixture(), null);
        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N3")
                .targetExternalId("N4")
                .departureTicks(5L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertEquals(List.of("N0", "N1"), response.getSourceExternalIds());
        assertEquals(List.of("N3", "N4"), response.getTargetExternalIds());
        assertEquals(TemporaryMatrixPlanner.STAGE14_REVISIT_NOTE, response.getImplementationNote());
        assertEquals(2, response.getReachable().length);
        assertEquals(2, response.getReachable()[0].length);
        assertTrue(response.getReachable()[0][0]);
        assertEquals(3.0f, response.getTotalCosts()[0][0], 1e-6f); // N0 -> N3
        assertEquals(4.0f, response.getTotalCosts()[0][1], 1e-6f); // N0 -> N4
        assertEquals(2.0f, response.getTotalCosts()[1][0], 1e-6f); // N1 -> N3
        assertEquals(3.0f, response.getTotalCosts()[1][1], 1e-6f); // N1 -> N4
    }

    @Test
    @DisplayName("Matrix response arrays are defensive copies")
    void testMatrixResponseDefensiveCopy() {
        RouteCore core = createCore(createLinearFixture(), null);
        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        float baseline = response.getTotalCosts()[0][0];
        float[][] costs = response.getTotalCosts();
        costs[0][0] = baseline + 100.0f;

        boolean[][] reachable = response.getReachable();
        reachable[0][0] = !reachable[0][0];

        long[][] arrivals = response.getArrivalTicks();
        arrivals[0][0] = arrivals[0][0] + 123L;

        assertEquals(baseline, response.getTotalCosts()[0][0], 1e-6f);
        assertTrue(response.getReachable()[0][0]);
        assertEquals(4L, response.getArrivalTicks()[0][0]);
    }

    @Test
    @DisplayName("Matrix validation rejects empty source and target sets")
    void testMatrixValidation() {
        RouteCore core = createCore(createLinearFixture(), null);

        RouteCoreException sourceEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .targetExternalId("N1")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SOURCE_LIST_REQUIRED, sourceEx.getReasonCode());

        RouteCoreException targetEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TARGET_LIST_REQUIRED, targetEx.getReasonCode());
    }

    @Test
    @DisplayName("Matrix validation rejects null algorithm and heuristic")
    void testMatrixAlgorithmAndHeuristicRequired() {
        RouteCore core = createCore(createLinearFixture(), null);

        RouteCoreException algorithmEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_ALGORITHM_REQUIRED, algorithmEx.getReasonCode());

        RouteCoreException heuristicEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .build())
        );
        assertEquals(RouteCore.REASON_HEURISTIC_REQUIRED, heuristicEx.getReasonCode());
    }

    @Test
    @DisplayName("Matrix validation rejects blank source and target entries")
    void testMatrixRejectsBlankSourceAndTargetEntries() {
        RouteCore core = createCore(createLinearFixture(), null);

        RouteCoreException blankSourceEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .sourceExternalId(" ")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SOURCE_EXTERNAL_ID_REQUIRED, blankSourceEx.getReasonCode());

        RouteCoreException blankTargetEx = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .targetExternalId("\t")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TARGET_EXTERNAL_ID_REQUIRED, blankTargetEx.getReasonCode());
    }

    @Test
    @DisplayName("Custom matrix planner is used when provided")
    void testCustomMatrixPlannerInjection() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        MatrixPlanner planner = (routeCore, request) -> new MatrixPlan(
                new boolean[][]{{true}},
                new float[][]{{42.5f}},
                new long[][]{{123L}},
                "custom-planner"
        );
        RouteCore core = createCore(fixture, null, fixture.nodeIdMapper(), fixture.costEngine(), planner);

        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertEquals("custom-planner", response.getImplementationNote());
        assertTrue(response.getReachable()[0][0]);
        assertEquals(42.5f, response.getTotalCosts()[0][0], 1e-6f);
        assertEquals(123L, response.getArrivalTicks()[0][0]);
    }

    @Test
    @DisplayName("Repeated identical requests remain deterministic")
    void testDeterministicRepeatedRequests() {
        RouteCore core = createCore(createLinearFixture(), null);
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse first = core.route(request);
        RouteResponse second = core.route(request);
        assertEquals(first.isReachable(), second.isReachable());
        assertEquals(first.getTotalCost(), second.getTotalCost(), 1e-6f);
        assertEquals(first.getArrivalTicks(), second.getArrivalTicks());
        assertEquals(first.getPathExternalNodeIds(), second.getPathExternalNodeIds());
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture, LandmarkStore landmarkStore) {
        return createCore(fixture, landmarkStore, fixture.nodeIdMapper(), fixture.costEngine(), null);
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            LandmarkStore landmarkStore,
            IDMapper mapper,
            CostEngine costEngine,
            MatrixPlanner matrixPlanner
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(costEngine)
                .nodeIdMapper(mapper)
                .landmarkStore(landmarkStore);
        if (matrixPlanner != null) {
            builder.matrixPlanner(matrixPlanner);
        }
        return builder.build();
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

    private static final class FixedIdMapper implements IDMapper {
        private final Map<String, Integer> forward;
        private final Map<Integer, String> reverse;
        private final int failingInternalId;

        private FixedIdMapper(Map<String, Integer> mappings) {
            this(mappings, Integer.MIN_VALUE);
        }

        private FixedIdMapper(Map<String, Integer> mappings, int failingInternalId) {
            this.forward = Map.copyOf(mappings);
            this.reverse = new HashMap<>();
            for (Map.Entry<String, Integer> entry : mappings.entrySet()) {
                reverse.put(entry.getValue(), entry.getKey());
            }
            this.failingInternalId = failingInternalId;
        }

        @Override
        public int toInternal(String externalId) throws UnknownIDException {
            Integer internalId = forward.get(externalId);
            if (internalId == null) {
                throw new UnknownIDException("unknown id: " + externalId);
            }
            return internalId;
        }

        @Override
        public String toExternal(int internalId) {
            if (internalId == failingInternalId) {
                throw new IllegalStateException("forced mapping failure for " + internalId);
            }
            String externalId = reverse.get(internalId);
            if (externalId == null) {
                throw new IndexOutOfBoundsException("no mapping for internalId: " + internalId);
            }
            return externalId;
        }

        @Override
        public boolean containsExternal(String externalId) {
            return forward.containsKey(externalId);
        }

        @Override
        public boolean containsInternal(int internalId) {
            return reverse.containsKey(internalId);
        }

        @Override
        public int size() {
            return forward.size();
        }
    }
}
