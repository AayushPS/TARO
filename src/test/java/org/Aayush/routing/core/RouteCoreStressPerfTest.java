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
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
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

@DisplayName("Stage 13 and Stage 14 RouteCore Stress and Perf Tests")
class RouteCoreStressPerfTest {
    private static final float MATRIX_COST_TOLERANCE = 1e-5f;
    private static final double H14_MIN_THROUGHPUT_GAIN = 3.0d;
    private static final double H14_MAX_P95_WORK_PER_CELL_RATIO = 0.70d;
    private static final double H14_MAX_HEAP_DELTA_PER_CELL_RATIO = 0.60d;

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

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 stress: randomized matrix workload remains complete and deterministic")
    void testStage14RandomizedMatrixCompletenessStress() {
        RouteCore core = createGridCore(16, 16);
        List<MatrixRandomQuery> queries = buildPinnedMatrixQueries(
                16 * 16,
                140,
                1,
                8,
                1,
                18,
                14_101L,
                true
        );

        for (int i = 0; i < queries.size(); i++) {
            MatrixRandomQuery query = queries.get(i);
            MatrixRequest request = toMatrixRequest(query, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE);
            MatrixResponse first = core.matrix(request);
            MatrixResponse second = core.matrix(request);

            assertEquals(
                    OneToManyDijkstraMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE,
                    first.getImplementationNote(),
                    "unexpected implementation note at query " + i
            );
            assertMatrixCompleteness(first, query.departureTicks(), "query " + i);
            assertMatrixResponseParity(first, second, MATRIX_COST_TOLERANCE, "determinism query " + i);
        }
    }

    @Test
    @Timeout(value = 25, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 correctness: Dijkstra matrix matches Stage 12 pairwise oracle on pinned suites")
    void testStage14DijkstraMatrixParityAgainstPairwiseOracle() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(18, 18);
        RouteCore stage12PairwiseCore = createCoreForFixture(
                fixture,
                null,
                fixture.costEngine(),
                null,
                new TemporaryMatrixPlanner()
        );
        RouteCore stage14Core = createCoreForFixture(fixture, null, fixture.costEngine(), null, null);
        List<MatrixRandomQuery> queries = buildPinnedMatrixQueries(
                fixture.edgeGraph().nodeCount(),
                70,
                2,
                7,
                6,
                24,
                14_102L,
                true
        );

        for (int i = 0; i < queries.size(); i++) {
            MatrixRandomQuery query = queries.get(i);
            MatrixResponse oracle = stage12PairwiseCore.matrix(
                    toMatrixRequest(query, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE)
            );
            MatrixResponse stage14 = stage14Core.matrix(
                    toMatrixRequest(query, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE)
            );

            assertEquals(
                    OneToManyDijkstraMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE,
                    stage14.getImplementationNote(),
                    "unexpected implementation note at query " + i
            );
            assertMatrixResponseParity(oracle, stage14, MATRIX_COST_TOLERANCE, "query " + i);
        }
    }

    @Test
    @Timeout(value = 25, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 correctness: A* matrix compatibility mode preserves Dijkstra oracle parity")
    void testStage14AStarCompatibilityParityAgainstDijkstraOracle() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(18, 18);
        RouteCore stage12PairwiseCore = createCoreForFixture(
                fixture,
                null,
                fixture.costEngine(),
                null,
                new TemporaryMatrixPlanner()
        );
        RouteCore stage14Core = createCoreForFixture(fixture, null, fixture.costEngine(), null, null);
        List<MatrixRandomQuery> queries = buildPinnedMatrixQueries(
                fixture.edgeGraph().nodeCount(),
                70,
                2,
                7,
                6,
                24,
                14_103L,
                true
        );

        for (int i = 0; i < queries.size(); i++) {
            MatrixRandomQuery query = queries.get(i);
            MatrixResponse dijkstraOracle = stage12PairwiseCore.matrix(
                    toMatrixRequest(query, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE)
            );
            MatrixResponse aStarCompatibility = stage14Core.matrix(
                    toMatrixRequest(query, RoutingAlgorithm.A_STAR, HeuristicType.NONE)
            );

            assertEquals(
                    TemporaryMatrixPlanner.PAIRWISE_COMPATIBILITY_NOTE,
                    aStarCompatibility.getImplementationNote(),
                    "unexpected implementation note at query " + i
            );
            assertMatrixResponseParity(
                    dijkstraOracle,
                    aStarCompatibility,
                    MATRIX_COST_TOLERANCE,
                    "A* compatibility query " + i
            );
        }
    }

