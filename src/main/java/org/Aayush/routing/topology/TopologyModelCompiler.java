package org.Aayush.routing.topology;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.FastUtilIDMapper;
import org.Aayush.core.id.IDMapper;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.IdMapping;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles source-level topology inputs into a TARO model buffer.
 */
public final class TopologyModelCompiler {

    public CompiledTopologyModel compile(TopologyModelSource source) {
        TopologyModelSource nonNullSource = java.util.Objects.requireNonNull(source, "source");
        nonNullSource.validate();

        FlatBufferBuilder builder = new FlatBufferBuilder(4096);
        TopologyIndexLayout layout = TopologyIndexLayout.fromSource(nonNullSource);
        List<TopologyModelSource.NodeDefinition> nodes = layout.nodes();
        List<TopologyIndexLayout.OrderedEdge> orderedEdges = layout.orderedEdges();
        Map<String, Integer> nodeIndexById = layout.nodeIndexById();

        int nodeCount = nodes.size();
        int edgeCount = orderedEdges.size();
        int[] degreeByNode = new int[nodeCount];
        for (TopologyIndexLayout.OrderedEdge edge : orderedEdges) {
            degreeByNode[edge.originNodeIndex()]++;
        }

        int[] firstEdge = new int[nodeCount + 1];
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            firstEdge[nodeIndex + 1] = firstEdge[nodeIndex] + degreeByNode[nodeIndex];
        }

        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfileIds = new int[edgeCount];
        LinkedHashMap<String, Integer> edgeIndexById = new LinkedHashMap<>(edgeCount);
        for (int edgeIndex = 0; edgeIndex < orderedEdges.size(); edgeIndex++) {
            TopologyIndexLayout.OrderedEdge orderedEdge = orderedEdges.get(edgeIndex);
            TopologyModelSource.EdgeDefinition edge = orderedEdge.edge();
            edgeTarget[edgeIndex] = orderedEdge.destinationNodeIndex();
            edgeOrigin[edgeIndex] = orderedEdge.originNodeIndex();
            baseWeights[edgeIndex] = edge.getBaseWeight();
            edgeProfileIds[edgeIndex] = edge.getProfileId();
            edgeIndexById.put(edge.getEdgeId(), edgeIndex);
        }

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int edgeProfileVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);

        int coordinatesVec = 0;
        if (nonNullSource.hasCoordinates()) {
            GraphTopology.startCoordinatesVector(builder, nodeCount);
            for (int nodeIndex = nodeCount - 1; nodeIndex >= 0; nodeIndex--) {
                TopologyModelSource.NodeDefinition node = nodes.get(nodeIndex);
                org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                        builder,
                        node.getX(),
                        node.getY()
                );
            }
            coordinatesVec = builder.endVector();
        }

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeCount);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileVec);
        if (coordinatesVec != 0) {
            GraphTopology.addCoordinates(builder, coordinatesVec);
        }
        int topologyOffset = GraphTopology.endGraphTopology(builder);

        int profilesOffset = buildProfiles(builder, nonNullSource.getProfiles());
        int turnCostsOffset = buildTurnCosts(builder, nonNullSource.getTurnCosts(), edgeIndexById);
        int spatialIndexOffset = buildSpatialIndex(builder, nodes, nonNullSource.hasCoordinates());
        int idMappingOffset = buildIdMapping(builder, nodes);
        int metadataOffset = buildMetadata(builder, nonNullSource);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataOffset);
        Model.addTopology(builder, topologyOffset);
        if (profilesOffset != 0) {
            Model.addProfiles(builder, profilesOffset);
        }
        if (turnCostsOffset != 0) {
            Model.addTurnCosts(builder, turnCostsOffset);
        }
        if (spatialIndexOffset != 0) {
            Model.addSpatialIndex(builder, spatialIndexOffset);
        }
        if (idMappingOffset != 0) {
            Model.addIdMapping(builder, idMappingOffset);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        Map<String, Integer> mapperData = new LinkedHashMap<>(nodeCount);
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            mapperData.put(nodes.get(nodeIndex).getNodeId(), nodeIndex);
        }
        IDMapper nodeIdMapper = new FastUtilIDMapper(mapperData);
        ByteBuffer buffer = ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        return new CompiledTopologyModel(buffer, nodeIdMapper, nonNullSource.hasCoordinates());
    }

    private static int buildProfiles(FlatBufferBuilder builder, List<TopologyModelSource.ProfileDefinition> profiles) {
        if (profiles.isEmpty()) {
            return 0;
        }
        int[] profileOffsets = new int[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            TopologyModelSource.ProfileDefinition profile = profiles.get(i);
            float[] buckets = new float[profile.getBuckets().size()];
            for (int bucketIndex = 0; bucketIndex < buckets.length; bucketIndex++) {
                buckets[bucketIndex] = profile.getBuckets().get(bucketIndex);
            }
            int bucketsOffset = TemporalProfile.createBucketsVector(builder, buckets);
            profileOffsets[i] = TemporalProfile.createTemporalProfile(
                    builder,
                    profile.getProfileId(),
                    profile.getDayMask(),
                    bucketsOffset,
                    profile.getMultiplier()
            );
        }
        return Model.createProfilesVector(builder, profileOffsets);
    }

    private static int buildTurnCosts(
            FlatBufferBuilder builder,
            List<TopologyModelSource.TurnCostDefinition> turnCosts,
            Map<String, Integer> edgeIndexById
    ) {
        if (turnCosts.isEmpty()) {
            return 0;
        }
        int[] turnOffsets = new int[turnCosts.size()];
        for (int i = 0; i < turnCosts.size(); i++) {
            TopologyModelSource.TurnCostDefinition turnCost = turnCosts.get(i);
            turnOffsets[i] = TurnCost.createTurnCost(
                    builder,
                    edgeIndexById.get(turnCost.getFromEdgeId()),
                    edgeIndexById.get(turnCost.getToEdgeId()),
                    turnCost.getPenaltySeconds()
            );
        }
        return Model.createTurnCostsVector(builder, turnOffsets);
    }

    private static int buildSpatialIndex(
            FlatBufferBuilder builder,
            List<TopologyModelSource.NodeDefinition> nodes,
            boolean coordinatesEnabled
    ) {
        if (!coordinatesEnabled) {
            return 0;
        }
        BuiltKDIndex kdIndex = buildBalancedKdIndex(nodes, 16);
        SpatialIndex.startTreeNodesVector(builder, kdIndex.treeNodes().length);
        for (int i = kdIndex.treeNodes().length - 1; i >= 0; i--) {
            KDNodeSpec node = kdIndex.treeNodes()[i];
            KDNode.createKDNode(
                    builder,
                    node.splitValue(),
                    node.leftChild(),
                    node.rightChild(),
                    node.itemStartIndex(),
                    node.itemCount(),
                    node.splitAxis(),
                    node.isLeaf()
            );
        }
        int treeNodes = builder.endVector();
        int leafItemsVec = SpatialIndex.createLeafItemsVector(builder, kdIndex.leafItems());
        SpatialIndex.startSpatialIndex(builder);
        SpatialIndex.addTreeNodes(builder, treeNodes);
        SpatialIndex.addLeafItems(builder, leafItemsVec);
        SpatialIndex.addRootIndex(builder, kdIndex.rootIndex());
        return SpatialIndex.endSpatialIndex(builder);
    }

    private static int buildIdMapping(FlatBufferBuilder builder, List<TopologyModelSource.NodeDefinition> nodes) {
        long[] externalIds = new long[nodes.size()];
        int[] externalStringOffsets = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            externalIds[i] = i;
            externalStringOffsets[i] = builder.createString(nodes.get(i).getNodeId());
        }
        int externalIdsVec = IdMapping.createExternalIdsVector(builder, externalIds);
        int externalStringsVec = IdMapping.createExternalStringIdsVector(builder, externalStringOffsets);
        IdMapping.startIdMapping(builder);
        IdMapping.addExternalIds(builder, externalIdsVec);
        IdMapping.addExternalStringIds(builder, externalStringsVec);
        return IdMapping.endIdMapping(builder);
    }

    private static int buildMetadata(FlatBufferBuilder builder, TopologyModelSource source) {
        int modelVersion = builder.createString(source.getModelVersion());
        int timezone = builder.createString(source.getProfileTimezone());
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, toFlatBufferTimeUnit(source.getEngineTimeUnit()));
        Metadata.addTickDurationNs(builder, source.getEngineTimeUnit().tickDurationNs());
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }

    private static int toFlatBufferTimeUnit(TimeUtils.EngineTimeUnit unit) {
        return switch (unit) {
            case SECONDS -> TimeUnit.SECONDS;
            case MILLISECONDS -> TimeUnit.MILLISECONDS;
        };
    }

    private static BuiltKDIndex buildBalancedKdIndex(List<TopologyModelSource.NodeDefinition> nodes, int leafSize) {
        int[] nodeIds = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            nodeIds[i] = i;
        }
        KDBuilder builder = new KDBuilder(nodes, leafSize);
        int rootIndex = builder.build(nodeIds, 0);
        return new BuiltKDIndex(
                builder.kdNodes.toArray(new KDNodeSpec[0]),
                builder.toLeafItemsArray(),
                rootIndex
        );
    }

    private record BuiltKDIndex(KDNodeSpec[] treeNodes, int[] leafItems, int rootIndex) {
    }

    private record KDNodeSpec(
            float splitValue,
            int leftChild,
            int rightChild,
            int itemStartIndex,
            int itemCount,
            int splitAxis,
            int isLeaf
    ) {
    }

    private static final class KDBuilder {
        private final List<TopologyModelSource.NodeDefinition> nodeDefinitions;
        private final int leafSize;
        private final List<KDNodeSpec> kdNodes = new ArrayList<>();
        private final List<Integer> leafItems = new ArrayList<>();

        private KDBuilder(List<TopologyModelSource.NodeDefinition> nodeDefinitions, int leafSize) {
            this.nodeDefinitions = nodeDefinitions;
            this.leafSize = leafSize;
        }

        private int build(int[] pointIds, int depth) {
            if (pointIds.length <= leafSize) {
                int start = leafItems.size();
                for (int pointId : pointIds) {
                    leafItems.add(pointId);
                }
                int leafNodeIndex = kdNodes.size();
                kdNodes.add(new KDNodeSpec(
                        0.0f,
                        -1,
                        -1,
                        start,
                        pointIds.length,
                        depth & 1,
                        1
                ));
                return leafNodeIndex;
            }

            int axis = depth & 1;
            Integer[] sorted = new Integer[pointIds.length];
            for (int i = 0; i < pointIds.length; i++) {
                sorted[i] = pointIds[i];
            }
            Arrays.sort(sorted, Comparator
                    .comparingDouble((Integer nodeId) -> coordinate(nodeId, axis))
                    .thenComparingInt(Integer::intValue));

            int mid = sorted.length >>> 1;
            float splitValue = (float) coordinate(sorted[mid], axis);
            int[] leftIds = new int[mid];
            int[] rightIds = new int[sorted.length - mid];
            for (int i = 0; i < mid; i++) {
                leftIds[i] = sorted[i];
            }
            for (int i = mid; i < sorted.length; i++) {
                rightIds[i - mid] = sorted[i];
            }

            int nodeIndex = kdNodes.size();
            kdNodes.add(new KDNodeSpec(splitValue, -1, -1, 0, 0, axis, 0));
            int leftChild = build(leftIds, depth + 1);
            int rightChild = build(rightIds, depth + 1);
            kdNodes.set(nodeIndex, new KDNodeSpec(splitValue, leftChild, rightChild, 0, 0, axis, 0));
            return nodeIndex;
        }

        private double coordinate(int nodeId, int axis) {
            TopologyModelSource.NodeDefinition node = nodeDefinitions.get(nodeId);
            return axis == 0 ? node.getX() : node.getY();
        }

        private int[] toLeafItemsArray() {
            int[] result = new int[leafItems.size()];
            for (int i = 0; i < leafItems.size(); i++) {
                result[i] = leafItems.get(i);
            }
            return result;
        }
    }
}
