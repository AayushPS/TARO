package org.Aayush.routing.future;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore.Config;
import org.Aayush.routing.topology.TopologyVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In-Memory Ephemeral Result Store Tests")
class InMemoryEphemeralResultStoreTest {
    private static final Instant BASE_INSTANT = Instant.parse("2026-03-21T00:00:00Z");

    @Test
    @DisplayName("Route store evicts to byte budget and expires entries by TTL")
    void testRouteStoreBudgetAndExpiry() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        FutureRouteResultSet first = routeResultSet("route-a", BASE_INSTANT.plus(Duration.ofMinutes(2)), List.of("N0", "N1"));
        long estimatedBytes = FutureResultStoreSizing.estimateRouteResultSet(first);
        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(
                clock,
                new InMemoryEphemeralRouteResultStore.Config(10L, (estimatedBytes * 2L) + (estimatedBytes / 2L), estimatedBytes * 2L)
        );

        FutureRouteResultSet second = routeResultSet("route-b", BASE_INSTANT.plus(Duration.ofMinutes(5)), List.of("N0", "N2"));
        FutureRouteResultSet third = routeResultSet("route-c", BASE_INSTANT.plus(Duration.ofMinutes(6)), List.of("N0", "N3"));

        store.put(first);
        store.put(second);
        store.put(third);

        assertFalse(store.get("route-a").isPresent(), "earliest-expiry entry should be evicted first when over byte budget");
        assertTrue(store.get("route-b").isPresent());
        assertTrue(store.get("route-c").isPresent());

