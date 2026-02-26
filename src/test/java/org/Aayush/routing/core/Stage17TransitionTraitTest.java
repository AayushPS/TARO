package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.FastUtilIDMapper;
import org.Aayush.core.id.IDMapper;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 Transition Trait Tests")
class Stage17TransitionTraitTest {
    private static final int ALL_DAYS_MASK = 0x7F;

    private record TurnSpec(int fromEdge, int toEdge, float penaltySeconds) {
    }

    private record Fixture(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            IDMapper nodeIdMapper
    ) {
    }

    @Test
    @DisplayName("Constructor validation: transition runtime config is required")
    void testTransitionRuntimeConfigRequiredInConstructor() {
        Fixture fixture = createFixture(null, true);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixture.edgeGraph())
                        .profileStore(fixture.profileStore())
                        .costEngine(fixture.costEngine())
                        .nodeIdMapper(fixture.nodeIdMapper())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .build()
        );
        assertEquals(RouteCore.REASON_TRANSITION_CONFIG_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Constructor validation: unknown startup transition trait is rejected")
    void testUnknownTransitionRuntimeTraitRejected() {
        Fixture fixture = createFixture(null, true);
        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixture.edgeGraph())
                        .profileStore(fixture.profileStore())
                        .costEngine(fixture.costEngine())
                        .nodeIdMapper(fixture.nodeIdMapper())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.builder()
                                .transitionTraitId("UNKNOWN")
                                .build())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .build()
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TRANSITION_TRAIT, ex.getReasonCode());
    }

    @Test
    @DisplayName("EDGE_BASED applies finite turn penalty while NODE_BASED ignores it")
    void testEdgeBasedAppliesFiniteTurnPenaltyNodeBasedIgnores() {
        Fixture fixture = createFixture(new TurnSpec[]{new TurnSpec(0, 2, 5.0f)}, true);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        RouteResponse edge = edgeCore.route(request);
        RouteResponse node = nodeCore.route(request);

        assertTrue(edge.isReachable());
        assertTrue(node.isReachable());
        assertEquals(3.0f, edge.getTotalCost(), 1e-6f); // direct edge 0->2
        assertEquals(2.0f, node.getTotalCost(), 1e-6f); // 0->1->2, finite turn penalty ignored
        assertEquals(List.of("N0", "N2"), edge.getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N2"), node.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Optimality: A_STAR matches DIJKSTRA under finite-turn semantics in both modes")
    void testAStarRouteOptimalityMatchesDijkstraInBothTransitionModes() {
        Fixture fixture = createFixture(new TurnSpec[]{new TurnSpec(0, 2, 5.0f)}, true);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        RouteRequest edgeDijkstraRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();
        RouteRequest edgeAStarRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        RouteResponse edgeDijkstra = edgeCore.route(edgeDijkstraRequest);
        RouteResponse edgeAStar = edgeCore.route(edgeAStarRequest);
        assertEquals(edgeDijkstra.isReachable(), edgeAStar.isReachable());
        assertEquals(edgeDijkstra.getTotalCost(), edgeAStar.getTotalCost(), 1e-6f);
        assertEquals(edgeDijkstra.getArrivalTicks(), edgeAStar.getArrivalTicks());
        assertEquals(edgeDijkstra.getPathExternalNodeIds(), edgeAStar.getPathExternalNodeIds());
        assertEquals(3.0f, edgeAStar.getTotalCost(), 1e-6f);
        assertEquals(List.of("N0", "N2"), edgeAStar.getPathExternalNodeIds());

        RouteResponse nodeDijkstra = nodeCore.route(edgeDijkstraRequest);
        RouteResponse nodeAStar = nodeCore.route(edgeAStarRequest);
        assertEquals(nodeDijkstra.isReachable(), nodeAStar.isReachable());
        assertEquals(nodeDijkstra.getTotalCost(), nodeAStar.getTotalCost(), 1e-6f);
        assertEquals(nodeDijkstra.getArrivalTicks(), nodeAStar.getArrivalTicks());
        assertEquals(nodeDijkstra.getPathExternalNodeIds(), nodeAStar.getPathExternalNodeIds());
        assertEquals(2.0f, nodeAStar.getTotalCost(), 1e-6f);
        assertEquals(List.of("N0", "N1", "N2"), nodeAStar.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Matrix optimality: A_STAR matches DIJKSTRA under finite-turn semantics in both modes")
    void testAStarMatrixOptimalityMatchesDijkstraInBothTransitionModes() {
        Fixture fixture = createFixture(new TurnSpec[]{new TurnSpec(0, 2, 5.0f)}, true);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        MatrixRequest dijkstraRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();
        MatrixRequest aStarRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        MatrixResponse edgeDijkstra = edgeCore.matrix(dijkstraRequest);
        MatrixResponse edgeAStar = edgeCore.matrix(aStarRequest);
        assertEquals(edgeDijkstra.getReachable()[0][0], edgeAStar.getReachable()[0][0]);
        assertEquals(edgeDijkstra.getTotalCosts()[0][0], edgeAStar.getTotalCosts()[0][0], 1e-6f);
        assertEquals(edgeDijkstra.getArrivalTicks()[0][0], edgeAStar.getArrivalTicks()[0][0]);
        assertEquals(3.0f, edgeAStar.getTotalCosts()[0][0], 1e-6f);

        MatrixResponse nodeDijkstra = nodeCore.matrix(dijkstraRequest);
        MatrixResponse nodeAStar = nodeCore.matrix(aStarRequest);
        assertEquals(nodeDijkstra.getReachable()[0][0], nodeAStar.getReachable()[0][0]);
        assertEquals(nodeDijkstra.getTotalCosts()[0][0], nodeAStar.getTotalCosts()[0][0], 1e-6f);
        assertEquals(nodeDijkstra.getArrivalTicks()[0][0], nodeAStar.getArrivalTicks()[0][0]);
        assertEquals(2.0f, nodeAStar.getTotalCosts()[0][0], 1e-6f);
    }

    @Test
    @DisplayName("Compatibility fallback: A_STAR matrix preserves transition semantics in both modes")
    void testAStarCompatibilityFallbackPreservesTransitionSemantics() {
        Fixture fixture = createFixture(new TurnSpec[]{new TurnSpec(0, 2, 5.0f)}, true);
        MatrixPlanner fallbackPlanner = new NativeOneToManyMatrixPlanner(
                new TemporaryMatrixPlanner(),
                MatrixSearchBudget.defaults(),
                TerminationPolicy.defaults(),
                1,
                1
        );
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased(), fallbackPlanner);
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased(), fallbackPlanner);

        MatrixRequest matrixRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N1")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        MatrixResponse edgeMatrix = edgeCore.matrix(matrixRequest);
        MatrixResponse nodeMatrix = nodeCore.matrix(matrixRequest);

        assertEquals(NativeOneToManyMatrixPlanner.BATCHED_A_STAR_COMPATIBILITY_NOTE, edgeMatrix.getImplementationNote());
        assertEquals(NativeOneToManyMatrixPlanner.BATCHED_A_STAR_COMPATIBILITY_NOTE, nodeMatrix.getImplementationNote());

        assertTrue(edgeMatrix.getReachable()[0][0]);
        assertTrue(edgeMatrix.getReachable()[0][1]);
        assertTrue(nodeMatrix.getReachable()[0][0]);
        assertTrue(nodeMatrix.getReachable()[0][1]);

        assertEquals(1.0f, edgeMatrix.getTotalCosts()[0][0], 1e-6f);
        assertEquals(3.0f, edgeMatrix.getTotalCosts()[0][1], 1e-6f);
        assertEquals(1.0f, nodeMatrix.getTotalCosts()[0][0], 1e-6f);
        assertEquals(2.0f, nodeMatrix.getTotalCosts()[0][1], 1e-6f);
    }

    @Test
    @DisplayName("Forbidden turn blocks expansion in both EDGE_BASED and NODE_BASED")
    void testForbiddenTurnBlockedInBothModes() {
        Fixture fixture = createFixture(new TurnSpec[]{new TurnSpec(0, 1, Float.POSITIVE_INFINITY)}, false);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        RouteRequest routeRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        RouteResponse edgeRoute = edgeCore.route(routeRequest);
        RouteResponse nodeRoute = nodeCore.route(routeRequest);
        assertFalse(edgeRoute.isReachable());
        assertFalse(nodeRoute.isReachable());

        MatrixRequest matrixRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        MatrixResponse edgeMatrix = edgeCore.matrix(matrixRequest);
        MatrixResponse nodeMatrix = nodeCore.matrix(matrixRequest);
        assertFalse(edgeMatrix.getReachable()[0][0]);
        assertFalse(nodeMatrix.getReachable()[0][0]);
        assertEquals(Float.POSITIVE_INFINITY, edgeMatrix.getTotalCosts()[0][0]);
        assertEquals(Float.POSITIVE_INFINITY, nodeMatrix.getTotalCosts()[0][0]);
    }

    @Test
    @DisplayName("Turn-map absent fixture keeps EDGE_BASED and NODE_BASED outputs identical")
    void testTurnMapAbsentParityAcrossModes() {
        Fixture fixture = createFixture(null, true);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        RouteRequest routeRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        RouteResponse edgeRoute = edgeCore.route(routeRequest);
        RouteResponse nodeRoute = nodeCore.route(routeRequest);
        assertEquals(edgeRoute.isReachable(), nodeRoute.isReachable());
        assertEquals(edgeRoute.getTotalCost(), nodeRoute.getTotalCost(), 1e-6f);
        assertEquals(edgeRoute.getArrivalTicks(), nodeRoute.getArrivalTicks());
        assertEquals(edgeRoute.getPathExternalNodeIds(), nodeRoute.getPathExternalNodeIds());

        MatrixRequest matrixRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N2")
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        MatrixResponse edgeMatrix = edgeCore.matrix(matrixRequest);
        MatrixResponse nodeMatrix = nodeCore.matrix(matrixRequest);
        assertEquals(edgeMatrix.getReachable()[0][0], nodeMatrix.getReachable()[0][0]);
        assertEquals(edgeMatrix.getReachable()[1][0], nodeMatrix.getReachable()[1][0]);
        assertEquals(edgeMatrix.getTotalCosts()[0][0], nodeMatrix.getTotalCosts()[0][0], 1e-6f);
        assertEquals(edgeMatrix.getTotalCosts()[1][0], nodeMatrix.getTotalCosts()[1][0], 1e-6f);
        assertEquals(edgeMatrix.getArrivalTicks()[0][0], nodeMatrix.getArrivalTicks()[0][0]);
        assertEquals(edgeMatrix.getArrivalTicks()[1][0], nodeMatrix.getArrivalTicks()[1][0]);
    }

    private RouteCore createCore(Fixture fixture, TransitionRuntimeConfig transitionRuntimeConfig) {
        return createCore(fixture, transitionRuntimeConfig, null);
    }

    private RouteCore createCore(
            Fixture fixture,
            TransitionRuntimeConfig transitionRuntimeConfig,
            MatrixPlanner matrixPlanner
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(transitionRuntimeConfig)
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime());
        if (matrixPlanner != null) {
            builder.matrixPlanner(matrixPlanner);
        }
        return builder.build();
    }

    private Fixture createFixture(TurnSpec[] turns, boolean includeDirectEdge) {
        int[] firstEdge;
        int[] edgeTarget;
        int[] edgeOrigin;
        float[] baseWeights;
        int[] profileIds;

        if (includeDirectEdge) {
            firstEdge = new int[]{0, 2, 3, 3};
            edgeTarget = new int[]{1, 2, 2};
            edgeOrigin = new int[]{0, 0, 1};
            baseWeights = new float[]{1.0f, 3.0f, 1.0f};
            profileIds = new int[]{1, 1, 1};
        } else {
            firstEdge = new int[]{0, 1, 2, 2};
            edgeTarget = new int[]{1, 2};
            edgeOrigin = new int[]{0, 1};
            baseWeights = new float[]{1.0f, 1.0f};
            profileIds = new int[]{1, 1};
        }

        ByteBuffer model = buildModelBuffer(firstEdge, edgeTarget, edgeOrigin, baseWeights, profileIds, turns);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        TurnCostMap turnCostMap = turns == null
                ? null
                : TurnCostMap.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(16),
                turnCostMap,
                TimeUtils.EngineTimeUnit.SECONDS,
                3_600,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        Map<String, Integer> mappings = new HashMap<>();
        mappings.put("N0", 0);
        mappings.put("N1", 1);
        mappings.put("N2", 2);

        return new Fixture(edgeGraph, profileStore, costEngine, new FastUtilIDMapper(mappings));
    }

    private ByteBuffer buildModelBuffer(
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] profileIds,
            TurnSpec[] turns
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int edgeProfileIdVec = GraphTopology.createEdgeProfileIdVector(builder, profileIds);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, 3);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIdVec);
        int topology = GraphTopology.endGraphTopology(builder);

        int buckets = TemporalProfile.createBucketsVector(builder, new float[]{1.0f});
        int profileOffset = TemporalProfile.createTemporalProfile(
                builder,
                1,
                ALL_DAYS_MASK,
                buckets,
                1.0f
        );
        int profilesVec = Model.createProfilesVector(builder, new int[]{profileOffset});

        int turnsVec = 0;
        if (turns != null && turns.length > 0) {
            int[] turnOffsets = new int[turns.length];
            for (int i = 0; i < turns.length; i++) {
                TurnSpec turn = turns[i];
                turnOffsets[i] = TurnCost.createTurnCost(builder, turn.fromEdge(), turn.toEdge(), turn.penaltySeconds());
            }
            turnsVec = Model.createTurnCostsVector(builder, turnOffsets);
        }

        int metadata = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadata);
        Model.addTopology(builder, topology);
        Model.addProfiles(builder, profilesVec);
        if (turnsVec != 0) {
            Model.addTurnCosts(builder, turnsVec);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("stage17-transition-test");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }
}
