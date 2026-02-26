package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.Aayush.core.id.FastUtilIDMapper;
import org.Aayush.core.id.IDMapper;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
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
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 17 Transition Trait Stress and Perf Tests")
class Stage17TransitionTraitStressPerfTest {
    private static final int ALL_DAYS_MASK = 0x7F;

    private static final double MAX_NO_TURN_ROUTE_RATIO = 1.15d;
    private static final double MAX_NO_TURN_MATRIX_RATIO = 1.20d;
    private static final double MAX_MODERATE_TURN_ROUTE_RATIO = 1.35d;

    private static final double MAX_ROUTE_AVG_MICROS = 6_000.0d;
    private static final double MAX_MATRIX_AVG_MILLIS = 220.0d;

    private record TurnSpec(int fromEdge, int toEdge, float penaltySeconds) {
    }

    private record Fixture(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            LiveOverlay liveOverlay,
            IDMapper nodeIdMapper,
            int turnEntryCount
    ) {
    }

    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf/parity: no-turn route workload keeps EDGE_BASED close to NODE_BASED")
    void testNoTurnRouteParityAndPerf() {
        Fixture fixture = createGridFixture(20, 20, null);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        int nodeCount = fixture.edgeGraph().nodeCount();
        assertRouteParity(edgeCore, nodeCore, 320, nodeCount, 10_000L);

        int warmupQueries = 220;
        int measuredQueries = 1_400;
        runRouteBatch(nodeCore, warmupQueries, nodeCount, 100_000L);
        runRouteBatch(edgeCore, warmupQueries, nodeCount, 200_000L);

        long nodeElapsed = runRouteBatch(nodeCore, measuredQueries, nodeCount, 1_000_000L);
        long edgeElapsed = runRouteBatch(edgeCore, measuredQueries, nodeCount, 2_000_000L);

        double nodeAvgMicros = (nodeElapsed / 1_000.0d) / measuredQueries;
        double edgeAvgMicros = (edgeElapsed / 1_000.0d) / measuredQueries;

        assertTrue(nodeAvgMicros < MAX_ROUTE_AVG_MICROS,
                "NODE_BASED route average latency exceeded smoke bound: " + nodeAvgMicros + "us");
        assertTrue(edgeAvgMicros < MAX_ROUTE_AVG_MICROS,
                "EDGE_BASED route average latency exceeded smoke bound: " + edgeAvgMicros + "us");
        assertTrue(
                edgeAvgMicros <= (nodeAvgMicros * MAX_NO_TURN_ROUTE_RATIO) + 1.0d,
                "EDGE_BASED no-turn route latency regressed beyond allowed ratio (edge="
                        + edgeAvgMicros + "us, node=" + nodeAvgMicros + "us)"
        );
    }

