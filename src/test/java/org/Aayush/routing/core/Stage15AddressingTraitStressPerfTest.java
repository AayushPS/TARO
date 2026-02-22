package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.addressing.AddressType;
import org.Aayush.routing.traits.addressing.AddressingTelemetry;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 15 Addressing Trait Stress and Perf Tests")
class Stage15AddressingTraitStressPerfTest {

    @Test
    @Timeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 15 perf smoke: typed coordinate route throughput remains practical")
    void testStage15TypedCoordinateRoutePerfSmoke() {
        int rows = 22;
        int cols = 22;
        RoutingFixtureFactory.Fixture fixture = createGridFixture(rows, cols);
        RouteCore core = createCoreWithSpatial(fixture);

        int nodeCount = rows * cols;
        int warmupQueries = 300;
        int queries = 2_000;
        for (int i = 0; i < warmupQueries; i++) {
            core.route(coordinateRouteRequest(i % nodeCount, (i * 13) % nodeCount, cols, i));
        }

        long start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            RouteResponse response = core.route(coordinateRouteRequest(i % nodeCount, (i * 17) % nodeCount, cols, i));
            assertTrue(response.getArrivalTicks() >= response.getDepartureTicks());
        }
        long elapsed = System.nanoTime() - start;
        double avgMicros = (elapsed / 1_000.0d) / queries;

