package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 Temporal Trait Stress and Perf Tests")
class Stage16TemporalTraitStressPerfTest {
    private static final double MAX_ROUTE_AVG_MICROS = 3_500.0d;
    private static final double MAX_MATRIX_AVG_MILLIS = 120.0d;
    private static final double MAX_CALENDAR_TO_LINEAR_RATIO = 3.0d;

    @Test
    @Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Concurrency stress: locked temporal mode remains deterministic")
    void testLockedTemporalModeConcurrencyDeterminism() throws InterruptedException {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        RouteCore core = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarModelTimezone("America/New_York"))
                .build();

        long departureTicks = 1_778_313_600L; // 2026-05-10T00:00:00Z
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteResponse baseline = core.route(request);

        int threads = 8;
        int loops = 250;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        RouteResponse current = core.route(request);
                        if (current.isReachable() != baseline.isReachable()
                                || Float.compare(current.getTotalCost(), baseline.getTotalCost()) != 0
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

        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS), "temporal concurrency run timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "locked temporal mode produced non-deterministic outputs under concurrency");
        assertEquals("N0", baseline.getPathExternalNodeIds().get(0));
    }

    @Test
    @Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Matrix concurrency stress: locked temporal mode remains deterministic")
    void testLockedTemporalModeMatrixConcurrencyDeterminism() throws InterruptedException {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        RouteCore core = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarModelTimezone("America/New_York"))
                .build();

        MatrixRequest request = MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N1")
                .targetExternalId("N3")
                .targetExternalId("N4")
                .departureTicks(1_778_313_600L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixResponse baseline = core.matrix(request);

        int threads = 8;
        int loops = 150;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        MatrixResponse current = core.matrix(request);
                        if (!matrixEquals(baseline, current)) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS), "temporal matrix concurrency run timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "locked temporal mode produced non-deterministic matrix outputs under concurrency");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf smoke: CALENDAR route mode remains practical and bounded against LINEAR")
    void testStage16RoutePerfSmokeCalendarVsLinear() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(18, 18);
        RouteCore linearCore = createCore(fixture, TemporalRuntimeConfig.linear());
        RouteCore calendarUtcCore = createCore(fixture, TemporalRuntimeConfig.calendarUtc());

        int nodeCount = 18 * 18;
        int warmupQueries = 250;
        int measuredQueries = 1_200;
        runRouteBatch(linearCore, warmupQueries, nodeCount, 1L);
        runRouteBatch(calendarUtcCore, warmupQueries, nodeCount, 1L);

        long linearElapsed = runRouteBatch(linearCore, measuredQueries, nodeCount, 10_000L);
        long calendarElapsed = runRouteBatch(calendarUtcCore, measuredQueries, nodeCount, 20_000L);

        double linearAvgMicros = (linearElapsed / 1_000.0d) / measuredQueries;
        double calendarAvgMicros = (calendarElapsed / 1_000.0d) / measuredQueries;

        assertTrue(
                linearAvgMicros < MAX_ROUTE_AVG_MICROS,
                "LINEAR route average latency should remain below " + MAX_ROUTE_AVG_MICROS + "us in this smoke test"
        );
        assertTrue(
                calendarAvgMicros < MAX_ROUTE_AVG_MICROS,
                "CALENDAR route average latency should remain below " + MAX_ROUTE_AVG_MICROS + "us in this smoke test"
        );
        assertTrue(
                calendarAvgMicros <= (linearAvgMicros * MAX_CALENDAR_TO_LINEAR_RATIO) + 1.0d,
                "CALENDAR route latency regressed disproportionately vs LINEAR baseline"
        );
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf smoke: CALENDAR matrix mode remains practical and bounded against LINEAR")
    void testStage16MatrixPerfSmokeCalendarVsLinear() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(16, 16);
        RouteCore linearCore = createCore(fixture, TemporalRuntimeConfig.linear());
        RouteCore calendarUtcCore = createCore(fixture, TemporalRuntimeConfig.calendarUtc());

        int nodeCount = 16 * 16;
        MatrixRequest request = buildMatrixPerfRequest(nodeCount, 8, 8, 1_778_313_600L);
        MatrixResponse linearBaseline = linearCore.matrix(request);
        MatrixResponse calendarBaseline = calendarUtcCore.matrix(request);
        assertTrue(matrixEquals(linearBaseline, calendarBaseline), "LINEAR and CALENDAR parity drifted for all-day profile");

        int warmupLoops = 8;
        int measuredLoops = 24;
        runMatrixBatch(linearCore, request, warmupLoops);
        runMatrixBatch(calendarUtcCore, request, warmupLoops);

        long linearElapsed = runMatrixBatch(linearCore, request, measuredLoops);
        long calendarElapsed = runMatrixBatch(calendarUtcCore, request, measuredLoops);
        double linearAvgMillis = (linearElapsed / 1_000_000.0d) / measuredLoops;
        double calendarAvgMillis = (calendarElapsed / 1_000_000.0d) / measuredLoops;

        assertTrue(
                linearAvgMillis < MAX_MATRIX_AVG_MILLIS,
                "LINEAR matrix average latency should remain below " + MAX_MATRIX_AVG_MILLIS + "ms in this smoke test"
        );
        assertTrue(
                calendarAvgMillis < MAX_MATRIX_AVG_MILLIS,
                "CALENDAR matrix average latency should remain below " + MAX_MATRIX_AVG_MILLIS + "ms in this smoke test"
        );
        assertTrue(
                calendarAvgMillis <= (linearAvgMillis * MAX_CALENDAR_TO_LINEAR_RATIO) + 2.0d,
                "CALENDAR matrix latency regressed disproportionately vs LINEAR baseline"
        );
    }

    private boolean matrixEquals(MatrixResponse expected, MatrixResponse actual) {
        if (!expected.getSourceExternalIds().equals(actual.getSourceExternalIds())
                || !expected.getTargetExternalIds().equals(actual.getTargetExternalIds())
                || !expected.getImplementationNote().equals(actual.getImplementationNote())) {
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

        for (int i = 0; i < expectedReachable.length; i++) {
            if (expectedReachable[i].length != actualReachable[i].length
                    || expectedCosts[i].length != actualCosts[i].length
                    || expectedArrivals[i].length != actualArrivals[i].length) {
                return false;
            }
            for (int j = 0; j < expectedReachable[i].length; j++) {
                if (expectedReachable[i][j] != actualReachable[i][j]) {
                    return false;
                }
                if (Float.compare(expectedCosts[i][j], actualCosts[i][j]) != 0) {
                    return false;
                }
                if (expectedArrivals[i][j] != actualArrivals[i][j]) {
                    return false;
                }
            }
        }
        return true;
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

    private MatrixRequest buildMatrixPerfRequest(int nodeCount, int sourceCount, int targetCount, long departureTicks) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE);
        for (int i = 0; i < sourceCount; i++) {
            builder.sourceExternalId("N" + ((i * 31) % nodeCount));
        }
        for (int i = 0; i < targetCount; i++) {
            builder.targetExternalId("N" + (((i * 17) + 9) % nodeCount));
        }
        return builder.build();
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture, TemporalRuntimeConfig temporalRuntimeConfig) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(temporalRuntimeConfig)
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
}
