package org.Aayush.routing.core;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.heuristic.LandmarkArtifact;
import org.Aayush.routing.heuristic.LandmarkPreprocessor;
import org.Aayush.routing.heuristic.LandmarkPreprocessorConfig;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 13 RouteCore Stress and Perf Tests")
class RouteCoreStressPerfTest {

    @Test
    @Timeout(value = 12, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: random query workload remains stable")
    void testRandomQueryStress() {
        RouteCore core = createGridCore(20, 20);
        Random random = new Random(2026);

        int queries = 2_000;
        for (int i = 0; i < queries; i++) {
            int source = random.nextInt(400);
            int target = random.nextInt(400);
            RouteResponse response = core.route(RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
            assertTrue(response.getArrivalTicks() >= response.getDepartureTicks());
            assertTrue(response.getTotalCost() >= 0.0f || response.getTotalCost() == Float.POSITIVE_INFINITY);
        }
    }

    @Test
    @Timeout(value = 8, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf smoke: route throughput is practical for medium workload")
    void testPerfSmoke() {
        RouteCore core = createGridCore(15, 15);
        int queries = 1_500;
        long start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            int source = i % 225;
            int target = (i * 17) % 225;
            core.route(RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(0L)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
        }
        long elapsed = System.nanoTime() - start;
        double avgMicros = (elapsed / 1_000.0d) / queries;
        assertTrue(avgMicros < 500.0d, "average query latency should stay below 500us in this smoke test");
    }

    @Test
    @Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Concurrency stress: repeated route calls remain deterministic")
    void testConcurrentDeterminism() throws InterruptedException {
        RouteCore core = createGridCore(12, 12);
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N143")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse baseline = core.route(request);
        int threads = 8;
        int loops = 300;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        RouteResponse current = core.route(request);
                        if (Float.compare(current.getTotalCost(), baseline.getTotalCost()) != 0
                                || current.getArrivalTicks() != baseline.getArrivalTicks()
                                || !current.getPathExternalNodeIds().equals(baseline.getPathExternalNodeIds())) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent route results diverged");
        assertEquals(baseline.getPathExternalNodeIds().get(0), "N0");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 13 stress: pinned-randomized parity remains stable across NONE/EUCLIDEAN/SPHERICAL/LANDMARK")
    void testStage13PinnedRandomizedParityAcrossHeuristicsStress() {
        RouteCore core = createGridCoreWithLandmarks(18, 18, 6, 31L);
        Random random = new Random(13_013L);
        List<HeuristicType> heuristics = List.of(
                HeuristicType.NONE,
                HeuristicType.EUCLIDEAN,
                HeuristicType.SPHERICAL,
                HeuristicType.LANDMARK
        );

        int queries = 300;
        for (int i = 0; i < queries; i++) {
            int source = random.nextInt(324);
            int target = random.nextInt(324);
            long departureTicks = random.nextInt(86_400);

            RouteRequest dijkstraRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(departureTicks)
                    .algorithm(RoutingAlgorithm.DIJKSTRA)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteResponse dijkstra = core.route(dijkstraRequest);

            for (HeuristicType heuristicType : heuristics) {
                RouteResponse aStar = core.route(RouteRequest.builder()
                        .sourceExternalId("N" + source)
                        .targetExternalId("N" + target)
                        .departureTicks(departureTicks)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(heuristicType)
                        .build());

                assertEquals(
                        dijkstra.isReachable(),
                        aStar.isReachable(),
                        "reachability mismatch for query " + i + " heuristic=" + heuristicType
                );
                assertEquals(
                        dijkstra.getTotalCost(),
                        aStar.getTotalCost(),
                        1e-5f,
                        "cost mismatch for query " + i + " heuristic=" + heuristicType
                );
                if (dijkstra.isReachable()) {
                    assertEquals(
                            dijkstra.getArrivalTicks(),
                            aStar.getArrivalTicks(),
                            "arrival mismatch for query " + i + " heuristic=" + heuristicType
                    );
                }
            }
        }
    }

    @Test
    @Timeout(value = 12, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 13 perf smoke: LANDMARK A* throughput remains practical")
    void testStage13LandmarkPerfSmoke() {
        RouteCore core = createGridCoreWithLandmarks(20, 20, 8, 67L);
        int warmupQueries = 200;
        int queries = 1_200;

        for (int i = 0; i < warmupQueries; i++) {
            core.route(RouteRequest.builder()
                    .sourceExternalId("N" + (i % 400))
                    .targetExternalId("N" + ((i * 11) % 400))
                    .departureTicks(i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.LANDMARK)
                    .build());
        }

        long start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            core.route(RouteRequest.builder()
                    .sourceExternalId("N" + (i % 400))
                    .targetExternalId("N" + ((i * 19) % 400))
                    .departureTicks(i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.LANDMARK)
                    .build());
        }
        long elapsed = System.nanoTime() - start;
        double avgMicros = (elapsed / 1_000.0d) / queries;
        assertTrue(avgMicros < 1_500.0d, "average LANDMARK latency should remain below 1500us in this smoke test");
    }

    @Test
    @Timeout(value = 12, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 13 concurrency stress: LANDMARK calls remain deterministic")
    void testStage13ConcurrentLandmarkDeterminism() throws InterruptedException {
        RouteCore core = createGridCoreWithLandmarks(14, 14, 6, 79L);
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N195")
                .departureTicks(15L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.LANDMARK)
                .build();

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
                        if (Float.compare(current.getTotalCost(), baseline.getTotalCost()) != 0
                                || current.getArrivalTicks() != baseline.getArrivalTicks()
                                || !current.getPathExternalNodeIds().equals(baseline.getPathExternalNodeIds())) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS), "LANDMARK concurrency stress timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent LANDMARK route results diverged");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 13 stress: heavy live-overlay churn with expiries keeps A* parity and determinism")
    void testStage13HeavyLiveOverlayChurnStress() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(14, 14);
        LiveOverlay overlay = new LiveOverlay(Math.max(128, fixture.edgeGraph().edgeCount() / 3));
        CostEngine churnCostEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                overlay,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );
        RouteCore core = createCoreForFixture(fixture, null, churnCostEngine, null);

        Random random = new Random(9_977L);
        int edgeCount = fixture.edgeGraph().edgeCount();
        int nodeCount = fixture.edgeGraph().nodeCount();
        int iterations = 220;
        int updatesPerIteration = 96;
        int acceptedTotal = 0;
        int expiredRejectedTotal = 0;

        for (int i = 0; i < iterations; i++) {
            long nowTicks = i * 3L;
            List<LiveUpdate> updates = new ArrayList<>(updatesPerIteration);
            for (int j = 0; j < updatesPerIteration; j++) {
                int edgeId = random.nextInt(edgeCount);
                float speedFactor = random.nextInt(10) == 0
                        ? 0.0f
                        : 0.15f + (random.nextFloat() * 0.85f);
                long validUntilTicks = (j % 7 == 0)
                        ? nowTicks - random.nextInt(3)
                        : nowTicks + 1 + random.nextInt(24);
                updates.add(LiveUpdate.of(edgeId, speedFactor, validUntilTicks));
            }

            LiveOverlay.BatchApplyResult applyResult = overlay.applyBatch(updates, nowTicks);
            acceptedTotal += applyResult.accepted();
            expiredRejectedTotal += applyResult.rejectedExpiredAtIngest();
            assertEquals(
                    updatesPerIteration,
                    applyResult.accepted() + applyResult.rejectedExpiredAtIngest() + applyResult.rejectedCapacity(),
                    "overlay batch accounting mismatch at iteration " + i
            );

            int source = random.nextInt(nodeCount);
            int target = random.nextInt(nodeCount);
            RouteRequest aStarRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build();
            RouteRequest dijkstraRequest = RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(nowTicks)
                    .algorithm(RoutingAlgorithm.DIJKSTRA)
                    .heuristicType(HeuristicType.NONE)
                    .build();

            RouteResponse aStarFirst = core.route(aStarRequest);
            RouteResponse aStarSecond = core.route(aStarRequest);
            RouteResponse dijkstra = core.route(dijkstraRequest);

            assertEquals(aStarFirst.isReachable(), aStarSecond.isReachable(), "A* reachability drift under churn");
            assertEquals(aStarFirst.getTotalCost(), aStarSecond.getTotalCost(), 1e-6f, "A* cost drift under churn");
            assertEquals(aStarFirst.getArrivalTicks(), aStarSecond.getArrivalTicks(), "A* arrival drift under churn");
            assertEquals(
                    aStarFirst.getPathExternalNodeIds(),
                    aStarSecond.getPathExternalNodeIds(),
                    "A* path drift under churn"
            );

            assertEquals(dijkstra.isReachable(), aStarFirst.isReachable(), "A*/Dijkstra reachability mismatch under churn");
            assertEquals(dijkstra.getTotalCost(), aStarFirst.getTotalCost(), 1e-5f, "A*/Dijkstra cost mismatch under churn");
            if (dijkstra.isReachable()) {
                assertEquals(
                        dijkstra.getArrivalTicks(),
                        aStarFirst.getArrivalTicks(),
                        "A*/Dijkstra arrival mismatch under churn"
                );
            }
        }

        assertTrue(acceptedTotal > 0, "overlay churn test should ingest accepted updates");
        assertTrue(expiredRejectedTotal > 0, "overlay churn test should ingest expired updates");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 13 gate: pinned workload p95 settled-state not worse than Stage 12 baseline with telemetry")
    void testStage13BaselineDeltaReportAgainstStage12() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(18, 18);
        RouteCore stage12AStarCore = createCoreForFixture(
                fixture,
                null,
                fixture.costEngine(),
                new EdgeBasedRoutePlanner(true)
        );
        RouteCore stage13AStarCore = createCoreForFixture(fixture, null, fixture.costEngine(), null);

        List<RandomQuery> queries = buildPinnedRandomQueries(fixture.edgeGraph().nodeCount(), 320, 17_013L);
        warmupAStar(stage12AStarCore, queries, 80);
        warmupAStar(stage13AStarCore, queries, 80);

        PlannerBatchMetrics stage12Metrics = runAStarBatch(stage12AStarCore, queries);
        PlannerBatchMetrics stage13Metrics = runAStarBatch(stage13AStarCore, queries);

        for (int i = 0; i < queries.size(); i++) {
            QueryOutcome stage12 = stage12Metrics.outcomes()[i];
            QueryOutcome stage13 = stage13Metrics.outcomes()[i];
            assertEquals(stage12.reachable(), stage13.reachable(), "reachability mismatch at query index " + i);
            assertEquals(stage12.totalCost(), stage13.totalCost(), 1e-5f, "cost mismatch at query index " + i);
            if (stage12.reachable()) {
                assertEquals(stage12.arrivalTicks(), stage13.arrivalTicks(), "arrival mismatch at query index " + i);
            }
        }

        int stage12P50 = percentile(stage12Metrics.settledStates(), 50);
        int stage12P95 = percentile(stage12Metrics.settledStates(), 95);
        int stage13P50 = percentile(stage13Metrics.settledStates(), 50);
        int stage13P95 = percentile(stage13Metrics.settledStates(), 95);
        assertTrue(
                stage13P95 <= stage12P95,
                "Stage 13 p95 settled-states should not exceed Stage 12 baseline: "
                        + stage13P95 + " > " + stage12P95
        );

        long stage12PeakDeltaBytes = Math.max(0L, stage12Metrics.heapPeakBytes() - stage12Metrics.heapStartBytes());
        long stage13PeakDeltaBytes = Math.max(0L, stage13Metrics.heapPeakBytes() - stage13Metrics.heapStartBytes());
        double stage12PeakDeltaPerQuery = stage12PeakDeltaBytes / (double) queries.size();
        double stage13PeakDeltaPerQuery = stage13PeakDeltaBytes / (double) queries.size();

        System.out.printf(
                "STAGE13_DELTA_REPORT queries=%d stage12_p50=%d stage12_p95=%d stage13_p50=%d stage13_p95=%d "
                        + "stage12_heap_peak_delta_bytes=%d stage13_heap_peak_delta_bytes=%d "
                        + "stage12_heap_peak_delta_per_query_bytes=%.2f stage13_heap_peak_delta_per_query_bytes=%.2f%n",
                queries.size(),
                stage12P50,
                stage12P95,
                stage13P50,
                stage13P95,
                stage12PeakDeltaBytes,
                stage13PeakDeltaBytes,
                stage12PeakDeltaPerQuery,
                stage13PeakDeltaPerQuery
        );
    }

    private RouteCore createGridCore(int rows, int cols) {
        return createGridCoreWithLandmarks(rows, cols, 0, 0L);
    }

    private RouteCore createGridCoreWithLandmarks(int rows, int cols, int landmarkCount, long seed) {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(rows, cols);
        LandmarkStore landmarkStore = null;
        if (landmarkCount > 0) {
            LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                    fixture.edgeGraph(),
                    fixture.profileStore(),
                    LandmarkPreprocessorConfig.builder()
                            .landmarkCount(landmarkCount)
                            .selectionSeed(seed)
                            .build()
            );
            landmarkStore = LandmarkStore.fromArtifact(artifact);
        }

        return createCoreForFixture(fixture, landmarkStore, fixture.costEngine(), null);
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

        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
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
        return fixture;
    }

    private RouteCore createCoreForFixture(
            RoutingFixtureFactory.Fixture fixture,
            LandmarkStore landmarkStore,
            CostEngine costEngine,
            RoutePlanner aStarPlanner
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(costEngine)
                .nodeIdMapper(fixture.nodeIdMapper())
                .landmarkStore(landmarkStore);
        if (aStarPlanner != null) {
            builder.aStarPlanner(aStarPlanner);
        }
        return builder.build();
    }

    private List<RandomQuery> buildPinnedRandomQueries(int nodeCount, int queryCount, long seed) {
        Random random = new Random(seed);
        List<RandomQuery> queries = new ArrayList<>(queryCount);
        for (int i = 0; i < queryCount; i++) {
            queries.add(new RandomQuery(
                    random.nextInt(nodeCount),
                    random.nextInt(nodeCount),
                    random.nextInt(86_400)
            ));
        }
        return queries;
    }

    private void warmupAStar(RouteCore core, List<RandomQuery> queries, int warmupQueries) {
        int cappedWarmup = Math.min(warmupQueries, queries.size());
        for (int i = 0; i < cappedWarmup; i++) {
            RandomQuery query = queries.get(i);
            core.route(RouteRequest.builder()
                    .sourceExternalId("N" + query.sourceNodeId())
                    .targetExternalId("N" + query.targetNodeId())
                    .departureTicks(query.departureTicks())
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
        }
    }

    private PlannerBatchMetrics runAStarBatch(RouteCore core, List<RandomQuery> queries) {
        QueryOutcome[] outcomes = new QueryOutcome[queries.size()];
        int[] settledStates = new int[queries.size()];
        long heapStartBytes = usedHeapBytes();
        long heapPeakBytes = heapStartBytes;

        for (int i = 0; i < queries.size(); i++) {
            RandomQuery query = queries.get(i);
            RouteResponse response = core.route(RouteRequest.builder()
                    .sourceExternalId("N" + query.sourceNodeId())
                    .targetExternalId("N" + query.targetNodeId())
                    .departureTicks(query.departureTicks())
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
            outcomes[i] = new QueryOutcome(
                    response.isReachable(),
                    response.getTotalCost(),
                    response.getArrivalTicks()
            );
            settledStates[i] = response.getSettledStates();

            if ((i & 7) == 0 || i == queries.size() - 1) {
                heapPeakBytes = Math.max(heapPeakBytes, usedHeapBytes());
            }
        }

        long heapEndBytes = usedHeapBytes();
        return new PlannerBatchMetrics(outcomes, settledStates, heapStartBytes, heapPeakBytes, heapEndBytes);
    }

    private static int percentile(int[] values, int percentile) {
        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0d) * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record RandomQuery(int sourceNodeId, int targetNodeId, long departureTicks) {
    }

    private record QueryOutcome(boolean reachable, float totalCost, long arrivalTicks) {
    }

    private record PlannerBatchMetrics(
            QueryOutcome[] outcomes,
            int[] settledStates,
            long heapStartBytes,
            long heapPeakBytes,
            long heapEndBytes
    ) {
    }
}
