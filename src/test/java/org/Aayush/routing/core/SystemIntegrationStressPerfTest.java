package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.registry.TraitBundleRegistry;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeBinder;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeConfig;
import org.Aayush.routing.traits.registry.TraitBundleSpec;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
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
                TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE,
                core.traitBundleContextContract().getConfigSource()
        );
        assertNotNull(core.traitBundleContextContract().getTraitHash());
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
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
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

        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE, edgeCore.traitBundleContextContract().getConfigSource());
        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE, nodeCore.traitBundleContextContract().getConfigSource());
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

    @Test
    @Timeout(value = 50, unit = TimeUnit.SECONDS)
    @DisplayName("Stage 18 heavy sample: named bundle keeps parity across coordinate, duplicate, and unreachable edge cases")
    void testStage18HeavySampleParityAcrossEdgeCases() {
        RoutingFixtureFactory.Fixture fixture = createGridWithIsolatedIslandFixture(18, 18, 8);
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromGraph(fixture.edgeGraph());
        TemporalRuntimeConfig temporalRuntimeConfig = TemporalRuntimeConfig.calendarModelTimezone("America/New_York");
        RouteCore nativeCore = createCore(
                fixture,
                temporalRuntimeConfig,
                null,
                TransitionRuntimeConfig.edgeBased(),
                spatialRuntime
        );
        RouteCore pairwiseCore = createCore(
                fixture,
                temporalRuntimeConfig,
                new TemporaryMatrixPlanner(),
                TransitionRuntimeConfig.edgeBased(),
                spatialRuntime
        );

        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE, nativeCore.traitBundleContextContract().getConfigSource());
        assertEquals(nativeCore.traitBundleContextContract().getTraitHash(), pairwiseCore.traitBundleContextContract().getTraitHash());

        RouteRequest reachableCoordinateRoute = RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.05d, 0.05d))
                .targetAddress(AddressInput.ofXY(17.05d, 17.05d))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.20d)
                .departureTicks(1_778_315_000L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteResponse nativeReachableRoute = nativeCore.route(reachableCoordinateRoute);
        RouteResponse pairwiseReachableRoute = pairwiseCore.route(reachableCoordinateRoute);
        assertTrue(routeEquals(nativeReachableRoute, pairwiseReachableRoute));
        assertTrue(nativeReachableRoute.isReachable());
        assertEquals(34.0f, nativeReachableRoute.getTotalCost(), COST_TOLERANCE);

        RouteRequest unreachableRoute = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N331")
                .departureTicks(1_778_315_031L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        RouteResponse nativeUnreachableRoute = nativeCore.route(unreachableRoute);
        RouteResponse pairwiseUnreachableRoute = pairwiseCore.route(unreachableRoute);
        assertTrue(routeEquals(nativeUnreachableRoute, pairwiseUnreachableRoute));
        assertFalse(nativeUnreachableRoute.isReachable());

        MatrixRequest nativeMatrixRequest = MatrixRequest.builder()
                .sourceExternalId("N0")
                .sourceExternalId("N0")
                .sourceExternalId("N17")
                .sourceExternalId("N323")
                .sourceExternalId("N324")
                .sourceExternalId("N331")
                .targetExternalId("N0")
                .targetExternalId("N323")
                .targetExternalId("N324")
                .targetExternalId("N331")
                .targetExternalId("N200")
                .departureTicks(1_778_315_047L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixResponse nativeMatrixResponse = nativeCore.matrix(nativeMatrixRequest);
        MatrixResponse pairwiseMatrixResponse = pairwiseCore.matrix(nativeMatrixRequest);
        assertEquals(NativeOneToManyMatrixPlanner.NATIVE_IMPLEMENTATION_NOTE, nativeMatrixResponse.getImplementationNote());
        assertTrue(matrixEquals(pairwiseMatrixResponse, nativeMatrixResponse, COST_TOLERANCE));
        assertArrayEquals(nativeMatrixResponse.getReachable()[0], nativeMatrixResponse.getReachable()[1]);
        assertArrayEquals(nativeMatrixResponse.getTotalCosts()[0], nativeMatrixResponse.getTotalCosts()[1], COST_TOLERANCE);
        assertFalse(nativeMatrixResponse.getReachable()[0][2]);
        assertFalse(nativeMatrixResponse.getReachable()[0][3]);
        assertTrue(nativeMatrixResponse.getReachable()[4][3]);

        MatrixRequest compatibilityMatrixRequest = MatrixRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.05d, 0.05d))
                .sourceAddress(AddressInput.ofXY(0.05d, 0.05d))
                .sourceAddress(AddressInput.ofXY(17.05d, 17.05d))
                .sourceAddress(AddressInput.ofXY(28.05d, 0.05d))
                .targetExternalId("N0")
                .targetExternalId("N323")
                .targetExternalId("N324")
                .targetExternalId("N331")
                .allowMixedAddressing(true)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.20d)
                .departureTicks(1_778_315_063L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        MatrixResponse nativeCompatibilityResponse = nativeCore.matrix(compatibilityMatrixRequest);
        MatrixResponse pairwiseCompatibilityResponse = pairwiseCore.matrix(compatibilityMatrixRequest);
        assertEquals(
                NativeOneToManyMatrixPlanner.NATIVE_A_STAR_IMPLEMENTATION_NOTE,
                nativeCompatibilityResponse.getImplementationNote()
        );
        assertTrue(matrixEquals(pairwiseCompatibilityResponse, nativeCompatibilityResponse, COST_TOLERANCE));
        assertArrayEquals(nativeCompatibilityResponse.getReachable()[0], nativeCompatibilityResponse.getReachable()[1]);
        assertFalse(nativeCompatibilityResponse.getReachable()[0][2]);
        assertTrue(nativeCompatibilityResponse.getReachable()[3][3]);
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
            builder.addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT);
            builder.coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY);
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
        return createCore(fixture, temporalRuntimeConfig, matrixPlanner, transitionRuntimeConfig, null);
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            TemporalRuntimeConfig temporalRuntimeConfig,
            MatrixPlanner matrixPlanner,
            TransitionRuntimeConfig transitionRuntimeConfig,
            SpatialRuntime spatialRuntime
    ) {
        TraitBundleSpec traitBundleSpec = namedBundleSpec(temporalRuntimeConfig, transitionRuntimeConfig);
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId(traitBundleSpec.getBundleId()))
                .traitBundleRegistry(new TraitBundleRegistry(List.of(traitBundleSpec)));
        if (spatialRuntime != null) {
            builder.spatialRuntime(spatialRuntime);
        }
        if (matrixPlanner != null) {
            builder.matrixPlanner(matrixPlanner);
        }
        return builder.build();
    }

    private TraitBundleSpec namedBundleSpec(
            TemporalRuntimeConfig temporalRuntimeConfig,
            TransitionRuntimeConfig transitionRuntimeConfig
    ) {
        return TraitBundleSpec.builder()
                .bundleId(bundleIdFor(temporalRuntimeConfig, transitionRuntimeConfig))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .temporalTraitId(temporalRuntimeConfig.getTemporalTraitId())
                .timezonePolicyId(temporalRuntimeConfig.getTimezonePolicyId())
                .modelProfileTimezone(temporalRuntimeConfig.getModelProfileTimezone())
                .transitionTraitId(transitionRuntimeConfig.getTransitionTraitId())
                .build();
    }

    private String bundleIdFor(
            TemporalRuntimeConfig temporalRuntimeConfig,
            TransitionRuntimeConfig transitionRuntimeConfig
    ) {
        return "SYS_"
                + normalizeBundleComponent(temporalRuntimeConfig.getTemporalTraitId()) + "_"
                + normalizeBundleComponent(temporalRuntimeConfig.getTimezonePolicyId()) + "_"
                + normalizeBundleComponent(temporalRuntimeConfig.getModelProfileTimezone()) + "_"
                + normalizeBundleComponent(transitionRuntimeConfig.getTransitionTraitId());
    }

    private String normalizeBundleComponent(String value) {
        if (value == null) {
            return "NONE";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "NONE";
        }
        StringBuilder normalized = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            normalized.append(Character.isLetterOrDigit(ch) ? Character.toUpperCase(ch) : '_');
        }
        return normalized.toString();
    }

    private SpatialRuntime buildSpatialRuntimeFromGraph(EdgeGraph edgeGraph) {
        ByteBuffer model = buildSpatialModelBuffer(edgeGraph);
        return SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), edgeGraph, true);
    }

    private ByteBuffer buildSpatialModelBuffer(EdgeGraph edgeGraph) {
        int nodeCount = edgeGraph.nodeCount();
        FlatBufferBuilder builder = new FlatBufferBuilder(Math.max(4096, nodeCount * 48));

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTargets = new int[0];

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTargets);

        GraphTopology.startCoordinatesVector(builder, nodeCount);
        for (int i = nodeCount - 1; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    edgeGraph.getNodeX(i),
                    edgeGraph.getNodeY(i)
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
        int modelVersion = builder.createString("system-integration-stress");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }

    private RoutingFixtureFactory.Fixture createGridWithIsolatedIslandFixture(int rows, int cols, int islandLength) {
        int mainNodeCount = rows * cols;
        int nodeCount = mainNodeCount + islandLength;
        int mainGridEdges = 2 * (rows * (cols - 1) + cols * (rows - 1));
        int islandEdges = Math.max(0, 2 * (islandLength - 1));
        int edgeCount = mainGridEdges + islandEdges;

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
        for (int i = 0; i < islandLength; i++) {
            int degree = 0;
            if (i > 0) degree++;
            if (i + 1 < islandLength) degree++;
            outDegree[mainNodeCount + i] = degree;
        }

        int[] firstEdge = new int[nodeCount + 1];
        int cursor = 0;
        for (int i = 0; i < nodeCount; i++) {
            firstEdge[i] = cursor;
            cursor += outDegree[i];
        }
        firstEdge[nodeCount] = cursor;

        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfiles = new int[edgeCount];
        double[] coords = new double[nodeCount * 2];

        cursor = 0;
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

        for (int i = 0; i < islandLength; i++) {
            int node = mainNodeCount + i;
            coords[node * 2] = rows + 10.0d;
            coords[node * 2 + 1] = i;

            if (i > 0) {
                edgeTarget[cursor] = node - 1;
                edgeOrigin[cursor] = node;
                baseWeights[cursor] = 1.0f;
                edgeProfiles[cursor] = 1;
                cursor++;
            }
            if (i + 1 < islandLength) {
                edgeTarget[cursor] = node + 1;
                edgeOrigin[cursor] = node;
                baseWeights[cursor] = 1.0f;
                edgeProfiles[cursor] = 1;
                cursor++;
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