    @Test
    @Timeout(value = 45, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 stress: concurrent matrix calls remain deterministic")
    void testStage14ConcurrentMatrixDeterminism() throws InterruptedException {
        RouteCore core = createGridCore(14, 14);
        MatrixRequest request = MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N7")
                .sourceExternalId("N35")
                .sourceExternalId("N91")
                .targetExternalId("N12")
                .targetExternalId("N44")
                .targetExternalId("N77")
                .targetExternalId("N103")
                .targetExternalId("N120")
                .targetExternalId("N159")
                .targetExternalId("N195")
                .departureTicks(22L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();

        MatrixResponse baseline = core.matrix(request);
        // Fixed stress profile keeps deterministic multi-thread pressure without making the
        // test hardware-quota sensitive on constrained CI/dev runtimes.
        int threads = 4;
        int loops = 90;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        MatrixResponse current = core.matrix(request);
                        if (!matrixResponsesEqual(baseline, current, MATRIX_COST_TOLERANCE)) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS),
                "matrix concurrency stress timed out (threads=" + threads + ", loops=" + loops + ")"
        );
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent matrix responses diverged");
    }

    @Test
    @Timeout(value = 25, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 stress: live-overlay churn preserves matrix parity and deterministic replay")
    void testStage14LiveOverlayChurnMatrixParityStress() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(12, 12);
        LiveOverlay overlay = new LiveOverlay(Math.max(128, fixture.edgeGraph().edgeCount() / 2));
        CostEngine churnCostEngine = new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                overlay,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS
        );
        RouteCore stage14Core = createCoreForFixture(fixture, null, churnCostEngine, null, null);
        RouteCore stage12PairwiseCore = createCoreForFixture(
                fixture,
                null,
                churnCostEngine,
                null,
                new TemporaryMatrixPlanner()
        );

        Random random = new Random(14_104L);
        int edgeCount = fixture.edgeGraph().edgeCount();
        int nodeCount = fixture.edgeGraph().nodeCount();
        int iterations = 120;
        int updatesPerIteration = 72;
        int acceptedTotal = 0;
        int expiredRejectedTotal = 0;

        for (int i = 0; i < iterations; i++) {
            long nowTicks = i * 4L;
            List<LiveUpdate> updates = new ArrayList<>(updatesPerIteration);
            for (int j = 0; j < updatesPerIteration; j++) {
                int edgeId = random.nextInt(edgeCount);
                float speedFactor = random.nextInt(12) == 0
                        ? 0.0f
                        : 0.20f + (random.nextFloat() * 0.80f);
                long validUntilTicks = (j % 9 == 0)
                        ? nowTicks - random.nextInt(4)
                        : nowTicks + 1 + random.nextInt(18);
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

            MatrixRandomQuery query = randomMatrixQuery(
                    random,
                    nodeCount,
                    2,
                    5,
                    5,
                    11,
                    true,
                    nowTicks
            );
            MatrixRequest request = toMatrixRequest(query, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE);

            MatrixResponse stage14First = stage14Core.matrix(request);
            MatrixResponse stage14Second = stage14Core.matrix(request);
            MatrixResponse oracle = stage12PairwiseCore.matrix(request);

            assertMatrixResponseParity(stage14First, stage14Second, MATRIX_COST_TOLERANCE, "replay iteration " + i);
            assertMatrixResponseParity(oracle, stage14First, MATRIX_COST_TOLERANCE, "oracle iteration " + i);
        }

        assertTrue(acceptedTotal > 0, "overlay churn test should ingest accepted updates");
        assertTrue(expiredRejectedTotal > 0, "overlay churn test should ingest expired updates");
    }

    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stage 14 gate: pinned matrix workload beats Stage 12 pairwise baseline with telemetry")
    void testStage14BaselineDeltaReportAgainstStage12Pairwise() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(18, 18);
        RouteCore stage12PairwiseCore = createCoreForFixture(
                fixture,
                null,
                fixture.costEngine(),
                null,
                new TemporaryMatrixPlanner()
        );
        RouteCore stage14Core = createCoreForFixture(fixture, null, fixture.costEngine(), null, null);