    @Test
    @Timeout(value = 35, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf/parity: no-turn matrix workload keeps EDGE_BASED close to NODE_BASED")
    void testNoTurnMatrixParityAndPerf() {
        Fixture fixture = createGridFixture(18, 18, null);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        int nodeCount = fixture.edgeGraph().nodeCount();
        MatrixRequest request = buildMatrixRequest(nodeCount, 10, 10, 777_000L);

        MatrixResponse edgeBaseline = edgeCore.matrix(request);
        MatrixResponse nodeBaseline = nodeCore.matrix(request);
        assertTrue(matrixEquals(edgeBaseline, nodeBaseline), "no-turn matrix parity drifted between transition modes");

        int warmupLoops = 8;
        int measuredLoops = 20;
        runMatrixBatch(nodeCore, request, warmupLoops);
        runMatrixBatch(edgeCore, request, warmupLoops);

        long nodeElapsed = runMatrixBatch(nodeCore, request, measuredLoops);
        long edgeElapsed = runMatrixBatch(edgeCore, request, measuredLoops);
        double nodeAvgMillis = (nodeElapsed / 1_000_000.0d) / measuredLoops;
        double edgeAvgMillis = (edgeElapsed / 1_000_000.0d) / measuredLoops;

        assertTrue(nodeAvgMillis < MAX_MATRIX_AVG_MILLIS,
                "NODE_BASED matrix average latency exceeded smoke bound: " + nodeAvgMillis + "ms");
        assertTrue(edgeAvgMillis < MAX_MATRIX_AVG_MILLIS,
                "EDGE_BASED matrix average latency exceeded smoke bound: " + edgeAvgMillis + "ms");
        assertTrue(
                edgeAvgMillis <= (nodeAvgMillis * MAX_NO_TURN_MATRIX_RATIO) + 2.0d,
                "EDGE_BASED no-turn matrix latency regressed beyond allowed ratio (edge="
                        + edgeAvgMillis + "ms, node=" + nodeAvgMillis + "ms)"
        );
    }

    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf: moderate turn-density keeps EDGE_BASED route latency within bounded overhead")
    void testModerateTurnDensityRoutePerfBound() {
        Fixture noTurnFixture = createGridFixture(20, 20, null);
        Fixture turnFixture = createGridFixtureWithTurnDensity(20, 20, 0.18d, 17_017L);

        assertTrue(turnFixture.turnEntryCount() > 0, "turn-density fixture should materialize explicit turn entries");

        RouteCore noTurnCore = createCore(noTurnFixture, TransitionRuntimeConfig.edgeBased());
        RouteCore turnCore = createCore(turnFixture, TransitionRuntimeConfig.edgeBased());

        int nodeCount = noTurnFixture.edgeGraph().nodeCount();
        int warmupQueries = 220;
        int measuredQueries = 1_200;

        runRouteBatch(noTurnCore, warmupQueries, nodeCount, 300_000L);
        runRouteBatch(turnCore, warmupQueries, nodeCount, 300_000L);

        long noTurnElapsed = runRouteBatch(noTurnCore, measuredQueries, nodeCount, 5_000_000L);
        long turnElapsed = runRouteBatch(turnCore, measuredQueries, nodeCount, 5_000_000L);

        double noTurnAvgMicros = (noTurnElapsed / 1_000.0d) / measuredQueries;
        double turnAvgMicros = (turnElapsed / 1_000.0d) / measuredQueries;

        assertTrue(
                turnAvgMicros <= (noTurnAvgMicros * MAX_MODERATE_TURN_ROUTE_RATIO) + 1.0d,
                "moderate turn-density route latency regressed beyond allowed ratio (turn="
                        + turnAvgMicros + "us, noTurn=" + noTurnAvgMicros + "us)"
        );
    }

    @Test
    @Timeout(value = 45, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: route and matrix remain deterministic under concurrency for both transition modes")
    void testTransitionModeConcurrencyDeterminism() throws InterruptedException {
        Fixture fixture = createGridFixtureWithTurnDensity(16, 16, 0.16d, 27_027L);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        RouteRequest edgeRouteRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N255")
                .departureTicks(1_778_313_600L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteRequest nodeRouteRequest = RouteRequest.builder()
                .sourceExternalId("N9")
                .targetExternalId("N244")
                .departureTicks(1_778_313_711L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixRequest edgeMatrixRequest = buildMatrixRequest(fixture.edgeGraph().nodeCount(), 8, 8, 1_778_313_900L);
        MatrixRequest nodeMatrixRequest = buildMatrixRequest(fixture.edgeGraph().nodeCount(), 7, 7, 1_778_314_200L);

        RouteResponse edgeRouteBaseline = edgeCore.route(edgeRouteRequest);
        RouteResponse nodeRouteBaseline = nodeCore.route(nodeRouteRequest);
        MatrixResponse edgeMatrixBaseline = edgeCore.matrix(edgeMatrixRequest);
        MatrixResponse nodeMatrixBaseline = nodeCore.matrix(nodeMatrixRequest);

        int threads = 8;
        int loops = 96;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        int mode = (threadId + i) & 3;
                        if (mode == 0) {
                            RouteResponse current = edgeCore.route(edgeRouteRequest);
                            if (!routeEquals(edgeRouteBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 1) {
                            RouteResponse current = nodeCore.route(nodeRouteRequest);
                            if (!routeEquals(nodeRouteBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 2) {
                            MatrixResponse current = edgeCore.matrix(edgeMatrixRequest);
                            if (!matrixEquals(edgeMatrixBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else {
                            MatrixResponse current = nodeCore.matrix(nodeMatrixRequest);
                            if (!matrixEquals(nodeMatrixBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(40, java.util.concurrent.TimeUnit.SECONDS), "stage17 transition concurrency run timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "transition-mode outputs diverged under concurrent stress");
    }

    @Test
    @Timeout(value = 45, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: live-overlay churn plus turn-map workload preserves parity and deterministic replay")
    void testLiveOverlayChurnParityAndDeterminism() {
        Fixture fixture = createGridFixtureWithTurnDensity(14, 14, 0.16d, 33_117L);
        RouteCore edgeCore = createCore(fixture, TransitionRuntimeConfig.edgeBased());
        RouteCore nodeCore = createCore(fixture, TransitionRuntimeConfig.nodeBased());

        int nodeCount = fixture.edgeGraph().nodeCount();
        int edgeCount = fixture.edgeGraph().edgeCount();
        Random random = new Random(17_423L);
        int iterations = 60;
        int updatesPerIteration = 56;

        for (int i = 0; i < iterations; i++) {
            long nowTicks = i * 5L;
            List<LiveUpdate> updates = new ArrayList<>(updatesPerIteration);
            for (int j = 0; j < updatesPerIteration; j++) {
                int edgeId = random.nextInt(edgeCount);
                float speedFactor = random.nextInt(12) == 0
                        ? 0.0f
                        : 0.20f + (random.nextFloat() * 0.80f);
                long validUntilTicks = (j % 9 == 0)
                        ? nowTicks - random.nextInt(4)
                        : nowTicks + 1 + random.nextInt(20);
                updates.add(LiveUpdate.of(edgeId, speedFactor, validUntilTicks));
            }
            LiveOverlay.BatchApplyResult applyResult = fixture.liveOverlay().applyBatch(updates, nowTicks);
            assertEquals(
                    updatesPerIteration,
                    applyResult.accepted() + applyResult.rejectedExpiredAtIngest() + applyResult.rejectedCapacity(),
                    "live-overlay batch accounting mismatch at iteration " + i
            );

            int source = random.nextInt(nodeCount);
            int target = random.nextInt(nodeCount);
            if (source == target) {
                target = (target + 1) % nodeCount;
            }

            RouteRequest edgeAStarRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteRequest edgeDijkstraRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks)
                    .algorithm(RoutingAlgorithm.DIJKSTRA)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteRequest nodeAStarRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks + 1L)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteRequest nodeDijkstraRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks + 1L)
                    .algorithm(RoutingAlgorithm.DIJKSTRA)
                    .heuristicType(HeuristicType.NONE)
                    .build();

            RouteResponse edgeAStarFirst = edgeCore.route(edgeAStarRequest);
            RouteResponse edgeAStarSecond = edgeCore.route(edgeAStarRequest);
            RouteResponse edgeDijkstra = edgeCore.route(edgeDijkstraRequest);
            assertTrue(routeEquals(edgeAStarFirst, edgeAStarSecond), "EDGE_BASED replay drift at iteration " + i);
            assertAStarDijkstraParity(edgeDijkstra, edgeAStarFirst, "EDGE_BASED parity drift at iteration " + i);

            RouteResponse nodeAStarFirst = nodeCore.route(nodeAStarRequest);
            RouteResponse nodeAStarSecond = nodeCore.route(nodeAStarRequest);
            RouteResponse nodeDijkstra = nodeCore.route(nodeDijkstraRequest);
            assertTrue(routeEquals(nodeAStarFirst, nodeAStarSecond), "NODE_BASED replay drift at iteration " + i);
            assertAStarDijkstraParity(nodeDijkstra, nodeAStarFirst, "NODE_BASED parity drift at iteration " + i);

            MatrixRequest edgeMatrixRequest = buildMatrixRequest(nodeCount, 4, 6, nowTicks + 2L);
            MatrixRequest nodeMatrixRequest = buildMatrixRequest(nodeCount, 4, 6, nowTicks + 3L);
            MatrixResponse edgeMatrixFirst = edgeCore.matrix(edgeMatrixRequest);
            MatrixResponse edgeMatrixSecond = edgeCore.matrix(edgeMatrixRequest);
            MatrixResponse nodeMatrixFirst = nodeCore.matrix(nodeMatrixRequest);
            MatrixResponse nodeMatrixSecond = nodeCore.matrix(nodeMatrixRequest);
            assertTrue(matrixEquals(edgeMatrixFirst, edgeMatrixSecond), "EDGE_BASED matrix replay drift at iteration " + i);
            assertTrue(matrixEquals(nodeMatrixFirst, nodeMatrixSecond), "NODE_BASED matrix replay drift at iteration " + i);
        }
    }

    private RouteCore createCore(Fixture fixture, TransitionRuntimeConfig transitionRuntimeConfig) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(transitionRuntimeConfig)
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private long runRouteBatch(RouteCore core, int queries, int nodeCount, long departureBaseTicks) {
        long start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            int source = i % nodeCount;
            int target = ((i * 37) + 11) % nodeCount;
            RouteResponse response = core.route(RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(departureBaseTicks + i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
            assertTrue(response.getArrivalTicks() >= response.getDepartureTicks());
            assertTrue(response.getTotalCost() >= 0.0f || response.getTotalCost() == Float.POSITIVE_INFINITY);
        }
        return System.nanoTime() - start;
    }

    private long runMatrixBatch(RouteCore core, MatrixRequest request, int loops) {
        long start = System.nanoTime();
        for (int i = 0; i < loops; i++) {
            MatrixResponse response = core.matrix(request);
            assertEquals(request.getSourceExternalIds().size(), response.getSourceExternalIds().size());
            assertEquals(request.getTargetExternalIds().size(), response.getTargetExternalIds().size());
        }
        return System.nanoTime() - start;
    }

    private void assertRouteParity(RouteCore edgeCore, RouteCore nodeCore, int queries, int nodeCount, long departureBaseTicks) {
        for (int i = 0; i < queries; i++) {
            int source = ((i * 17) + 3) % nodeCount;
            int target = ((i * 29) + 7) % nodeCount;
            RouteRequest request = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(departureBaseTicks + i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteResponse edge = edgeCore.route(request);
            RouteResponse node = nodeCore.route(request);
            assertTrue(routeEquals(edge, node), "no-turn route parity mismatch at query " + i);
        }
    }

    private boolean routeEquals(RouteResponse expected, RouteResponse actual) {
        return expected.isReachable() == actual.isReachable()
                && Float.compare(expected.getTotalCost(), actual.getTotalCost()) == 0
                && expected.getArrivalTicks() == actual.getArrivalTicks()
                && expected.getSettledStates() == actual.getSettledStates()
                && expected.getPathExternalNodeIds().equals(actual.getPathExternalNodeIds());
    }

    private void assertAStarDijkstraParity(RouteResponse dijkstra, RouteResponse aStar, String prefix) {
        assertEquals(dijkstra.isReachable(), aStar.isReachable(), prefix + " reachability");
        assertEquals(dijkstra.getTotalCost(), aStar.getTotalCost(), 1e-5f, prefix + " cost");
        if (dijkstra.isReachable()) {
            assertEquals(dijkstra.getArrivalTicks(), aStar.getArrivalTicks(), prefix + " arrival");
        }
    }

    private boolean matrixEquals(MatrixResponse expected, MatrixResponse actual) {
        if (!expected.getSourceExternalIds().equals(actual.getSourceExternalIds())) {
            return false;
        }
        if (!expected.getTargetExternalIds().equals(actual.getTargetExternalIds())) {
            return false;
        }
        if (!expected.getImplementationNote().equals(actual.getImplementationNote())) {
            return false;
        }

        boolean[][] expectedReachable = expected.getReachable();
        boolean[][] actualReachable = actual.getReachable();
        float[][] expectedCosts = expected.getTotalCosts();
        float[][] actualCosts = actual.getTotalCosts();
        long[][] expectedArrivals = expected.getArrivalTicks();
        long[][] actualArrivals = actual.getArrivalTicks();

        if (expectedReachable.length != actualReachable.length
                || expectedCosts.length != actualCosts.length
                || expectedArrivals.length != actualArrivals.length) {
            return false;
        }

        for (int row = 0; row < expectedReachable.length; row++) {
            if (expectedReachable[row].length != actualReachable[row].length
                    || expectedCosts[row].length != actualCosts[row].length
                    || expectedArrivals[row].length != actualArrivals[row].length) {
                return false;
            }
            for (int col = 0; col < expectedReachable[row].length; col++) {
                if (expectedReachable[row][col] != actualReachable[row][col]) {
                    return false;
                }
                if (Float.compare(expectedCosts[row][col], actualCosts[row][col]) != 0) {
                    return false;
                }
                if (expectedArrivals[row][col] != actualArrivals[row][col]) {
                    return false;
                }
            }
        }

        return true;
    }

    private MatrixRequest buildMatrixRequest(int nodeCount, int sourceCount, int targetCount, long departureTicks) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE);
        for (int i = 0; i < sourceCount; i++) {
            builder.sourceExternalId("N" + ((i * 31 + 5) % nodeCount));
        }
        for (int i = 0; i < targetCount; i++) {
            builder.targetExternalId("N" + ((i * 19 + 9) % nodeCount));
        }
        return builder.build();
    }

    private Fixture createGridFixture(int rows, int cols, TurnSpec[] turns) {
        int nodeCount = rows * cols;
        int[] outDegree = new int[nodeCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int degree = 0;
                if (c + 1 < cols) degree++;
                if (c - 1 >= 0) degree++;
                if (r + 1 < rows) degree++;
                if (r - 1 >= 0) degree++;
                outDegree[r * cols + c] = degree;
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

        int cursor = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
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
                if (r - 1 < 0) {
                    continue;
                }
                edgeTarget[cursor] = node - cols;
                edgeOrigin[cursor] = node;
                baseWeights[cursor] = 1.0f;
                edgeProfiles[cursor] = 1;
                cursor++;
            }
        }

        ByteBuffer model = buildModelBuffer(nodeCount, firstEdge, edgeTarget, edgeOrigin, baseWeights, edgeProfiles, turns);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        TurnCostMap turnCostMap = turns == null || turns.length == 0
                ? null
                : TurnCostMap.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        LiveOverlay liveOverlay = new LiveOverlay(Math.max(64, edgeGraph.edgeCount()));
        CostEngine costEngine = new CostEngine(
                edgeGraph,
                profileStore,
                liveOverlay,
                turnCostMap,
                TimeUtils.EngineTimeUnit.SECONDS,
                3_600,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        Map<String, Integer> mappings = new HashMap<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            mappings.put("N" + i, i);
        }

        return new Fixture(
                edgeGraph,
                profileStore,
                costEngine,
                liveOverlay,
                new FastUtilIDMapper(mappings),
                turns == null ? 0 : turns.length
        );
    }

    private Fixture createGridFixtureWithTurnDensity(int rows, int cols, double turnDensity, long seed) {
        int nodeCount = rows * cols;
        int[] outDegree = new int[nodeCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int degree = 0;
                if (c + 1 < cols) degree++;
                if (c - 1 >= 0) degree++;
                if (r + 1 < rows) degree++;
                if (r - 1 >= 0) degree++;
                outDegree[r * cols + c] = degree;
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

        IntArrayList[] incomingByNode = new IntArrayList[nodeCount];
        IntArrayList[] outgoingByNode = new IntArrayList[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            incomingByNode[i] = new IntArrayList();
            outgoingByNode[i] = new IntArrayList();
        }

        int cursor = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
                if (c + 1 < cols) {
                    edgeTarget[cursor] = node + 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    outgoingByNode[node].add(cursor);
                    incomingByNode[node + 1].add(cursor);
                    cursor++;
                }
                if (c - 1 >= 0) {
                    edgeTarget[cursor] = node - 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    outgoingByNode[node].add(cursor);
                    incomingByNode[node - 1].add(cursor);
                    cursor++;
                }
                if (r + 1 < rows) {
                    edgeTarget[cursor] = node + cols;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    outgoingByNode[node].add(cursor);
                    incomingByNode[node + cols].add(cursor);
                    cursor++;
                }
                if (r - 1 < 0) {
                    continue;
                }
                edgeTarget[cursor] = node - cols;
                edgeOrigin[cursor] = node;
                baseWeights[cursor] = 1.0f;
                edgeProfiles[cursor] = 1;
                outgoingByNode[node].add(cursor);
                incomingByNode[node - cols].add(cursor);
                cursor++;
            }
        }

        Random random = new Random(seed);
        List<TurnSpec> turns = new ArrayList<>();
        for (int node = 0; node < nodeCount; node++) {
            IntArrayList incoming = incomingByNode[node];
            IntArrayList outgoing = outgoingByNode[node];
            for (int i = 0; i < incoming.size(); i++) {
                int fromEdge = incoming.getInt(i);
                for (int j = 0; j < outgoing.size(); j++) {
                    int toEdge = outgoing.getInt(j);
                    if (fromEdge == toEdge) {
                        continue;
                    }
                    if (random.nextDouble() < turnDensity) {
                        float penalty = 0.25f + (random.nextFloat() * 2.25f);
                        turns.add(new TurnSpec(fromEdge, toEdge, penalty));
                    }
                }
            }
        }

        return createGridFixture(rows, cols, turns.toArray(new TurnSpec[0]));
    }

    private ByteBuffer buildModelBuffer(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            TurnSpec[] turns
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int edgeProfileVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileVec);
        int topology = GraphTopology.endGraphTopology(builder);

        float[] bucketsByHour = new float[24];
        Arrays.fill(bucketsByHour, 1.0f);
        int buckets = TemporalProfile.createBucketsVector(builder, bucketsByHour);
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
        int modelVersion = builder.createString("stage17-stress-perf-test");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }
}
