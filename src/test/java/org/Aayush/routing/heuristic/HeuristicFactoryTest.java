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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 11 HeuristicFactory Tests")
class HeuristicFactoryTest {

    private static final int ALL_DAYS_MASK = 0x7F;
    private static final int BUCKET_SIZE_SECONDS = 3_600;

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {
    }

    private record Fixture(EdgeGraph graph, ProfileStore profileStore, CostEngine costEngine) {
    }

    @Test
    @DisplayName("Rejects unspecified heuristic type (no implicit default)")
    void testRejectsUnspecifiedType() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        null,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_TYPE_REQUIRED, ex.reasonCode());
        assertTrue(ex.getMessage().contains("[" + HeuristicFactory.REASON_TYPE_REQUIRED + "]"));
    }

    @Test
    @DisplayName("Rejects null edgeGraph deterministically")
    void testRejectsNullEdgeGraph() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.NONE,
                        null,
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );
        assertEquals(HeuristicFactory.REASON_GRAPH_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("Rejects null profileStore deterministically")
    void testRejectsNullProfileStore() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.NONE,
                        fixture.graph(),
                        null,
                        fixture.costEngine()
                )
        );
        assertEquals(HeuristicFactory.REASON_PROFILE_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("Rejects null costEngine deterministically")
    void testRejectsNullCostEngine() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.NONE,
                        fixture.graph(),
                        fixture.profileStore(),
                        null
                )
        );
        assertEquals(HeuristicFactory.REASON_COST_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("NONE mode is valid even when coordinates are absent")
    void testNoneModeWithoutCoordinates() {
        Fixture fixture = createFixture(
                null,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.NONE,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );

        assertEquals(HeuristicType.NONE, provider.type());
        GoalBoundHeuristic bound = provider.bindGoal(1);
        assertEquals(0.0d, bound.estimateFromNode(0), 0.0d);
        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(-1));
        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(2));
    }

    @Test
    @DisplayName("EUCLIDEAN mode requires coordinates")
    void testEuclideanRequiresCoordinates() {
        Fixture fixture = createFixture(
                null,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.EUCLIDEAN,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_COORDINATES_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("EUCLIDEAN mode rejects non-finite x even on isolated nodes")
    void testEuclideanRejectsNonFiniteXOnIsolatedNode() {
        Fixture fixture = createThreeNodeSingleEdgeFixture(
                new double[]{0.0, 0.0, 1.0, 1.0, Double.NaN, 5.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.EUCLIDEAN,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_EUCLIDEAN_X_NON_FINITE, ex.reasonCode());
    }

    @Test
    @DisplayName("EUCLIDEAN mode rejects non-finite y even on isolated nodes")
    void testEuclideanRejectsNonFiniteYOnIsolatedNode() {
        Fixture fixture = createThreeNodeSingleEdgeFixture(
                new double[]{0.0, 0.0, 1.0, 1.0, 2.0, Double.POSITIVE_INFINITY},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.EUCLIDEAN,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_EUCLIDEAN_Y_NON_FINITE, ex.reasonCode());
    }

    @Test
    @DisplayName("EUCLIDEAN mode allows wide finite ranges and keeps estimates finite")
    void testEuclideanAllowsWideFiniteRanges() {
        Fixture fixture = createThreeNodeSingleEdgeFixture(
                new double[]{-1.0e308, 0.0, 0.0, 0.0, 1.0e308, 0.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        double estimate = provider.bindGoal(2).estimateFromNode(0);
        assertTrue(Double.isFinite(estimate));
        assertEquals(0.0d, estimate, 0.0d);
    }

    @Test
    @DisplayName("EUCLIDEAN mode degrades to zero when estimate overflows")
    void testEuclideanDegradesToZeroWhenEstimateOverflows() {
        Fixture fixture = createThreeNodeSingleEdgeFixture(
                new double[]{0.0, 0.0, 1.0, 0.0, 1.0e308, 0.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicProvider provider = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        double estimate = provider.bindGoal(2).estimateFromNode(0);
        assertTrue(Double.isFinite(estimate));
        assertEquals(0.0d, estimate, 0.0d);
    }

    @Test
    @DisplayName("EUCLIDEAN bound estimator validates nodeId deterministically")
    void testEuclideanEstimateNodeBounds() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        GoalBoundHeuristic bound = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(1);

        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(-1));
        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(2));
    }

    @Test
    @DisplayName("SPHERICAL mode requires coordinates")
    void testSphericalRequiresCoordinates() {
        Fixture fixture = createFixture(
                null,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.SPHERICAL,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_COORDINATES_REQUIRED, ex.reasonCode());
    }

    @Test
    @DisplayName("SPHERICAL mode rejects latitude outside [-90,90]")
    void testSphericalRejectsLatitudeRange() {
        Fixture fixture = createFixture(
                new double[]{95.0, 0.0, 0.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.SPHERICAL,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_SPHERICAL_LAT_RANGE, ex.reasonCode());
    }

    @Test
    @DisplayName("SPHERICAL mode rejects longitude outside [-180,180]")
    void testSphericalRejectsLongitudeRange() {
        Fixture fixture = createFixture(
                new double[]{0.0, 181.0, 0.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.SPHERICAL,
                        fixture.graph(),
                        fixture.profileStore(),
                        fixture.costEngine()
                )
        );

        assertEquals(HeuristicFactory.REASON_SPHERICAL_LON_RANGE, ex.reasonCode());
    }

    @Test
    @DisplayName("SPHERICAL bound estimator validates nodeId deterministically")
    void testSphericalEstimateNodeBounds() {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 0.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        GoalBoundHeuristic bound = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(1);

        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(-1));
        assertThrows(IllegalArgumentException.class, () -> bound.estimateFromNode(2));
    }

    @Test
    @DisplayName("Factory rejects cost-engine graph mismatch deterministically")
    void testCostEngineGraphMismatch() {
        Fixture fixtureA = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );
        Fixture fixtureB = createFixture(
                new double[]{0.0, 0.0, 2.0, 2.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.EUCLIDEAN,
                        fixtureA.graph(),
                        fixtureA.profileStore(),
                        fixtureB.costEngine()
                )
        );

        assertEquals(GeometryLowerBoundModel.REASON_COST_GRAPH_MISMATCH, ex.reasonCode());
    }

    @Test
    @DisplayName("Factory rejects cost-engine profile mismatch deterministically")
    void testCostEngineProfileMismatch() {
        Fixture fixtureA = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );
        Fixture fixtureB = createFixture(
                new double[]{0.0, 0.0, 1.0, 1.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{2.0f}, 1.0f)
        );

        CostEngine mismatchedCostEngine = new CostEngine(
                fixtureA.graph(),
                fixtureB.profileStore(),
                new LiveOverlay(16),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        HeuristicConfigurationException ex = assertThrows(
                HeuristicConfigurationException.class,
                () -> HeuristicFactory.create(
                        HeuristicType.EUCLIDEAN,
                        fixtureA.graph(),
                        fixtureA.profileStore(),
                        mismatchedCostEngine
                )
        );

        assertEquals(GeometryLowerBoundModel.REASON_COST_PROFILE_MISMATCH, ex.reasonCode());
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: concurrent create/bind remains deterministic across modes")
    void testConcurrentFactoryStress() throws InterruptedException {
        Fixture fixture = createFixture(
                new double[]{0.0, 0.0, 3.0, 4.0},
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        double expectedNone = HeuristicFactory.create(
                HeuristicType.NONE,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(1).estimateFromNode(0);
        double expectedEuclidean = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(1).estimateFromNode(0);
        double expectedSpherical = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(1).estimateFromNode(0);
        double[] expected = new double[]{expectedNone, expectedEuclidean, expectedSpherical};
        HeuristicType[] types = new HeuristicType[]{
                HeuristicType.NONE,
                HeuristicType.EUCLIDEAN,
                HeuristicType.SPHERICAL
        };

        int threads = 8;
        int loopsPerThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int seed = 42 + t;
            executor.execute(() -> {
                Random random = new Random(seed);
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        int mode = random.nextInt(types.length);
                        HeuristicProvider provider = HeuristicFactory.create(
                                types[mode],
                                fixture.graph(),
                                fixture.profileStore(),
                                fixture.costEngine()
                        );
                        double estimate = provider.bindGoal(1).estimateFromNode(0);
                        if (Double.doubleToLongBits(estimate) != Double.doubleToLongBits(expected[mode])) {
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

        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS), "factory stress test timed out");
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent factory create/bind produced non-deterministic estimates");
    }

    @Test
    @Timeout(value = 20, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Performance smoke: NONE factory throughput remains practical")
    void testFactoryPerformanceSmoke() {
        Fixture fixture = createFixture(
                null,
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        int warmupLoops = 50_000;
        for (int i = 0; i < warmupLoops; i++) {
            HeuristicProvider provider = HeuristicFactory.create(
                    HeuristicType.NONE,
                    fixture.graph(),
                    fixture.profileStore(),
                    fixture.costEngine()
            );
            provider.bindGoal(i & 1).estimateFromNode(i & 1);
        }

        int loops = 300_000;
        long startNs = System.nanoTime();
        double checksum = 0.0d;
        for (int i = 0; i < loops; i++) {
            HeuristicProvider provider = HeuristicFactory.create(
                    HeuristicType.NONE,
                    fixture.graph(),
                    fixture.profileStore(),
                    fixture.costEngine()
            );
            checksum += provider.bindGoal(i & 1).estimateFromNode(i & 1);
        }
        long elapsedNs = System.nanoTime() - startNs;
        double throughput = loops / (elapsedNs / 1_000_000_000.0d);

        assertTrue(Double.isFinite(checksum));
        assertTrue(throughput >= 5_000.0d, "factory throughput too low: " + throughput + " ops/s");
    }

    private Fixture createFixture(double[] coordinates, ProfileSpec... profiles) {
        ByteBuffer model = buildModelBuffer(coordinates, profiles);
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                graph,
                profileStore,
                new LiveOverlay(16),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );
        return new Fixture(graph, profileStore, costEngine);
    }

    private Fixture createThreeNodeSingleEdgeFixture(double[] coordinates, ProfileSpec... profiles) {
        ByteBuffer model = buildThreeNodeSingleEdgeModelBuffer(coordinates, profiles);
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                graph,
                profileStore,
                new LiveOverlay(16),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );
        return new Fixture(graph, profileStore, costEngine);
    }

    private ByteBuffer buildModelBuffer(double[] coordinates, ProfileSpec... profiles) {
        FlatBufferBuilder builder = new FlatBufferBuilder(2048);

        int firstEdge = GraphTopology.createFirstEdgeVector(builder, new int[]{0, 1, 1});
        int edgeTarget = GraphTopology.createEdgeTargetVector(builder, new int[]{1});
        int baseWeights = GraphTopology.createBaseWeightsVector(builder, new float[]{10.0f});
        int edgeProfileIds = GraphTopology.createEdgeProfileIdVector(builder, new int[]{1});
        int edgeOrigin = GraphTopology.createEdgeOriginVector(builder, new int[]{0});
        int coordinatesVec = 0;
        if (coordinates != null) {
            GraphTopology.startCoordinatesVector(builder, coordinates.length / 2);
            for (int i = (coordinates.length / 2) - 1; i >= 0; i--) {
                org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                        builder,
                        coordinates[i * 2],
                        coordinates[i * 2 + 1]
                );
            }
            coordinatesVec = builder.endVector();
        }

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, 2);
        GraphTopology.addEdgeCount(builder, 1);
        GraphTopology.addFirstEdge(builder, firstEdge);
        GraphTopology.addEdgeTarget(builder, edgeTarget);
        GraphTopology.addBaseWeights(builder, baseWeights);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIds);
        GraphTopology.addEdgeOrigin(builder, edgeOrigin);
        if (coordinatesVec != 0) {
            GraphTopology.addCoordinates(builder, coordinatesVec);
        }
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

    private ByteBuffer buildThreeNodeSingleEdgeModelBuffer(double[] coordinates, ProfileSpec... profiles) {
        if (coordinates == null || coordinates.length != 6) {
            throw new IllegalArgumentException("three-node fixture requires exactly 3 coordinate pairs");
        }

        FlatBufferBuilder builder = new FlatBufferBuilder(2048);

        int firstEdge = GraphTopology.createFirstEdgeVector(builder, new int[]{0, 1, 1, 1});
        int edgeTarget = GraphTopology.createEdgeTargetVector(builder, new int[]{1});
        int baseWeights = GraphTopology.createBaseWeightsVector(builder, new float[]{10.0f});
        int edgeProfileIds = GraphTopology.createEdgeProfileIdVector(builder, new int[]{1});
        int edgeOrigin = GraphTopology.createEdgeOriginVector(builder, new int[]{0});

        GraphTopology.startCoordinatesVector(builder, 3);
        for (int i = 2; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    coordinates[i * 2],
                    coordinates[i * 2 + 1]
            );
        }
        int coordinatesVec = builder.endVector();

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, 3);
        GraphTopology.addEdgeCount(builder, 1);
        GraphTopology.addFirstEdge(builder, firstEdge);
        GraphTopology.addEdgeTarget(builder, edgeTarget);
        GraphTopology.addBaseWeights(builder, baseWeights);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIds);
        GraphTopology.addEdgeOrigin(builder, edgeOrigin);
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
        int modelVersion = builder.createString("stage11-factory-test");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }
}