        List<MatrixRandomQuery> queries = buildPinnedMatrixQueries(
                fixture.edgeGraph().nodeCount(),
                52,
                4,
                8,
                18,
                30,
                14_105L,
                true
        );
        warmupMatrix(stage12PairwiseCore, queries, 12, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE);
        warmupMatrix(stage14Core, queries, 12, RoutingAlgorithm.DIJKSTRA, HeuristicType.NONE);

        stabilizeHeap();
        MatrixBatchMetrics stage12Metrics = runMatrixBatch(
                stage12PairwiseCore,
                queries,
                RoutingAlgorithm.DIJKSTRA,
                HeuristicType.NONE
        );
        stabilizeHeap();
        MatrixBatchMetrics stage14Metrics = runMatrixBatch(
                stage14Core,
                queries,
                RoutingAlgorithm.DIJKSTRA,
                HeuristicType.NONE
        );

        for (int i = 0; i < queries.size(); i++) {
            assertMatrixResponseParity(
                    stage12Metrics.outcomes()[i],
                    stage14Metrics.outcomes()[i],
                    MATRIX_COST_TOLERANCE,
                    "delta query " + i
            );
        }

        double stage12Millis = stage12Metrics.elapsedNanos() / 1_000_000.0d;
        double stage14Millis = stage14Metrics.elapsedNanos() / 1_000_000.0d;
        double throughputGain = stage12Millis / stage14Millis;
        assertTrue(
                throughputGain >= H14_MIN_THROUGHPUT_GAIN,
                "expected Stage 14 throughput gain >= " + H14_MIN_THROUGHPUT_GAIN
                        + "x but was " + String.format("%.3f", throughputGain) + "x"
        );

        double[] stage12SettledPerCell = settledStatesPerCell(stage12Metrics.executionStats(), queries);
        double[] stage14WorkPerCell = workStatesPerCell(stage14Metrics.executionStats(), queries);
        double stage12P95SettledPerCell = percentile(stage12SettledPerCell, 95);
        double stage14P95WorkPerCell = percentile(stage14WorkPerCell, 95);
        assertTrue(stage12P95SettledPerCell > 0.0d, "stage12 p95 settled/cell must be > 0 for gate comparability");
        double p95WorkRatio = stage14P95WorkPerCell / stage12P95SettledPerCell;
        assertTrue(
                p95WorkRatio <= H14_MAX_P95_WORK_PER_CELL_RATIO,
                "expected Stage 14 p95 work/cell ratio <= " + H14_MAX_P95_WORK_PER_CELL_RATIO
                        + " but was " + String.format("%.3f", p95WorkRatio)
        );

        long stage12PeakDeltaBytes = Math.max(0L, stage12Metrics.heapPeakBytes() - stage12Metrics.heapStartBytes());
        long stage14PeakDeltaBytes = Math.max(0L, stage14Metrics.heapPeakBytes() - stage14Metrics.heapStartBytes());
        double stage12PeakDeltaPerCell = stage12PeakDeltaBytes / (double) stage12Metrics.totalCells();
        double stage14PeakDeltaPerCell = stage14PeakDeltaBytes / (double) stage14Metrics.totalCells();
        assertTrue(stage12PeakDeltaPerCell > 0.0d, "stage12 heap delta/cell must be > 0 for gate comparability");
        double heapRatio = stage14PeakDeltaPerCell / stage12PeakDeltaPerCell;
        assertTrue(
                heapRatio <= H14_MAX_HEAP_DELTA_PER_CELL_RATIO,
                "expected Stage 14 heap delta/cell ratio <= " + H14_MAX_HEAP_DELTA_PER_CELL_RATIO
                        + " but was " + String.format("%.3f", heapRatio)
        );

