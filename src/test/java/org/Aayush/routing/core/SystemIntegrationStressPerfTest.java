package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Integrated System Stress/Perf Tests")
class SystemIntegrationStressPerfTest {
    private static final float COST_TOLERANCE = 1e-5f;

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("Heavy mixed traffic: route + matrix + typed addressing stay deterministic under concurrency")
    void testHeavyMixedTrafficConcurrencyDeterminism() throws InterruptedException {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(22, 22);
        RouteCore core = createCore(
                fixture,
                TemporalRuntimeConfig.calendarModelTimezone("America/New_York"),
                null
        );

        RouteRequest legacyRouteRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N483")
                .departureTicks(1_778_313_600L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteRequest typedRouteRequest = RouteRequest.builder()
                .sourceAddress(AddressInput.ofExternalId("N20"))
                .targetAddress(AddressInput.ofExternalId("N460"))
                .addressingTraitId(AddressingRuntimeConfig.defaultRuntime().getAddressingTraitId())
                .departureTicks(1_778_313_600L + 17L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixRequest nativeMatrixRequest = buildMatrixRequest(22 * 22, 6, 10, 1_778_313_600L, false);
        MatrixRequest compatibilityMatrixRequest = buildMatrixRequest(22 * 22, 4, 6, 1_778_313_600L + 31L, true);

        RouteResponse legacyRouteBaseline = core.route(legacyRouteRequest);
        RouteResponse typedRouteBaseline = core.route(typedRouteRequest);
        MatrixResponse nativeMatrixBaseline = core.matrix(nativeMatrixRequest);
        MatrixResponse compatibilityMatrixBaseline = core.matrix(compatibilityMatrixRequest);

        assertEquals(
                NativeOneToManyMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE,
                nativeMatrixBaseline.getImplementationNote()
        );
        assertEquals(
                NativeOneToManyMatrixPlanner.NATIVE_A_STAR_IMPLEMENTATION_NOTE,
                compatibilityMatrixBaseline.getImplementationNote()
        );

        int threads = 8;
        int loopsPerThread = 80;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);
        long startNanos = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        int mode = (threadId + i) & 3;
                        if (mode == 0) {
                            RouteResponse current = core.route(legacyRouteRequest);
                            if (!routeEquals(legacyRouteBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 1) {
                            RouteResponse current = core.route(typedRouteRequest);
                            if (!routeEquals(typedRouteBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 2) {
                            MatrixResponse current = core.matrix(nativeMatrixRequest);
                            if (!matrixEquals(nativeMatrixBaseline, current, COST_TOLERANCE)) {
                                failed.set(true);
                                return;
                            }
                        } else {
                            MatrixResponse current = core.matrix(compatibilityMatrixRequest);
                            if (!matrixEquals(compatibilityMatrixBaseline, current, COST_TOLERANCE)) {
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

        assertTrue(latch.await(35, TimeUnit.SECONDS), "mixed integration concurrency run timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "mixed integration responses diverged under concurrent stress");

        long elapsedNanos = System.nanoTime() - startNanos;
        long totalOperations = (long) threads * loopsPerThread;
        double throughputOpsPerSecond = totalOperations / (elapsedNanos / 1_000_000_000.0d);
        assertTrue(
                throughputOpsPerSecond > 15.0d,
                "mixed integration throughput dropped below expected floor: " + throughputOpsPerSecond + " ops/sec"
        );
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Stage 17 integration: EDGE_BASED and NODE_BASED mixed traffic remain deterministic")
    void testStage17MixedTrafficDeterminismAcrossTransitionModes() throws InterruptedException {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(20, 20);
        RouteCore edgeCore = createCore(
                fixture,
                TemporalRuntimeConfig.calendarUtc(),
                null,
                TransitionRuntimeConfig.edgeBased()
        );
        RouteCore nodeCore = createCore(
                fixture,
                TemporalRuntimeConfig.calendarUtc(),
                null,
                TransitionRuntimeConfig.nodeBased()
        );

        RouteRequest legacyRouteRequest = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N399")
                .departureTicks(1_778_314_000L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteRequest typedRouteRequest = RouteRequest.builder()
                .sourceAddress(AddressInput.ofExternalId("N19"))
                .targetAddress(AddressInput.ofExternalId("N378"))
                .addressingTraitId(AddressingRuntimeConfig.defaultRuntime().getAddressingTraitId())
                .departureTicks(1_778_314_000L + 9L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixRequest nativeMatrixRequest = buildMatrixRequest(20 * 20, 6, 8, 1_778_314_000L + 17L, false);
        MatrixRequest compatibilityMatrixRequest = buildMatrixRequest(20 * 20, 4, 6, 1_778_314_000L + 31L, true);

        RouteResponse edgeLegacyBaseline = edgeCore.route(legacyRouteRequest);
        RouteResponse nodeLegacyBaseline = nodeCore.route(legacyRouteRequest);
        RouteResponse edgeTypedBaseline = edgeCore.route(typedRouteRequest);
        RouteResponse nodeTypedBaseline = nodeCore.route(typedRouteRequest);
        MatrixResponse edgeNativeMatrixBaseline = edgeCore.matrix(nativeMatrixRequest);
        MatrixResponse nodeNativeMatrixBaseline = nodeCore.matrix(nativeMatrixRequest);
        MatrixResponse edgeCompatibilityBaseline = edgeCore.matrix(compatibilityMatrixRequest);
        MatrixResponse nodeCompatibilityBaseline = nodeCore.matrix(compatibilityMatrixRequest);

        assertTrue(routeEquals(edgeLegacyBaseline, nodeLegacyBaseline));
        assertTrue(routeEquals(edgeTypedBaseline, nodeTypedBaseline));
        assertTrue(matrixEquals(edgeNativeMatrixBaseline, nodeNativeMatrixBaseline, COST_TOLERANCE));
        assertTrue(matrixEquals(edgeCompatibilityBaseline, nodeCompatibilityBaseline, COST_TOLERANCE));
        assertEquals(edgeNativeMatrixBaseline.getImplementationNote(), nodeNativeMatrixBaseline.getImplementationNote());
        assertEquals(edgeCompatibilityBaseline.getImplementationNote(), nodeCompatibilityBaseline.getImplementationNote());

        int threads = 8;
        int loopsPerThread = 56;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);
        long startNanos = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        int mode = (threadId + i) & 7;
                        if (mode == 0) {
                            RouteResponse current = edgeCore.route(legacyRouteRequest);
                            if (!routeEquals(edgeLegacyBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 1) {
                            RouteResponse current = nodeCore.route(legacyRouteRequest);
                            if (!routeEquals(nodeLegacyBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 2) {
                            RouteResponse current = edgeCore.route(typedRouteRequest);
                            if (!routeEquals(edgeTypedBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 3) {
                            RouteResponse current = nodeCore.route(typedRouteRequest);
                            if (!routeEquals(nodeTypedBaseline, current)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 4) {
                            MatrixResponse current = edgeCore.matrix(nativeMatrixRequest);
                            if (!matrixEquals(edgeNativeMatrixBaseline, current, COST_TOLERANCE)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 5) {
                            MatrixResponse current = nodeCore.matrix(nativeMatrixRequest);
                            if (!matrixEquals(nodeNativeMatrixBaseline, current, COST_TOLERANCE)) {
                                failed.set(true);
                                return;
                            }
                        } else if (mode == 6) {
                            MatrixResponse current = edgeCore.matrix(compatibilityMatrixRequest);
                            if (!matrixEquals(edgeCompatibilityBaseline, current, COST_TOLERANCE)) {
                                failed.set(true);
                                return;
                            }
                        } else {
                            MatrixResponse current = nodeCore.matrix(compatibilityMatrixRequest);
                            if (!matrixEquals(nodeCompatibilityBaseline, current, COST_TOLERANCE)) {
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

        assertTrue(latch.await(35, TimeUnit.SECONDS), "stage17 mixed integration run timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "stage17 mixed integration responses diverged under concurrent stress");

        long elapsedNanos = System.nanoTime() - startNanos;
        long totalOperations = (long) threads * loopsPerThread;
        double throughputOpsPerSecond = totalOperations / (elapsedNanos / 1_000_000_000.0d);
        assertTrue(
                throughputOpsPerSecond > 12.0d,
                "stage17 mixed integration throughput dropped below expected floor: " + throughputOpsPerSecond + " ops/sec"
        );
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Large matrix limit: native planner keeps oracle parity and outperforms pairwise compatibility")
    void testLargeMatrixNativeLimitAgainstPairwiseCompatibility() {
        RoutingFixtureFactory.Fixture fixture = createGridFixture(16, 16);
        RouteCore nativeCore = createCore(fixture, TemporalRuntimeConfig.calendarUtc(), null);
        RouteCore pairwiseCore = createCore(
                fixture,
                TemporalRuntimeConfig.calendarUtc(),
                new TemporaryMatrixPlanner()
        );

        MatrixRequest request = buildMatrixRequest(16 * 16, 12, 40, 1_778_313_600L, false);

        MatrixResponse oracle = pairwiseCore.matrix(request);
        MatrixResponse nativeBaseline = nativeCore.matrix(request);
        assertEquals(NativeOneToManyMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE, nativeBaseline.getImplementationNote());
        assertTrue(matrixEquals(oracle, nativeBaseline, COST_TOLERANCE));

        runMatrixBatch(nativeCore, request, 1);
        runMatrixBatch(pairwiseCore, request, 1);

        int measuredLoops = 1;
        long nativeNanos = runMatrixBatch(nativeCore, request, measuredLoops);
        long pairwiseNanos = runMatrixBatch(pairwiseCore, request, measuredLoops);
        double nativeAvgMillis = (nativeNanos / 1_000_000.0d) / measuredLoops;
        double pairwiseAvgMillis = (pairwiseNanos / 1_000_000.0d) / measuredLoops;
        double speedup = pairwiseAvgMillis / Math.max(1e-9d, nativeAvgMillis);

        MatrixExecutionStats stats = nativeCore.matrixExecutionStatsContract();
        assertTrue(stats.requestWorkStates() > 0L);
        assertEquals(12, stats.rowWorkStates().length);
        assertTrue(
                speedup >= 1.2d,
                "native matrix speedup regressed: expected >= 1.2x but observed " + speedup
                        + "x (native=" + nativeAvgMillis + "ms, pairwise=" + pairwiseAvgMillis + "ms)"
        );
    }

    private boolean routeEquals(RouteResponse baseline, RouteResponse candidate) {
        return baseline.isReachable() == candidate.isReachable()
                && Float.compare(baseline.getTotalCost(), candidate.getTotalCost()) == 0
                && baseline.getArrivalTicks() == candidate.getArrivalTicks()
                && baseline.getSettledStates() == candidate.getSettledStates()
                && baseline.getPathExternalNodeIds().equals(candidate.getPathExternalNodeIds())
                && baseline.getSourceResolvedAddress().equals(candidate.getSourceResolvedAddress())
                && baseline.getTargetResolvedAddress().equals(candidate.getTargetResolvedAddress());
    }

    private boolean matrixEquals(MatrixResponse expected, MatrixResponse actual, float costTolerance) {
        if (!expected.getSourceExternalIds().equals(actual.getSourceExternalIds())) {
            return false;
        }
        if (!expected.getTargetExternalIds().equals(actual.getTargetExternalIds())) {
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
                float expectedCost = expectedCosts[row][col];
                float actualCost = actualCosts[row][col];
                if (Float.isFinite(expectedCost) && Float.isFinite(actualCost)) {
                    if (Math.abs(expectedCost - actualCost) > costTolerance) {
                        return false;
                    }
                } else if (Float.compare(expectedCost, actualCost) != 0) {
                    return false;
                }
                if (expectedArrivals[row][col] != actualArrivals[row][col]) {
                    return false;
                }
            }
        }
        return true;
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

    private MatrixRequest buildMatrixRequest(
            int nodeCount,
            int sourceCount,
            int targetCount,
            long departureTicks,
            boolean aStarCompatibilityMode
    ) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .departureTicks(departureTicks)
                .algorithm(aStarCompatibilityMode ? RoutingAlgorithm.A_STAR : RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE);

        for (int i = 0; i < sourceCount; i++) {
            int nodeId = (i * 37 + 3) % nodeCount;
            if (aStarCompatibilityMode) {
                builder.sourceAddress(AddressInput.ofExternalId("N" + nodeId));
            } else {
                builder.sourceExternalId("N" + nodeId);
            }
        }
        for (int i = 0; i < targetCount; i++) {
            int nodeId = (i * 19 + 11) % nodeCount;
            builder.targetExternalId("N" + nodeId);
        }
        if (aStarCompatibilityMode) {
            builder.addressingTraitId(AddressingRuntimeConfig.defaultRuntime().getAddressingTraitId());
        }
        return builder.build();
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            TemporalRuntimeConfig temporalRuntimeConfig,
            MatrixPlanner matrixPlanner
    ) {
        return createCore(
                fixture,
                temporalRuntimeConfig,
                matrixPlanner,
                TransitionRuntimeConfig.defaultRuntime()
        );
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            TemporalRuntimeConfig temporalRuntimeConfig,
            MatrixPlanner matrixPlanner,
            TransitionRuntimeConfig transitionRuntimeConfig
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(temporalRuntimeConfig)
                .transitionRuntimeConfig(transitionRuntimeConfig)
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime());
        if (matrixPlanner != null) {
            builder.matrixPlanner(matrixPlanner);
        }
        return builder.build();
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
}