        clock.set(BASE_INSTANT.plus(Duration.ofMinutes(6)));
        assertFalse(store.get("route-b").isPresent(), "expired entry should be removed on read");
        assertFalse(store.get("route-c").isPresent(), "entry expiring at now should be treated as expired");
    }

    @Test
    @DisplayName("Route store prefers least-recently-read ties, supports invalidation, and rejects oversize entries")
    void testRouteStoreReadOrderInvalidationAndOversizeAdmission() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        FutureRouteResultSet first = routeResultSet("route-read-a", BASE_INSTANT.plus(Duration.ofMinutes(10)), List.of("N0", "N1"));
        long estimatedBytes = FutureResultStoreSizing.estimateRouteResultSet(first);
        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(
                clock,
                new InMemoryEphemeralRouteResultStore.Config(10L, (estimatedBytes * 2L) + (estimatedBytes / 2L), estimatedBytes * 2L)
        );

        FutureRouteResultSet second = routeResultSet("route-read-b", BASE_INSTANT.plus(Duration.ofMinutes(10)), List.of("N0", "N2"));
        FutureRouteResultSet third = routeResultSet("route-read-c", BASE_INSTANT.plus(Duration.ofMinutes(10)), List.of("N0", "N3"));
        store.put(first);
        store.put(second);
        assertTrue(store.get("route-read-a").isPresent(), "reading one entry should make it newest for tie-break eviction");
        store.put(third);

        assertTrue(store.get("route-read-a").isPresent());
        assertFalse(store.get("route-read-b").isPresent(), "least-recently-read equal-expiry entry should be evicted first");
        assertTrue(store.get("route-read-c").isPresent());

        store.invalidate(resultSet -> resultSet.getResultSetId().endsWith("-a"));
        assertFalse(store.get("route-read-a").isPresent());
        assertTrue(store.get("route-read-c").isPresent());

        InMemoryEphemeralRouteResultStore tinyStore = new InMemoryEphemeralRouteResultStore(
                clock,
                new InMemoryEphemeralRouteResultStore.Config(10L, 1024L * 1024L, estimatedBytes - 1L)
        );
        tinyStore.put(first);
        assertFalse(tinyStore.get("route-read-a").isPresent(), "oversize route entries should not be retained");
        assertFalse(tinyStore.get("missing-route").isPresent(), "missing entries should return empty");
    }

    @Test
    @DisplayName("Matrix store round-trips compacted payloads and rejects oversize entries")
    void testMatrixStoreCompressionAndOversizeNonAdmission() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        FutureMatrixResultSet matrixResultSet = matrixResultSet("matrix-large", 80, 80, BASE_INSTANT.plus(Duration.ofMinutes(10)));

        InMemoryEphemeralMatrixResultStore store = new InMemoryEphemeralMatrixResultStore(clock, Config.defaults());
        store.put(matrixResultSet);
        FutureMatrixResultSet restored = store.get("matrix-large").orElseThrow();

        assertEquals(matrixResultSet.getAggregate().getSourceExternalIds(), restored.getAggregate().getSourceExternalIds());
        assertEquals(matrixResultSet.getAggregate().getTargetExternalIds(), restored.getAggregate().getTargetExternalIds());
        assertDoubleMatrixEquals(matrixResultSet.getAggregate().getReachabilityProbabilities(),
                restored.getAggregate().getReachabilityProbabilities());
        assertFloatMatrixEquals(matrixResultSet.getAggregate().getExpectedCosts(), restored.getAggregate().getExpectedCosts());
        assertFloatMatrixEquals(matrixResultSet.getAggregate().getP90Costs(), restored.getAggregate().getP90Costs());
        assertLongMatrixEquals(matrixResultSet.getAggregate().getMaxArrivalTicks(), restored.getAggregate().getMaxArrivalTicks());
        assertArrayEquals(
                matrixResultSet.getScenarioResults().getFirst().getMatrix().getTotalCosts()[10],
                restored.getScenarioResults().getFirst().getMatrix().getTotalCosts()[10]
        );

        InMemoryEphemeralMatrixResultStore tinyStore = new InMemoryEphemeralMatrixResultStore(
                clock,
                new Config(10L, 1024L * 1024L, 256L, 0)
        );
        tinyStore.put(matrixResultSet);
        assertFalse(tinyStore.get("matrix-large").isPresent(), "oversize entries should not be admitted into retained storage");
    }

    @Test
    @DisplayName("Matrix store honors read-order eviction, invalidation, and TTL expiry on uncompressed payloads")
    void testMatrixStoreReadOrderInvalidationAndExpiry() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        FutureMatrixResultSet first = matrixResultSet("matrix-a", 4, 4, BASE_INSTANT.plus(Duration.ofMinutes(5)));
        FutureMatrixResultSet second = matrixResultSet("matrix-b", 4, 4, BASE_INSTANT.plus(Duration.ofMinutes(5)));
        FutureMatrixResultSet third = matrixResultSet("matrix-c", 4, 4, BASE_INSTANT.plus(Duration.ofMinutes(5)));
        long estimatedBytes = FutureResultStoreSizing.estimateMatrixMetadata(first) + 2_048L;

        InMemoryEphemeralMatrixResultStore store = new InMemoryEphemeralMatrixResultStore(
                clock,
                new Config(10L, estimatedBytes * 2L, estimatedBytes * 2L, Integer.MAX_VALUE)
        );
        store.put(first);
        store.put(second);
        assertTrue(store.get("matrix-a").isPresent(), "read access should refresh eviction ordering");
        store.put(third);

        assertTrue(store.get("matrix-a").isPresent());
        assertFalse(store.get("matrix-b").isPresent(), "equal-expiry matrix entries should evict by least-recently-read");
        assertTrue(store.get("matrix-c").isPresent());

        store.invalidate(resultSet -> resultSet.getResultSetId().equals("matrix-a"));
        assertFalse(store.get("matrix-a").isPresent());

        clock.set(BASE_INSTANT.plus(Duration.ofMinutes(5)));
        assertFalse(store.get("matrix-c").isPresent(), "matrix entries expiring at now should be removed");
        assertFalse(store.get("missing-matrix").isPresent());
    }

    @Test
    @DisplayName("Store config validation rejects impossible limits")
    void testStoreConfigValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEphemeralRouteResultStore.Config(0L, 1L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEphemeralRouteResultStore.Config(1L, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEphemeralRouteResultStore.Config(1L, 1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryEphemeralRouteResultStore.Config(1L, 10L, 11L));

        assertThrows(IllegalArgumentException.class,
                () -> new Config(0L, 1L, 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(1L, 0L, 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(1L, 1L, 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(1L, 10L, 11L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(1L, 10L, 10L, -1));
    }

    @Test
    @DisplayName("Route store rejects malformed B5 density and provenance artifacts")
    void testRouteStoreRejectsMalformedB5Artifacts() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(clock, InMemoryEphemeralRouteResultStore.Config.defaults());

        FutureRouteResultSet valid = routeResultSet("route-valid", BASE_INSTANT.plus(Duration.ofMinutes(10)), List.of("N0", "N1"));
        FutureRouteResultSet invalidDensity = valid.toBuilder()
                .candidateDensityCalibrationReport(valid.getCandidateDensityCalibrationReport().toBuilder()
                        .aggregateAddedCandidateCount(1)
                        .build())
                .build();
        FutureRouteResultSet invalidRatios = valid.toBuilder()
                .candidateDensityCalibrationReport(valid.getCandidateDensityCalibrationReport().toBuilder()
                        .scenarioCoverageRatio(0.25d)
                        .build())
                .build();
        ScenarioRouteSelection invalidSelection = valid.getExpectedRoute().toBuilder()
                .routeSelectionProvenance(null)
                .build();
        FutureRouteResultSet invalidProvenance = valid.toBuilder()
                .expectedRoute(invalidSelection)
                .build();
        ScenarioRouteSelection invalidUnreachable = valid.getExpectedRoute().toBuilder()
                .route(valid.getExpectedRoute().getRoute().toBuilder().reachable(false).build())
                .routeSelectionProvenance(RouteSelectionProvenance.SCENARIO_OPTIMAL)
                .build();
        FutureRouteResultSet invalidUnreachableSelection = valid.toBuilder()
                .expectedRoute(invalidUnreachable)
                .build();

        assertThrows(IllegalArgumentException.class, () -> store.put(invalidDensity));
        assertThrows(IllegalArgumentException.class, () -> store.put(invalidRatios));
        assertThrows(IllegalArgumentException.class, () -> store.put(invalidProvenance));
        assertThrows(IllegalArgumentException.class, () -> store.put(invalidUnreachableSelection));
    }

    @Test
    @DisplayName("Matrix store utility helpers handle zero-sized and corrupted payload edge cases")
    void testMatrixStoreUtilityEdgeCases() throws Exception {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        FutureMatrixResultSet empty = matrixResultSet("matrix-empty", 0, 0, BASE_INSTANT.plus(Duration.ofMinutes(1)));
        InMemoryEphemeralMatrixResultStore store = new InMemoryEphemeralMatrixResultStore(
                clock,
                new Config(10L, 1024L * 1024L, 1024L * 1024L, Integer.MAX_VALUE)
        );
        store.put(empty);
        FutureMatrixResultSet restored = store.get("matrix-empty").orElseThrow();
        assertEquals(0, restored.getAggregate().getExpectedCosts().length);
        assertEquals(0, restored.getScenarioResults().getFirst().getMatrix().getReachable().length);

        Method maybeCompress = InMemoryEphemeralMatrixResultStore.class.getDeclaredMethod(
                "maybeCompress",
                byte[].class,
                int.class
        );
        maybeCompress.setAccessible(true);
        byte[] raw = new byte[512];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) ((i * 37) ^ (i >>> 1));
        }
        Object compressionResult = maybeCompress.invoke(null, raw, 0);
        Method compressedAccessor = compressionResult.getClass().getDeclaredMethod("compressed");
        compressedAccessor.setAccessible(true);
        assertFalse((boolean) compressedAccessor.invoke(compressionResult), "high-entropy payloads should stay raw when compression is not helpful");

        Method maybeInflate = InMemoryEphemeralMatrixResultStore.class.getDeclaredMethod(
                "maybeInflate",
                byte[].class,
                boolean.class,
                int.class
        );
        maybeInflate.setAccessible(true);
        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> maybeInflate.invoke(null, new byte[]{1, 2, 3}, true, 16)
        );
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }

    private FutureRouteResultSet routeResultSet(String resultSetId, Instant expiresAt, List<String> pathNodes) {
        TopologyVersion topologyVersion = topologyVersion();
        ScenarioRouteSelection selection = ScenarioRouteSelection.builder()
                .route(RouteShape.builder()
                        .reachable(true)
                        .departureTicks(0L)
                        .pathExternalNodeIds(pathNodes)
                        .build())
                .expectedCost(10.0f)
                .p50Cost(10.0f)
                .p90Cost(10.0f)
                .minCost(10.0f)
                .maxCost(10.0f)
                .minArrivalTicks(10L)
                .maxArrivalTicks(10L)
                .optimalityProbability(1.0d)
                .dominantScenarioId("baseline")
                .dominantScenarioLabel("baseline")
                .routeSelectionProvenance(RouteSelectionProvenance.SCENARIO_OPTIMAL)
                .build();
        return FutureRouteResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(BASE_INSTANT)
                .expiresAt(expiresAt)
                .request(FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId(pathNodes.getFirst())
                                .targetExternalId(pathNodes.getLast())
                                .departureTicks(0L)
                                .build())
                        .resultTtl(Duration.ofMinutes(10))
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-route:0")
                .scenarioBundle(ScenarioBundle.builder()
                        .scenarioBundleId("bundle-" + resultSetId)
                        .generatedAt(BASE_INSTANT)
                        .validUntil(expiresAt)
                        .horizonTicks(3_600L)
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId("q-route:0")
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(1.0d)
                                .build())
                        .build())
                .candidateDensityCalibrationReport(CandidateDensityCalibrationReport.builder()
                        .policyId("b5-density-v2")
                        .scenarioCount(1)
                        .scenarioOptimalRouteCount(1)
                        .uniqueScenarioOptimalRouteCount(1)
                        .uniqueCandidateRouteCount(1)
                        .aggregateAddedCandidateCount(0)
                        .expectedRouteAggregateOnly(false)
                        .robustRouteAggregateOnly(false)
                        .selectedAlternativeCount(1)
                        .scenarioCoverageRatio(1.0d)
                        .candidateCoverageRatio(1.0d)
                        .aggregateExpansionRatio(0.0d)
                        .densityClass(CandidateDensityClass.LOW_DENSITY)
                        .build())
                .expectedRoute(selection)
                .robustRoute(selection)
                .alternative(selection)
                .scenarioResult(FutureRouteScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .route(org.Aayush.routing.core.RouteResponse.builder()
                                .reachable(true)
                                .departureTicks(0L)
                                .arrivalTicks(10L)
                                .totalCost(10.0f)
                                .settledStates(1)
                                .pathExternalNodeIds(pathNodes)
                                .build())
                        .build())
                .build();
    }

    private FutureMatrixResultSet matrixResultSet(String resultSetId, int rows, int cols, Instant expiresAt) {
        TopologyVersion topologyVersion = topologyVersion();
        List<String> sourceIds = ids("S", rows);
        List<String> targetIds = ids("T", cols);

        double[][] reachability = new double[rows][cols];
        float[][] expected = new float[rows][cols];
        float[][] p50 = new float[rows][cols];
        float[][] p90 = new float[rows][cols];
        float[][] min = new float[rows][cols];
        float[][] max = new float[rows][cols];
        long[][] minArrival = new long[rows][cols];
        long[][] maxArrival = new long[rows][cols];
        boolean[][] scenarioReachable = new boolean[rows][cols];
        float[][] scenarioCosts = new float[rows][cols];
        long[][] scenarioArrivals = new long[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double probability = ((row + col) % 5 == 0) ? 0.8d : 1.0d;
                float base = (row * 3.0f) + col;
                reachability[row][col] = probability;
                expected[row][col] = base;
                p50[row][col] = base + 1.0f;
                p90[row][col] = base + 2.0f;
                min[row][col] = base - 1.0f;
                max[row][col] = base + 3.0f;
                minArrival[row][col] = row + col;
                maxArrival[row][col] = row + col + 5L;
                scenarioReachable[row][col] = true;
                scenarioCosts[row][col] = base + 4.0f;
                scenarioArrivals[row][col] = row + col + 10L;
            }
        }

        return FutureMatrixResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(BASE_INSTANT)
                .expiresAt(expiresAt)
                .request(FutureMatrixRequest.builder()
                        .matrixRequest(MatrixRequest.builder()
                                .sourceExternalIds(sourceIds)
                                .targetExternalIds(targetIds)
                                .departureTicks(0L)
                                .build())
                        .resultTtl(Duration.ofMinutes(10))
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-matrix:0")
                .scenarioBundle(ScenarioBundle.builder()
                        .scenarioBundleId("bundle-" + resultSetId)
                        .generatedAt(BASE_INSTANT)
                        .validUntil(expiresAt)
                        .horizonTicks(3_600L)
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId("q-matrix:0")
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(1.0d)
                                .build())
                        .build())
                .aggregate(FutureMatrixAggregate.builder()
                        .sourceExternalIds(sourceIds)
                        .targetExternalIds(targetIds)
                        .reachabilityProbabilities(reachability)
                        .expectedCosts(expected)
                        .p50Costs(p50)
                        .p90Costs(p90)
                        .minCosts(min)
                        .maxCosts(max)
                        .minArrivalTicks(minArrival)
                        .maxArrivalTicks(maxArrival)
                        .aggregationNote("compacted")
                        .build())
                .scenarioResult(FutureMatrixScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .matrix(MatrixResponse.builder()
                                .sourceExternalIds(sourceIds)
                                .targetExternalIds(targetIds)
                                .reachable(scenarioReachable)
                                .totalCosts(scenarioCosts)
                                .arrivalTicks(scenarioArrivals)
                                .implementationNote("scenario")
                                .build())
                        .build())
                .build();
    }

    private TopologyVersion topologyVersion() {
        return TopologyVersion.builder()
                .modelVersion("store-model")
                .topologyVersion("store-topology")
                .generatedAt(BASE_INSTANT)
                .sourceDataLineageHash("store-lineage")
                .changeSetHash("store-change")
                .build();
    }

    private List<String> ids(String prefix, int count) {
        ArrayList<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(prefix + i);
        }
        return List.copyOf(ids);
    }

    private void assertFloatMatrixEquals(float[][] expected, float[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals(expected[row], actual[row]);
        }
    }

    private void assertLongMatrixEquals(long[][] expected, long[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals(expected[row], actual[row]);
        }
    }

    private void assertDoubleMatrixEquals(double[][] expected, double[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals(expected[row], actual[row]);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }
    }
}
