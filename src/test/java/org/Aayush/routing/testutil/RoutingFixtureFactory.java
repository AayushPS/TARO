package org.Aayush.routing.testutil;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.FastUtilIDMapper;
import org.Aayush.core.id.IDMapper;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared test fixture factory for routing tests.
 */
public final class RoutingFixtureFactory {
    public static final int ALL_DAYS_MASK = 0x7F;
    public static final int BUCKET_SIZE_SECONDS = 3_600;

    private RoutingFixtureFactory() {
    }

    public record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {
    }

    public record Fixture(
            ByteBuffer modelBuffer,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            IDMapper nodeIdMapper
    ) {
    }

    public static Fixture createFixture(
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
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(Math.max(16, edgeGraph.edgeCount())),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        Map<String, Integer> mappings = new HashMap<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            mappings.put("N" + i, i);
        }
        IDMapper mapper = new FastUtilIDMapper(mappings);
        return new Fixture(model, edgeGraph, profileStore, costEngine, mapper);
    }

    public static ByteBuffer buildModelBuffer(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            double[] coordinates,
            ProfileSpec... profiles
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int profileIdVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);

        int coordinatesVec = 0;
        if (coordinates != null) {
            if (coordinates.length != nodeCount * 2) {
                throw new IllegalArgumentException(
                        "coordinates length mismatch: " + coordinates.length + " != " + (nodeCount * 2)
                );
            }
            GraphTopology.startCoordinatesVector(builder, nodeCount);
            for (int i = nodeCount - 1; i >= 0; i--) {
                org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                        builder,
                        coordinates[i * 2],
                        coordinates[(i * 2) + 1]
                );
            }
            coordinatesVec = builder.endVector();
        }

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, profileIdVec);
        if (coordinatesVec != 0) {
            GraphTopology.addCoordinates(builder, coordinatesVec);
        }
        int topologyOffset = GraphTopology.endGraphTopology(builder);

        int profilesOffset = 0;
        if (profiles != null && profiles.length > 0) {
            int[] profileOffsets = new int[profiles.length];
            for (int i = 0; i < profiles.length; i++) {
                ProfileSpec profile = profiles[i];
                int bucketsOffset = TemporalProfile.createBucketsVector(builder, profile.buckets());
                profileOffsets[i] = TemporalProfile.createTemporalProfile(
                        builder,
                        profile.profileId(),
                        profile.dayMask(),
                        bucketsOffset,
                        profile.multiplier()
                );
            }
            profilesOffset = Model.createProfilesVector(builder, profileOffsets);
        }

        int metadataOffset = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataOffset);
        Model.addTopology(builder, topologyOffset);
        if (profilesOffset != 0) {
            Model.addProfiles(builder, profilesOffset);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("test-v11");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }
}

