package org.Aayush.routing.heuristic;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.testutil.TemporalTestContexts;
import org.Aayush.routing.testutil.TransitionTestContexts;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 11 Heuristic Admissibility Tests")
class HeuristicAdmissibilityTest {
    private static final int ALL_DAYS_MASK = 0x7F;
    private static final int WEEKDAY_MASK = 0x1F;
    private static final int BUCKET_SIZE_SECONDS = 3_600;
    // Jan 5, 1970 Monday 00:00:00 UTC
    private static final long MONDAY_00_00 = 345_600L;
    // Jan 4, 1970 Sunday 00:00:00 UTC
    private static final long SUNDAY_00_00 = 259_200L;
    private static final ResolvedTemporalContext CALENDAR_UTC_CONTEXT = TemporalTestContexts.calendarUtc();
    private static final ResolvedTransitionContext EDGE_BASED_CONTEXT = TransitionTestContexts.edgeBased();

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {
    }

    private record Fixture(EdgeGraph graph, ProfileStore profileStore, CostEngine costEngine) {
    }

    private record NodeState(int nodeId, double cost) {
    }

    @Test
    @DisplayName("Admissibility: Euclidean estimates never exceed shortest-path cost")
    void testEuclideanAdmissibility() {
        Fixture fixture = createFixture(
                6,
                new int[]{0, 2, 4, 6, 7, 8, 8},
                new int[]{1, 4, 2, 4, 3, 5, 5, 5},
                new int[]{0, 0, 1, 1, 2, 2, 3, 4},
                new float[]{
                        2.0f, (float) (2.0d * Math.sqrt(2.0d)),
                        2.0f, 2.0f,
                        2.0f, (float) (2.0d * Math.sqrt(2.0d)),
                        2.0f, 4.0f
                },
                new int[]{1, 2, 1, 2, 1, 2, 1, 2},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        2.0, 0.0,
                        3.0, 0.0,
                        1.0, 1.0,
                        3.0, 1.0
                },
                new ProfileSpec(1, WEEKDAY_MASK, new float[]{1.2f}, 1.0f),
                new ProfileSpec(2, ALL_DAYS_MASK, new float[]{1.5f}, 1.0f)
        );