        assertTrue(avgMicros < 2_500.0d, "typed coordinate average latency should stay below 2500us in this smoke test");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 15 stress: concurrent typed-coordinate routes remain deterministic")
    void testStage15TypedCoordinateConcurrentDeterminism() throws InterruptedException {
        int rows = 18;
        int cols = 18;
        RoutingFixtureFactory.Fixture fixture = createGridFixture(rows, cols);
        RouteCore core = createCoreWithSpatial(fixture);

        RouteRequest request = coordinateRouteRequest(0, (rows * cols) - 1, cols, 11L);
        RouteResponse baseline = core.route(request);

        int threads = 8;
        int loops = 220;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        RouteResponse current = core.route(request);
                        if (!routesEquivalent(baseline, current)) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, java.util.concurrent.TimeUnit.SECONDS), "typed-coordinate concurrency stress timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent typed-coordinate route responses diverged");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 15 stress: mixed mode coordinate+external requests remain deterministic under concurrency")
    void testStage15MixedModeConcurrentDeterminism() throws InterruptedException {
        int rows = 16;
        int cols = 16;
        int nodeCount = rows * cols;
        RoutingFixtureFactory.Fixture fixture = createGridFixture(rows, cols);
        RouteCore core = createCoreWithSpatial(fixture);

        RouteRequest request = RouteRequest.builder()
                .sourceAddress(coordinateAddressForNode(0, cols))
                .targetExternalId("N" + (nodeCount - 1))
                .allowMixedAddressing(true)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.25d)
                .departureTicks(17L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();

        RouteResponse baseline = core.route(request);
        assertEquals(AddressType.COORDINATES, baseline.getSourceResolvedAddress().getInputType());
        assertEquals(AddressType.EXTERNAL_ID, baseline.getTargetResolvedAddress().getInputType());

        int threads = 8;
        int loops = 180;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        RouteResponse current = core.route(request);
                        if (!routesEquivalent(baseline, current)) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, java.util.concurrent.TimeUnit.SECONDS), "mixed-mode concurrency stress timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent mixed-mode route responses diverged");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 15 stress/perf: typed matrix dedup remains deterministic and reduces resolve work")
    void testStage15MatrixDedupStressAndPerf() {
        int rows = 20;
        int cols = 20;
        RoutingFixtureFactory.Fixture fixture = createGridFixture(rows, cols);
        RouteCore core = createCoreWithSpatial(fixture);

        MatrixRequest request = buildDedupHeavyMatrixRequest(cols);
        MatrixResponse baseline = core.matrix(request);
        AddressingTelemetry telemetry = core.addressingTelemetryContract();

        assertTrue(telemetry.endpointCount() > 0);
        assertEquals(telemetry.uniqueEndpointCount(), telemetry.resolveCalls());
        assertTrue(telemetry.dedupSaved() >= 45, "expected substantial dedup savings for duplicate-heavy matrix");
        assertTrue(telemetry.uniqueEndpointCount() <= 12, "expected small unique endpoint count for duplicate-heavy matrix");

        int loops = 24;
        long start = System.nanoTime();
        for (int i = 0; i < loops; i++) {
            MatrixResponse current = core.matrix(request);
            assertTrue(matrixResponsesEqual(baseline, current), "typed matrix response drift at loop " + i);
        }
        long elapsed = System.nanoTime() - start;
        double avgMillis = (elapsed / 1_000_000.0d) / loops;
        assertTrue(avgMillis < 250.0d, "typed matrix average latency should stay below 250ms in this stress/perf smoke");
    }

    private RouteRequest coordinateRouteRequest(int sourceNodeId, int targetNodeId, int cols, long departureTicks) {
        return RouteRequest.builder()
                .sourceAddress(coordinateAddressForNode(sourceNodeId, cols))
                .targetAddress(coordinateAddressForNode(targetNodeId, cols))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.25d)
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build();
    }

    private AddressInput coordinateAddressForNode(int nodeId, int cols) {
        int row = nodeId / cols;
        int col = nodeId % cols;
        return AddressInput.ofCoordinates(row + 0.03d, col - 0.02d, null);
    }

    private MatrixRequest buildDedupHeavyMatrixRequest(int cols) {
        int[] sourceNodes = new int[]{0, 1, cols, cols + 1};
        int[] targetNodes = new int[]{(17 * cols) + 0, (17 * cols) + 1, (18 * cols) + 0, (18 * cols) + 1, (19 * cols) + 0, (19 * cols) + 19};

        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.25d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE);

        for (int i = 0; i < 24; i++) {
            int node = sourceNodes[i % sourceNodes.length];
            builder.sourceAddress(coordinateAddressForNode(node, cols));
        }
        for (int i = 0; i < 36; i++) {
            int node = targetNodes[i % targetNodes.length];
            builder.targetAddress(coordinateAddressForNode(node, cols));
        }
        return builder.build();
    }

    private boolean routesEquivalent(RouteResponse baseline, RouteResponse candidate) {
        return baseline.isReachable() == candidate.isReachable()
                && Float.compare(baseline.getTotalCost(), candidate.getTotalCost()) == 0
                && baseline.getArrivalTicks() == candidate.getArrivalTicks()
                && baseline.getSettledStates() == candidate.getSettledStates()
                && baseline.getPathExternalNodeIds().equals(candidate.getPathExternalNodeIds())
                && baseline.getSourceResolvedAddress().equals(candidate.getSourceResolvedAddress())
                && baseline.getTargetResolvedAddress().equals(candidate.getTargetResolvedAddress());
    }

    private boolean matrixResponsesEqual(MatrixResponse baseline, MatrixResponse candidate) {
        if (!baseline.getSourceExternalIds().equals(candidate.getSourceExternalIds())) {
            return false;
        }
        if (!baseline.getTargetExternalIds().equals(candidate.getTargetExternalIds())) {
            return false;
        }
        if (!baseline.getImplementationNote().equals(candidate.getImplementationNote())) {
            return false;
        }

        boolean[][] baselineReachable = baseline.getReachable();
        boolean[][] candidateReachable = candidate.getReachable();
        float[][] baselineCosts = baseline.getTotalCosts();
        float[][] candidateCosts = candidate.getTotalCosts();
        long[][] baselineArrivals = baseline.getArrivalTicks();
        long[][] candidateArrivals = candidate.getArrivalTicks();

        if (baselineReachable.length != candidateReachable.length
                || baselineCosts.length != candidateCosts.length
                || baselineArrivals.length != candidateArrivals.length) {
            return false;
        }

        for (int row = 0; row < baselineReachable.length; row++) {
            if (baselineReachable[row].length != candidateReachable[row].length
                    || baselineCosts[row].length != candidateCosts[row].length
                    || baselineArrivals[row].length != candidateArrivals[row].length) {
                return false;
            }

            for (int col = 0; col < baselineReachable[row].length; col++) {
                if (baselineReachable[row][col] != candidateReachable[row][col]) {
                    return false;
                }
                if (Float.compare(baselineCosts[row][col], candidateCosts[row][col]) != 0) {
                    return false;
                }
                if (baselineArrivals[row][col] != candidateArrivals[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    private RouteCore createCoreWithSpatial(RoutingFixtureFactory.Fixture fixture) {
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromGraph(fixture.edgeGraph());
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .spatialRuntime(spatialRuntime)
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .build();
    }

    private RoutingFixtureFactory.Fixture createGridFixture(int rows, int cols) {
        int nodeCount = rows * cols;
        int[] outDegree = new int[nodeCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
                int degree = 0;
                if (c + 1 < cols) degree++;
                if (c - 1 >= 0) degree++;
                if (r + 1 < rows) degree++;
                if (r - 1 >= 0) degree++;
                outDegree[node] = degree;
            }
        }

        int[] firstEdge = new int[nodeCount + 1];
        int edgeCount = 0;
        for (int i = 0; i < nodeCount; i++) {
            firstEdge[i] = edgeCount;
            edgeCount += outDegree[i];
        }
        firstEdge[nodeCount] = edgeCount;

        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfiles = new int[edgeCount];
        double[] coords = new double[nodeCount * 2];

        int cursor = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
                coords[node * 2] = r;
                coords[node * 2 + 1] = c;

                if (c + 1 < cols) {
                    edgeTarget[cursor] = node + 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (c - 1 >= 0) {
                    edgeTarget[cursor] = node - 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (r + 1 < rows) {
                    edgeTarget[cursor] = node + cols;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (r - 1 >= 0) {
                    edgeTarget[cursor] = node - cols;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
            }
        }

        return RoutingFixtureFactory.createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfiles,
                coords,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private SpatialRuntime buildSpatialRuntimeFromGraph(EdgeGraph edgeGraph) {
        int nodeCount = edgeGraph.nodeCount();
        double[] coordinates = new double[nodeCount * 2];
        for (int i = 0; i < nodeCount; i++) {
            coordinates[i * 2] = edgeGraph.getNodeX(i);
            coordinates[i * 2 + 1] = edgeGraph.getNodeY(i);
        }
        ByteBuffer model = buildSpatialModelBuffer(nodeCount, coordinates);
        return SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), edgeGraph, true);
    }

    private ByteBuffer buildSpatialModelBuffer(int nodeCount, double[] coordinates) {
        FlatBufferBuilder builder = new FlatBufferBuilder(2048);

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTargets = new int[0];

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTargets);

        GraphTopology.startCoordinatesVector(builder, nodeCount);
        for (int i = nodeCount - 1; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    coordinates[i * 2],
                    coordinates[i * 2 + 1]
            );
        }
        int coordinatesVec = builder.endVector();

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, 0);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addCoordinates(builder, coordinatesVec);
        int topologyRef = GraphTopology.endGraphTopology(builder);

        int[] leafItems = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            leafItems[i] = i;
        }

        SpatialIndex.startTreeNodesVector(builder, 1);
        KDNode.createKDNode(builder, 0.0f, -1, -1, 0, nodeCount, 0, 1);
        int treeNodesVec = builder.endVector();
        int leafItemsVec = SpatialIndex.createLeafItemsVector(builder, leafItems);

        SpatialIndex.startSpatialIndex(builder);
        SpatialIndex.addTreeNodes(builder, treeNodesVec);
        SpatialIndex.addLeafItems(builder, leafItemsVec);
        SpatialIndex.addRootIndex(builder, 0);
        int spatialRef = SpatialIndex.endSpatialIndex(builder);

        int metadataRef = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataRef);
        Model.addTopology(builder, topologyRef);
        Model.addSpatialIndex(builder, spatialRef);
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("stage15-stress-test");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }
}
