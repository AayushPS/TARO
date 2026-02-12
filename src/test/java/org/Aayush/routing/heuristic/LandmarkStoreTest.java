package org.Aayush.routing.heuristic;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Landmark;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LandmarkStore Contract Tests")
class LandmarkStoreTest {

    @Test
    @DisplayName("Distance accessors validate indices and return expected values")
    void testDistanceAccessorsAndIndexValidation() {
        LandmarkStore store = new LandmarkStore(
                3,
                new int[]{1},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{2.0f, 1.0f, 0.0f}}
        );

        assertEquals(1.0f, store.forwardDistance(0, 1), 0.0f);
        assertEquals(1.0f, store.backwardDistance(0, 1), 0.0f);

        assertThrows(IllegalArgumentException.class, () -> store.forwardDistance(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> store.forwardDistance(1, 0));
        assertThrows(IllegalArgumentException.class, () -> store.backwardDistance(0, -1));
        assertThrows(IllegalArgumentException.class, () -> store.backwardDistance(0, 3));
    }

    @Test
    @DisplayName("landmarkNodeIdsCopy is defensive")
    void testLandmarkNodeIdsCopyDefensive() {
        LandmarkStore store = new LandmarkStore(
                3,
                new int[]{0, 2},
                new float[][]{
                        {0.0f, 1.0f, 2.0f},
                        {2.0f, 1.0f, 0.0f}
                },
                new float[][]{
                        {2.0f, 1.0f, 0.0f},
                        {0.0f, 1.0f, 2.0f}
                }
        );

        int[] ids = store.landmarkNodeIdsCopy();
        ids[0] = 99;
        assertArrayEquals(new int[]{0, 2}, store.landmarkNodeIdsCopy());
    }

    @Test
    @DisplayName("fromModel loads landmarks without compatibility signature")
    void testFromModelLeavesSignatureUnknown() {
        ByteBuffer modelBuffer = buildModelWithLandmarks(
                3,
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{2.0f, 1.0f, 0.0f}}
        );
        Model model = Model.getRootAsModel(modelBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        LandmarkStore store = LandmarkStore.fromModel(model, 3);
        assertFalse(store.hasCompatibilitySignature());
        assertEquals(3, store.getNodeCount());
        assertEquals(1, store.landmarkCount());
    }

    @Test
    @DisplayName("fromFlatBuffer computes compatibility signature and rejects bad identifier")
    void testFromFlatBufferIdentifierAndSignature() {
        ByteBuffer invalid = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(IllegalArgumentException.class, () -> LandmarkStore.fromFlatBuffer(invalid, 3));

        ByteBuffer modelBuffer = buildModelWithLandmarks(
                3,
                new int[]{0},
                new float[][]{{0.0f, 1.0f, 2.0f}},
                new float[][]{{2.0f, 1.0f, 0.0f}}
        );
        LandmarkStore store = LandmarkStore.fromFlatBuffer(modelBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN), 3);
        assertTrue(store.hasCompatibilitySignature());
    }

    @Test
    @DisplayName("fromFlatBuffer rejects invalid landmark distance values")
    void testFromFlatBufferRejectsInvalidDistances() {
        ByteBuffer modelBuffer = buildModelWithLandmarks(
                3,
                new int[]{0},
                new float[][]{{0.0f, -1.0f, 2.0f}},
                new float[][]{{2.0f, 1.0f, 0.0f}}
        );
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LandmarkStore.fromFlatBuffer(modelBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN), 3)
        );
        assertTrue(ex.getMessage().contains("must be >= 0"));
    }

    private ByteBuffer buildModelWithLandmarks(
            int nodeCount,
            int[] landmarkNodeIds,
            float[][] forwardDistances,
            float[][] backwardDistances
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, new int[nodeCount + 1]);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, new int[0]);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, new int[0]);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, new float[0]);
        int edgeProfileIdVec = GraphTopology.createEdgeProfileIdVector(builder, new int[0]);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, 0);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIdVec);
        int topology = GraphTopology.endGraphTopology(builder);

        int[] landmarkOffsets = new int[landmarkNodeIds.length];
        for (int i = 0; i < landmarkNodeIds.length; i++) {
            int forward = Landmark.createForwardDistancesVector(builder, forwardDistances[i]);
            int backward = Landmark.createBackwardDistancesVector(builder, backwardDistances[i]);
            landmarkOffsets[i] = Landmark.createLandmark(builder, landmarkNodeIds[i], forward, backward);
        }
        int landmarksVec = Model.createLandmarksVector(builder, landmarkOffsets);

        int profile = TemporalProfile.createTemporalProfile(
                builder,
                1,
                RoutingFixtureFactory.ALL_DAYS_MASK,
                TemporalProfile.createBucketsVector(builder, new float[]{1.0f}),
                1.0f
        );
        int profilesVec = Model.createProfilesVector(builder, new int[]{profile});

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