        assertAdmissibleForTicks(fixture, HeuristicType.EUCLIDEAN, 5, MONDAY_00_00, SUNDAY_00_00);
    }

    @Test
    @DisplayName("Admissibility: Spherical estimates never exceed shortest-path cost")
    void testSphericalAdmissibility() {
        double[] coordinates = new double[]{
                0.0, 0.0,
                0.0, 1.0,
                0.0, 2.0,
                0.0, 3.0,
                1.0, 1.0,
                1.0, 3.0
        };

        double d01 = greatCircleDistanceMeters(0.0, 0.0, 0.0, 1.0);
        double d04 = greatCircleDistanceMeters(0.0, 0.0, 1.0, 1.0);
        double d12 = greatCircleDistanceMeters(0.0, 1.0, 0.0, 2.0);
        double d14 = greatCircleDistanceMeters(0.0, 1.0, 1.0, 1.0);
        double d23 = greatCircleDistanceMeters(0.0, 2.0, 0.0, 3.0);
        double d25 = greatCircleDistanceMeters(0.0, 2.0, 1.0, 3.0);
        double d35 = greatCircleDistanceMeters(0.0, 3.0, 1.0, 3.0);
        double d45 = greatCircleDistanceMeters(1.0, 1.0, 1.0, 3.0);

        Fixture fixture = createFixture(
                6,
                new int[]{0, 2, 4, 6, 7, 8, 8},
                new int[]{1, 4, 2, 4, 3, 5, 5, 5},
                new int[]{0, 0, 1, 1, 2, 2, 3, 4},
                new float[]{
                        (float) (2.0d * d01), (float) (2.0d * d04),
                        (float) (2.0d * d12), (float) (2.0d * d14),
                        (float) (2.0d * d23), (float) (2.0d * d25),
                        (float) (2.0d * d35), (float) (2.0d * d45)
                },
                new int[]{1, 2, 1, 2, 1, 2, 1, 2},
                coordinates,
                new ProfileSpec(1, WEEKDAY_MASK, new float[]{1.2f}, 1.0f),
                new ProfileSpec(2, ALL_DAYS_MASK, new float[]{1.5f}, 1.0f)
        );

        assertAdmissibleForTicks(fixture, HeuristicType.SPHERICAL, 5, MONDAY_00_00, SUNDAY_00_00);
    }

    @Test
    @Timeout(value = 25, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: randomized admissibility remains valid for EUCLIDEAN and SPHERICAL")
    void testRandomizedAdmissibilityStress() {
        int nodeCount = 256;
        int edgeCount = nodeCount - 1;

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        int[] edgeProfileIds = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        double[] coordinates = new double[nodeCount * 2];

        for (int node = 0; node < nodeCount; node++) {
            firstEdge[node] = Math.min(node, edgeCount);
            coordinates[node * 2] = 0.0d;
            coordinates[node * 2 + 1] = -120.0d + (node * 0.01d);
        }
        firstEdge[nodeCount] = edgeCount;

        for (int edge = 0; edge < edgeCount; edge++) {
            edgeTarget[edge] = edge + 1;
            edgeOrigin[edge] = edge;
            edgeProfileIds[edge] = (edge & 1) == 0 ? 1 : 2;
            double d = greatCircleDistanceMeters(
                    coordinates[edge * 2],
                    coordinates[edge * 2 + 1],
                    coordinates[(edge + 1) * 2],
                    coordinates[(edge + 1) * 2 + 1]
            );
            baseWeights[edge] = (float) (2.2d * d);
        }

        Fixture fixture = createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfileIds,
                coordinates,
                new ProfileSpec(1, WEEKDAY_MASK, new float[]{1.10f}, 1.0f),
                new ProfileSpec(2, ALL_DAYS_MASK, new float[]{1.35f}, 1.0f)
        );

        int goalNode = nodeCount - 1;
        GoalBoundHeuristic euclidean = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(goalNode);
        GoalBoundHeuristic spherical = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(goalNode);

        Random random = new Random(12345);
        int queries = 2_000;
        for (int i = 0; i < queries; i++) {
            int source = random.nextInt(nodeCount);
            long tick = (i & 1) == 0 ? MONDAY_00_00 : SUNDAY_00_00;
            double shortest = shortestPathCost(fixture.graph(), fixture.costEngine(), source, goalNode, tick);
            double e = euclidean.estimateFromNode(source);
            double s = spherical.estimateFromNode(source);

            assertTrue(Double.isFinite(shortest));
            assertTrue(Double.isFinite(e) && e >= 0.0d);
            assertTrue(Double.isFinite(s) && s >= 0.0d);
            assertTrue(e <= shortest + 1e-4d, "euclidean inadmissible at query " + i);
            assertTrue(s <= shortest + 1e-4d, "spherical inadmissible at query " + i);
        }
    }

    @Test
    @Timeout(value = 25, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Performance smoke: admissibility query throughput remains practical")
    void testAdmissibilityPerformanceSmoke() {
        int nodeCount = 192;
        int edgeCount = nodeCount - 1;

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        int[] edgeProfileIds = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        double[] coordinates = new double[nodeCount * 2];

        for (int node = 0; node < nodeCount; node++) {
            firstEdge[node] = Math.min(node, edgeCount);
            coordinates[node * 2] = 0.0d;
            coordinates[node * 2 + 1] = -90.0d + (node * 0.02d);
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
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.2f}, 1.0f)
        );

        int goalNode = nodeCount - 1;
        GoalBoundHeuristic euclidean = HeuristicFactory.create(
                HeuristicType.EUCLIDEAN,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(goalNode);
        GoalBoundHeuristic spherical = HeuristicFactory.create(
                HeuristicType.SPHERICAL,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        ).bindGoal(goalNode);

        Random random = new Random(7);
        int warmup = 100;
        for (int i = 0; i < warmup; i++) {
            int source = random.nextInt(nodeCount);
            shortestPathCost(fixture.graph(), fixture.costEngine(), source, goalNode, MONDAY_00_00);
            euclidean.estimateFromNode(source);
            spherical.estimateFromNode(source);
        }

        int queries = 600;
        long startNs = System.nanoTime();
        double checksum = 0.0d;
        for (int i = 0; i < queries; i++) {
            int source = random.nextInt(nodeCount);
            long tick = (i & 1) == 0 ? MONDAY_00_00 : SUNDAY_00_00;
            double shortest = shortestPathCost(fixture.graph(), fixture.costEngine(), source, goalNode, tick);
            double e = euclidean.estimateFromNode(source);
            double s = spherical.estimateFromNode(source);
            checksum += shortest + e + s;
            assertTrue(e <= shortest + 1e-4d);
            assertTrue(s <= shortest + 1e-4d);
        }
        long elapsedNs = System.nanoTime() - startNs;
        double throughput = queries / (elapsedNs / 1_000_000_000.0d);

        assertTrue(Double.isFinite(checksum));
        assertTrue(throughput >= 20.0d, "admissibility throughput too low: " + throughput + " queries/s");
    }

    private void assertAdmissibleForTicks(
            Fixture fixture,
            HeuristicType type,
            int goalNodeId,
            long... departureTicks
    ) {
        HeuristicProvider provider = HeuristicFactory.create(
                type,
                fixture.graph(),
                fixture.profileStore(),
                fixture.costEngine()
        );
        GoalBoundHeuristic heuristic = provider.bindGoal(goalNodeId);

        for (long tick : departureTicks) {
            for (int source = 0; source < fixture.graph().nodeCount(); source++) {
                double estimate = heuristic.estimateFromNode(source);
                double shortestCost = shortestPathCost(
                        fixture.graph(),
                        fixture.costEngine(),
                        source,
                        goalNodeId,
                        tick
                );

                assertTrue(Double.isFinite(estimate), "estimate must be finite for source " + source);
                assertTrue(estimate >= 0.0d, "estimate must be non-negative for source " + source);

                if (Double.isFinite(shortestCost)) {
                    assertTrue(
                            estimate <= shortestCost + 1e-4d,
                            "heuristic must be admissible for source " + source
                                    + ", tick " + tick
                                    + ", estimate " + estimate
                                    + ", shortest " + shortestCost
                    );
                }
            }
        }
    }

    private double shortestPathCost(
            EdgeGraph graph,
            CostEngine costEngine,
            int sourceNodeId,
            int goalNodeId,
            long departureTicks
    ) {
        double[] best = new double[graph.nodeCount()];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        best[sourceNodeId] = 0.0d;

        PriorityQueue<NodeState> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeState::cost));
        pq.add(new NodeState(sourceNodeId, 0.0d));

        EdgeGraph.EdgeIterator iterator = graph.iterator();
        while (!pq.isEmpty()) {
            NodeState state = pq.poll();
            if (state.cost() > best[state.nodeId()]) {
                continue;
            }
            if (state.nodeId() == goalNodeId) {
                return state.cost();
            }

            iterator.resetForNode(state.nodeId());
            while (iterator.hasNext()) {
                int edgeId = iterator.next();
                float edgeCost = costEngine.computeEdgeCost(
                        edgeId,
                        CostEngine.NO_PREDECESSOR,
                        departureTicks,
                        CALENDAR_UTC_CONTEXT,
                        EDGE_BASED_CONTEXT
                );
                if (edgeCost == Float.POSITIVE_INFINITY) {
                    continue;
                }
                int nextNode = graph.getEdgeDestination(edgeId);
                double candidate = state.cost() + edgeCost;
                if (candidate < best[nextNode]) {
                    best[nextNode] = candidate;
                    pq.add(new NodeState(nextNode, candidate));
                }
            }
        }

        return Double.POSITIVE_INFINITY;
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
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
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
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
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
        int modelVersion = builder.createString("stage11-admissibility-test");
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
