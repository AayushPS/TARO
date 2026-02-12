package org.Aayush.routing.heuristic;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 11 Geometry Heuristic Tests")
class GeometryHeuristicTest {
    private static final int ALL_DAYS_MASK = 0x7F;
    private static final int BUCKET_SIZE_SECONDS = 3_600;

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {
    }

    private record Fixture(EdgeGraph graph, ProfileStore profileStore, CostEngine costEngine) {
    }

    @Test
    @DisplayName("Known-distance: Euclidean heuristic equals distance * calibrated scale")
    void testKnownEuclideanDistance() {
        Fixture fixture = createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{10.0f, 10.0f},
                new int[]{1, 1},
                new double[]{0.0, 0.0, 3.0, 4.0, 6.0, 8.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        GoalBoundHeuristic bound = provider.bindGoal(2);

        assertEquals(20.0d, bound.estimateFromNode(0), 1e-9);
        assertEquals(10.0d, bound.estimateFromNode(1), 1e-9);
        assertEquals(0.0d, bound.estimateFromNode(2), 1e-12);
    }

    @Test
    @DisplayName("Known-distance: Spherical heuristic equals great-circle * calibrated scale")
    void testKnownSphericalDistance() {
        double d01 = greatCircleDistanceMeters(0.0, 0.0, 0.0, 1.0);
        double d12 = greatCircleDistanceMeters(0.0, 1.0, 0.0, 2.0);
        float edgeWeight = (float) (d01 * 1.5d);

        Fixture fixture = createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{edgeWeight, (float) (d12 * 1.5d)},
                new int[]{1, 1},
                new double[]{0.0, 0.0, 0.0, 1.0, 0.0, 2.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        GoalBoundHeuristic bound = provider.bindGoal(2);

        double d02 = greatCircleDistanceMeters(0.0, 0.0, 0.0, 2.0);
        assertEquals(d02 * 1.5d, bound.estimateFromNode(0), 0.05d);
        assertEquals(d12 * 1.5d, bound.estimateFromNode(1), 0.05d);
        assertEquals(0.0d, bound.estimateFromNode(2), 1e-9);
    }

    @Test
    @DisplayName("Boundary values remain finite near poles and anti-meridian")
    void testSphericalBoundaryStability() {
        double lat = 89.9999d;
        double edge01 = greatCircleDistanceMeters(lat, 179.9999d, lat, -179.9999d);
        double edge12 = greatCircleDistanceMeters(lat, -179.9999d, -lat, 179.9999d);
        double edge23 = greatCircleDistanceMeters(-lat, 179.9999d, -lat, -179.9999d);

        Fixture fixture = createFixture(
                4,
                new int[]{0, 1, 2, 3, 3},
                new int[]{1, 2, 3},
                new int[]{0, 1, 2},
                new float[]{(float) (edge01 * 1.2d), (float) (edge12 * 1.2d), (float) (edge23 * 1.2d)},
                new int[]{1, 1, 1},
                new double[]{
                        lat, 179.9999d,
                        lat, -179.9999d,
                        -lat, 179.9999d,
                        -lat, -179.9999d
                },
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );

        GoalBoundHeuristic toNode3 = provider.bindGoal(3);
        for (int node = 0; node < fixture.graph().nodeCount(); node++) {
            double estimate = toNode3.estimateFromNode(node);
            assertTrue(Double.isFinite(estimate), "estimate must be finite for node " + node);
            assertTrue(estimate >= 0.0d, "estimate must be >= 0 for node " + node);
        }

        GoalBoundHeuristic toNode1 = provider.bindGoal(1);
        double antiMeridianEstimate = toNode1.estimateFromNode(0);
        assertTrue(Double.isFinite(antiMeridianEstimate));
        assertTrue(antiMeridianEstimate < 10.0d, "anti-meridian estimate should stay tiny and stable");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Determinism and concurrency: parallel repeated estimates are stable")
    void testConcurrentDeterminism() throws InterruptedException {
        int nodeCount = 256;
        double[] coordinates = new double[nodeCount * 2];
        int edgeCount = nodeCount - 1;
        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        int[] edgeProfileIds = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];

        for (int i = 0; i < nodeCount; i++) {
            coordinates[i * 2] = -40.0d + (80.0d * i / (nodeCount - 1));
            coordinates[i * 2 + 1] = -120.0d + (240.0d * i / (nodeCount - 1));
            firstEdge[i] = i == 0 ? 0 : i - 1;
        }
        firstEdge[nodeCount] = edgeCount;

        for (int edge = 0; edge < edgeCount; edge++) {
            edgeTarget[edge] = edge + 1;
            edgeOrigin[edge] = edge;
            edgeProfileIds[edge] = 1;
            double d = greatCircleDistanceMeters(
                    coordinates[edge * 2],
                    coordinates[edge * 2 + 1],
                    coordinates[(edge + 1) * 2],
                    coordinates[(edge + 1) * 2 + 1]
            );
            baseWeights[edge] = (float) (2.0d * d);
        }

        Fixture fixture = createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfileIds,
                coordinates,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        GoalBoundHeuristic heuristic = provider.bindGoal(nodeCount - 1);

        double[] expected = new double[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            expected[node] = heuristic.estimateFromNode(node);
        }

        int threads = 8;
        int loopsPerThread = 100_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int seed = 1_000 + t;
            executor.execute(() -> {
                Random random = new Random(seed);
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        int node = random.nextInt(nodeCount);
                        double value = heuristic.estimateFromNode(node);
                        if (Double.doubleToLongBits(value) != Double.doubleToLongBits(expected[node])) {
                            failed.set(true);
                            break;
                        }
                    }
                } catch (Throwable t1) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS), "concurrency test timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "parallel repeated estimates must remain deterministic");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Performance smoke: estimate throughput remains within hot-path guardrail")
    void testPerformanceSmoke() {
        int nodeCount = 10_000;
        int edgeCount = nodeCount - 1;

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        int[] edgeProfileIds = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        double[] coordinates = new double[nodeCount * 2];

        for (int node = 0; node < nodeCount; node++) {
            coordinates[node * 2] = node;
            coordinates[node * 2 + 1] = node * 0.25d;
            firstEdge[node] = node == 0 ? 0 : node - 1;
        }
        firstEdge[nodeCount] = edgeCount;

        for (int edge = 0; edge < edgeCount; edge++) {
            edgeTarget[edge] = edge + 1;
            edgeOrigin[edge] = edge;
            edgeProfileIds[edge] = 1;
            double d = Math.hypot(1.0d, 0.25d);
            baseWeights[edge] = (float) (1.1d * d);
        }

        Fixture fixture = createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfileIds,
                coordinates,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        GoalBoundHeuristic heuristic = provider.bindGoal(nodeCount - 1);

        double warmup = 0.0d;
        for (int i = 0; i < 200_000; i++) {
            warmup += heuristic.estimateFromNode(i % nodeCount);
        }
        assertTrue(Double.isFinite(warmup));

        int loops = 2_000_000;
        long startNs = System.nanoTime();
        double checksum = 0.0d;
        for (int i = 0; i < loops; i++) {
            checksum += heuristic.estimateFromNode(i % nodeCount);
        }
        long elapsedNs = System.nanoTime() - startNs;

        double throughput = loops / (elapsedNs / 1_000_000_000.0d);
        assertTrue(Double.isFinite(checksum));
        assertTrue(throughput >= 100_000.0d, "estimate throughput too low: " + throughput + " est/s");
    }

    private Fixture createFixture(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            double[] coordinates,
            ProfileSpec... profiles
    ) {
        ByteBuffer model = buildModelBuffer(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfileIds,
                coordinates,
                profiles
        );
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                graph,
                profileStore,
                new LiveOverlay(128),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );
        return new Fixture(graph, profileStore, costEngine);
    }

