package org.Aayush.routing.heuristic;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 12 Landmark Preprocessor Tests")
class LandmarkPreprocessorTest {

    @Test
    @DisplayName("Deterministic landmark selection and distances under fixed seed")
    void testDeterministicPreprocess() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkPreprocessorConfig config = LandmarkPreprocessorConfig.builder()
                .landmarkCount(2)
                .selectionSeed(11L)
                .maxSettledNodesPerLandmark(Integer.MAX_VALUE)
                .build();

        LandmarkArtifact artifactA = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                config
        );
        LandmarkArtifact artifactB = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                config
        );

        assertEquals(5, artifactA.getNodeCount());
        assertEquals(2, artifactA.landmarkCount());
        assertArrayEquals(artifactA.landmarkNodeIdsCopy(), artifactB.landmarkNodeIdsCopy());
        for (int i = 0; i < artifactA.landmarkCount(); i++) {
            assertArrayEquals(artifactA.forwardDistancesCopy(i), artifactB.forwardDistancesCopy(i));
            assertArrayEquals(artifactA.backwardDistancesCopy(i), artifactB.backwardDistancesCopy(i));
        }
    }

    @Test
    @DisplayName("Disconnected graphs keep unreachable nodes at +INF")
    void testDisconnectedGraphUnreachableDistances() {
        RoutingFixtureFactory.Fixture fixture = createDisconnectedFixture();
        LandmarkPreprocessorConfig config = LandmarkPreprocessorConfig.builder()
                .landmarkCount(4)
                .selectionSeed(99L)
                .maxSettledNodesPerLandmark(Integer.MAX_VALUE)
                .build();

        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                config
        );

        boolean foundInfinity = false;
        for (int i = 0; i < artifact.landmarkCount(); i++) {
            for (float value : artifact.forwardDistancesCopy(i)) {
                if (value == Float.POSITIVE_INFINITY) {
                    foundInfinity = true;
                    break;
                }
            }
            if (foundInfinity) {
                break;
            }
        }
        assertTrue(foundInfinity, "at least one unreachable pair should remain +INF");
    }

    @Test
    @DisplayName("Config validation rejects non-positive landmark count and budget")
    void testConfigValidation() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();

        LandmarkPreprocessorConfig badCount = LandmarkPreprocessorConfig.builder()
                .landmarkCount(0)
                .selectionSeed(1L)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> LandmarkPreprocessor.preprocess(fixture.edgeGraph(), fixture.profileStore(), badCount));

        LandmarkPreprocessorConfig badBudget = LandmarkPreprocessorConfig.builder()
                .landmarkCount(1)
                .selectionSeed(1L)
                .maxSettledNodesPerLandmark(0)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> LandmarkPreprocessor.preprocess(fixture.edgeGraph(), fixture.profileStore(), badBudget));
    }

    @Test
    @DisplayName("Landmark serialization roundtrip loads valid LandmarkStore")
    void testLandmarkSerializerRoundtrip() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                LandmarkPreprocessorConfig.builder()
                        .landmarkCount(2)
                        .selectionSeed(5L)
                        .build()
        );

        ByteBuffer modelWithLandmarks = buildModelWithLandmarks(
                artifact,
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1}
        );

        LandmarkStore store = LandmarkStore.fromFlatBuffer(modelWithLandmarks, 5);
        assertEquals(5, store.getNodeCount());
        assertEquals(2, store.landmarkCount());
        assertTrue(store.hasCompatibilitySignature());
        assertTrue(store.lowerBound(0, 4) >= 0.0d);
    }

    @Test
    @DisplayName("Weekday-only profile keeps Sunday ALT lower bounds admissible")
    void testWeekdayOnlyProfileSundayAdmissibility() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        2.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        0x1F, // Mon..Fri only
                        new float[]{2.0f},
                        1.0f
                )
        );

        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                fixture.edgeGraph(),
                fixture.profileStore(),
                LandmarkPreprocessorConfig.builder()
                        .landmarkCount(3)
                        .selectionSeed(7L)
                        .maxSettledNodesPerLandmark(Integer.MAX_VALUE)
                        .build()
        );
        LandmarkStore store = LandmarkStore.fromArtifact(artifact);

        RouteCore core = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .build();
        RouteResponse sundayRoute = core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N2")
                .departureTicks(259_200L) // Jan 4, 1970 Sunday 00:00:00 UTC
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());

        assertTrue(sundayRoute.isReachable());
        assertEquals(2.0f, sundayRoute.getTotalCost(), 1e-6f);
        double sundayLowerBound = store.lowerBound(0, 2);
        assertTrue(
                sundayLowerBound <= sundayRoute.getTotalCost() + 1e-6d,
                "Sunday lower bound must not overestimate when day-mask fallback multiplier is 1.0"
        );
    }

    @Test
    @Timeout(value = 12, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress/perf: preprocessing remains deterministic on larger random graph")
    void testPreprocessStressAndPerf() {
        int nodeCount = 300;
        int degreePerNode = 4;
        int edgeCount = nodeCount * degreePerNode;
        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfiles = new int[edgeCount];
        double[] coordinates = new double[nodeCount * 2];

        Random random = new Random(1234);
        int cursor = 0;
        for (int node = 0; node < nodeCount; node++) {
            firstEdge[node] = cursor;
            for (int d = 0; d < degreePerNode; d++) {
                int target = random.nextInt(nodeCount);
                edgeTarget[cursor] = target;
                edgeOrigin[cursor] = node;
                baseWeights[cursor] = 1.0f + random.nextFloat() * 4.0f;
                edgeProfiles[cursor] = 1;
                cursor++;
            }
            coordinates[node * 2] = node * 0.001d;
            coordinates[node * 2 + 1] = node * 0.002d;
        }
        firstEdge[nodeCount] = edgeCount;

        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfiles,
                coordinates,
                new RoutingFixtureFactory.ProfileSpec(1, RoutingFixtureFactory.ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );

        LandmarkPreprocessorConfig config = LandmarkPreprocessorConfig.builder()
                .landmarkCount(16)
                .selectionSeed(777L)
                .maxSettledNodesPerLandmark(nodeCount)
                .build();

        long start = System.nanoTime();
        LandmarkArtifact first = LandmarkPreprocessor.preprocess(fixture.edgeGraph(), fixture.profileStore(), config);
        long elapsedNs = System.nanoTime() - start;
        LandmarkArtifact second = LandmarkPreprocessor.preprocess(fixture.edgeGraph(), fixture.profileStore(), config);

        assertEquals(16, first.landmarkCount());
        assertArrayEquals(first.landmarkNodeIdsCopy(), second.landmarkNodeIdsCopy());
        assertTrue(elapsedNs < 2_000_000_000L, "preprocessing should complete in under 2 seconds");
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

    private RoutingFixtureFactory.Fixture createDisconnectedFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 1, 1, 2, 2},
                new int[]{1, 3},
                new int[]{0, 2},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0, 0.0,
                        1.0, 0.0,
                        10.0, 0.0,
                        11.0, 0.0
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private ByteBuffer buildModelWithLandmarks(
            LandmarkArtifact artifact,
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int edgeProfileIdVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIdVec);
        int topology = GraphTopology.endGraphTopology(builder);

        int profiles = TemporalProfile.createTemporalProfile(
                builder,
                1,
                RoutingFixtureFactory.ALL_DAYS_MASK,
                TemporalProfile.createBucketsVector(builder, new float[]{1.0f}),
                1.0f
        );
        int profilesVec = Model.createProfilesVector(builder, new int[]{profiles});
        int landmarksVec = LandmarkSerializer.createLandmarksVector(builder, artifact);
        int metadata = RoutingFixtureFactory.createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadata);
        Model.addTopology(builder, topology);
        Model.addProfiles(builder, profilesVec);
        Model.addLandmarks(builder, landmarksVec);
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }
}