        System.out.printf(
                "STAGE14_DELTA_REPORT queries=%d cells=%d stage12_ms=%.2f stage14_ms=%.2f throughput_gain_x=%.3f "
                        + "stage12_p95_settled_per_cell=%.4f stage14_p95_work_per_cell=%.4f p95_work_ratio=%.4f "
                        + "stage12_heap_peak_delta_bytes=%d stage14_heap_peak_delta_bytes=%d "
                        + "stage12_heap_peak_delta_per_cell_bytes=%.4f stage14_heap_peak_delta_per_cell_bytes=%.4f "
                        + "heap_ratio=%.4f%n",
                queries.size(),
                stage12Metrics.totalCells(),
                stage12Millis,
                stage14Millis,
                throughputGain,
                stage12P95SettledPerCell,
                stage14P95WorkPerCell,
                p95WorkRatio,
                stage12PeakDeltaBytes,
                stage14PeakDeltaBytes,
                stage12PeakDeltaPerCell,
                stage14PeakDeltaPerCell,
                heapRatio
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
        return createCoreForFixture(fixture, landmarkStore, costEngine, aStarPlanner, null);
    }

    private RouteCore createCoreForFixture(
            RoutingFixtureFactory.Fixture fixture,
            LandmarkStore landmarkStore,
            CostEngine costEngine,
            RoutePlanner aStarPlanner,
            MatrixPlanner matrixPlanner
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(costEngine)
                .nodeIdMapper(fixture.nodeIdMapper())
                .landmarkStore(landmarkStore)
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc());
        if (aStarPlanner != null) {
            builder.aStarPlanner(aStarPlanner);
        }
        if (matrixPlanner != null) {
            builder.matrixPlanner(matrixPlanner);
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

    private List<MatrixRandomQuery> buildPinnedMatrixQueries(
            int nodeCount,
            int queryCount,
            int minSources,
            int maxSources,
            int minTargets,
            int maxTargets,
            long seed,
            boolean allowDuplicates
    ) {
        Random random = new Random(seed);
        List<MatrixRandomQuery> queries = new ArrayList<>(queryCount);
        for (int i = 0; i < queryCount; i++) {
            long departureTicks = random.nextInt(86_400);
            queries.add(
                    randomMatrixQuery(
                            random,
                            nodeCount,
                            minSources,
                            maxSources,
                            minTargets,
                            maxTargets,
                            allowDuplicates,
                            departureTicks
                    )
            );
        }
        return queries;
    }

    private MatrixRandomQuery randomMatrixQuery(
            Random random,
            int nodeCount,
            int minSources,
            int maxSources,
            int minTargets,
            int maxTargets,
            boolean allowDuplicates,
            long departureTicks
    ) {
        int[] sourceNodeIds = randomNodeArray(random, nodeCount, minSources, maxSources, allowDuplicates);
        int[] targetNodeIds = randomNodeArray(random, nodeCount, minTargets, maxTargets, allowDuplicates);
        return new MatrixRandomQuery(sourceNodeIds, targetNodeIds, departureTicks);
    }

    private int[] randomNodeArray(Random random, int nodeCount, int minCount, int maxCount, boolean allowDuplicates) {
        int count = minCount + random.nextInt((maxCount - minCount) + 1);
        int[] nodeIds = new int[count];
        for (int i = 0; i < count; i++) {
            if (allowDuplicates && i > 0 && random.nextInt(5) == 0) {
                nodeIds[i] = nodeIds[random.nextInt(i)];
            } else {
                nodeIds[i] = random.nextInt(nodeCount);
            }
        }
        return nodeIds;
    }

    private MatrixRequest toMatrixRequest(
            MatrixRandomQuery query,
            RoutingAlgorithm algorithm,
            HeuristicType heuristicType
    ) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .departureTicks(query.departureTicks())
                .algorithm(algorithm)
                .heuristicType(heuristicType);
        for (int sourceNodeId : query.sourceNodeIds()) {
            builder.sourceExternalId("N" + sourceNodeId);
        }
        for (int targetNodeId : query.targetNodeIds()) {
            builder.targetExternalId("N" + targetNodeId);
        }
        return builder.build();
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

    private void warmupMatrix(
            RouteCore core,
            List<MatrixRandomQuery> queries,
            int warmupQueries,
            RoutingAlgorithm algorithm,
            HeuristicType heuristicType
    ) {
        int cappedWarmup = Math.min(warmupQueries, queries.size());
        for (int i = 0; i < cappedWarmup; i++) {
            core.matrix(toMatrixRequest(queries.get(i), algorithm, heuristicType));
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

    private MatrixBatchMetrics runMatrixBatch(
            RouteCore core,
            List<MatrixRandomQuery> queries,
            RoutingAlgorithm algorithm,
            HeuristicType heuristicType
    ) {
        MatrixOutcome[] outcomes = new MatrixOutcome[queries.size()];
        MatrixExecutionStats[] executionStats = new MatrixExecutionStats[queries.size()];
        long totalCells = 0L;
        long heapStartBytes = usedHeapBytes();
        long heapPeakBytes = heapStartBytes;
        long startNanos = System.nanoTime();

        for (int i = 0; i < queries.size(); i++) {
            MatrixRandomQuery query = queries.get(i);
            MatrixResponse response = core.matrix(toMatrixRequest(query, algorithm, heuristicType));
            outcomes[i] = MatrixOutcome.from(response);
            executionStats[i] = core.matrixExecutionStatsContract();
            totalCells += query.cellCount();

            if ((i & 3) == 0 || i == queries.size() - 1) {
                heapPeakBytes = Math.max(heapPeakBytes, usedHeapBytes());
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        long heapEndBytes = usedHeapBytes();
        return new MatrixBatchMetrics(
                outcomes,
                executionStats,
                elapsedNanos,
                heapStartBytes,
                heapPeakBytes,
                heapEndBytes,
                totalCells
        );
    }

    private double[] settledStatesPerCell(MatrixExecutionStats[] executionStats, List<MatrixRandomQuery> queries) {
        double[] perCell = new double[executionStats.length];
        for (int i = 0; i < executionStats.length; i++) {
            long cells = Math.max(1L, queries.get(i).cellCount());
            perCell[i] = executionStats[i].requestSettledStates() / (double) cells;
        }
        return perCell;
    }

    private double[] workStatesPerCell(MatrixExecutionStats[] executionStats, List<MatrixRandomQuery> queries) {
        double[] perCell = new double[executionStats.length];
        for (int i = 0; i < executionStats.length; i++) {
            long cells = Math.max(1L, queries.get(i).cellCount());
            perCell[i] = executionStats[i].requestWorkStates() / (double) cells;
        }
        return perCell;
    }

    private void assertMatrixCompleteness(MatrixResponse response, long departureTicks, String context) {
        boolean[][] reachable = response.getReachable();
        float[][] totalCosts = response.getTotalCosts();
        long[][] arrivals = response.getArrivalTicks();

        assertEquals(reachable.length, totalCosts.length, context + " row mismatch between reachable and totalCosts");
        assertEquals(reachable.length, arrivals.length, context + " row mismatch between reachable and arrivalTicks");

        for (int row = 0; row < reachable.length; row++) {
            assertEquals(
                    reachable[row].length,
                    totalCosts[row].length,
                    context + " column mismatch in row " + row + " between reachable and totalCosts"
            );
            assertEquals(
                    reachable[row].length,
                    arrivals[row].length,
                    context + " column mismatch in row " + row + " between reachable and arrivalTicks"
            );

            for (int col = 0; col < reachable[row].length; col++) {
                float cost = totalCosts[row][col];
                long arrival = arrivals[row][col];
                if (reachable[row][col]) {
                    assertTrue(Float.isFinite(cost), context + " reachable cell must have finite cost at [" + row + "," + col + "]");
                    assertTrue(cost >= 0.0f, context + " reachable cell must have non-negative cost at [" + row + "," + col + "]");
                    assertTrue(
                            arrival >= departureTicks,
                            context + " reachable cell must have arrival >= departure at [" + row + "," + col + "]"
                    );
                } else {
                    assertEquals(
                            Float.POSITIVE_INFINITY,
                            cost,
                            context + " unreachable cost sentinel mismatch at [" + row + "," + col + "]"
                    );
                    assertEquals(
                            departureTicks,
                            arrival,
                            context + " unreachable arrival sentinel mismatch at [" + row + "," + col + "]"
                    );
                }
            }
        }
    }

    private void assertMatrixResponseParity(
            MatrixResponse expected,
            MatrixResponse actual,
            float costTolerance,
            String context
    ) {
        assertMatrixResponseParity(MatrixOutcome.from(expected), MatrixOutcome.from(actual), costTolerance, context);
    }

    private void assertMatrixResponseParity(
            MatrixOutcome expected,
            MatrixOutcome actual,
            float costTolerance,
            String context
    ) {
        boolean[][] expectedReachable = expected.reachable();
        boolean[][] actualReachable = actual.reachable();
        float[][] expectedCosts = expected.totalCosts();
        float[][] actualCosts = actual.totalCosts();
        long[][] expectedArrivals = expected.arrivalTicks();
        long[][] actualArrivals = actual.arrivalTicks();

        assertEquals(expectedReachable.length, actualReachable.length, context + " row count mismatch");
        assertEquals(expectedCosts.length, actualCosts.length, context + " cost row count mismatch");
        assertEquals(expectedArrivals.length, actualArrivals.length, context + " arrival row count mismatch");

        for (int row = 0; row < expectedReachable.length; row++) {
            assertEquals(expectedReachable[row].length, actualReachable[row].length, context + " col count mismatch row " + row);
            assertEquals(expectedCosts[row].length, actualCosts[row].length, context + " cost col count mismatch row " + row);
            assertEquals(
                    expectedArrivals[row].length,
                    actualArrivals[row].length,
                    context + " arrival col count mismatch row " + row
            );

            for (int col = 0; col < expectedReachable[row].length; col++) {
                assertEquals(
                        expectedReachable[row][col],
                        actualReachable[row][col],
                        context + " reachability mismatch at [" + row + "," + col + "]"
                );

                float expectedCost = expectedCosts[row][col];
                float actualCost = actualCosts[row][col];
                if (Float.isFinite(expectedCost) && Float.isFinite(actualCost)) {
                    assertEquals(
                            expectedCost,
                            actualCost,
                            costTolerance,
                            context + " cost mismatch at [" + row + "," + col + "]"
                    );
                } else {
                    assertEquals(expectedCost, actualCost, context + " non-finite cost mismatch at [" + row + "," + col + "]");
                }

                assertEquals(
                        expectedArrivals[row][col],
                        actualArrivals[row][col],
                        context + " arrival mismatch at [" + row + "," + col + "]"
                );
            }
        }
    }

    private boolean matrixResponsesEqual(MatrixResponse baseline, MatrixResponse candidate, float costTolerance) {
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
                float baselineCost = baselineCosts[row][col];
                float candidateCost = candidateCosts[row][col];
                if (Float.isFinite(baselineCost) && Float.isFinite(candidateCost)) {
                    if (Math.abs(baselineCost - candidateCost) > costTolerance) {
                        return false;
                    }
                } else if (Float.compare(baselineCost, candidateCost) != 0) {
                    return false;
                }
                if (baselineArrivals[row][col] != candidateArrivals[row][col]) {
                    return false;
                }
            }
        }
        return true;
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

    private static double percentile(double[] values, int percentile) {
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0d) * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    private static void stabilizeHeap() {
        System.gc();
        try {
            Thread.sleep(20L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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

    private record MatrixRandomQuery(int[] sourceNodeIds, int[] targetNodeIds, long departureTicks) {
        long cellCount() {
            return (long) sourceNodeIds.length * targetNodeIds.length;
        }
    }

    private record MatrixOutcome(
            boolean[][] reachable,
            float[][] totalCosts,
            long[][] arrivalTicks
    ) {
        static MatrixOutcome from(MatrixResponse response) {
            return new MatrixOutcome(
                    response.getReachable(),
                    response.getTotalCosts(),
                    response.getArrivalTicks()
            );
        }
    }

    private record MatrixBatchMetrics(
            MatrixOutcome[] outcomes,
            MatrixExecutionStats[] executionStats,
            long elapsedNanos,
            long heapStartBytes,
            long heapPeakBytes,
            long heapEndBytes,
            long totalCells
    ) {
    }
}