    private ByteBuffer buildModelBuffer(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            double[] coordinates,
            ProfileSpec... profiles
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(16_384);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int baseWeightVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int edgeProfileVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);

        GraphTopology.startCoordinatesVector(builder, coordinates.length / 2);
        for (int i = (coordinates.length / 2) - 1; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    coordinates[i * 2],
                    coordinates[i * 2 + 1]
            );
        }
        int coordinatesVec = builder.endVector();

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addBaseWeights(builder, baseWeightVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addCoordinates(builder, coordinatesVec);
        int topology = GraphTopology.endGraphTopology(builder);

        int profilesVec = 0;
        if (profiles != null && profiles.length > 0) {
            int[] profileOffsets = new int[profiles.length];
            for (int i = 0; i < profiles.length; i++) {
                ProfileSpec profile = profiles[i];
                int buckets = TemporalProfile.createBucketsVector(builder, profile.buckets());
                profileOffsets[i] = TemporalProfile.createTemporalProfile(
                        builder,
                        profile.profileId(),
                        profile.dayMask(),
                        buckets,
                        profile.multiplier()
                );
            }
            profilesVec = Model.createProfilesVector(builder, profileOffsets);
        }

        int metadata = createMetadata(builder);
        Model.startModel(builder);
        Model.addMetadata(builder, metadata);
        Model.addTopology(builder, topology);
        if (profilesVec != 0) {
            Model.addProfiles(builder, profilesVec);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("stage11-geometry-test");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }

    private double greatCircleDistanceMeters(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        double lat1Rad = Math.toRadians(lat1Deg);
        double lat2Rad = Math.toRadians(lat2Deg);
        double deltaLatRad = Math.toRadians(lat2Deg - lat1Deg);
        double deltaLonRad = Math.toRadians(normalizeDeltaLongitude(lon2Deg - lon1Deg));

        double sinHalfLat = Math.sin(deltaLatRad * 0.5d);
        double sinHalfLon = Math.sin(deltaLonRad * 0.5d);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfLon * sinHalfLon;
        double clampedA = Math.max(0.0d, Math.min(1.0d, a));
        double c = 2.0d * Math.asin(Math.sqrt(clampedA));
        return 6_371_008.8d * c;
    }

    private double normalizeDeltaLongitude(double deltaLon) {
        double normalized = ((deltaLon + 540.0d) % 360.0d) - 180.0d;
        if (normalized == -180.0d) {
            return 180.0d;
        }
        return normalized;
    }
}
