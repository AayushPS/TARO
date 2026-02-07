package org.Aayush.routing.spatial;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Stage 8: Spatial Runtime (KD query engine over serialized spatial index).
 * <p>
 * This class is immutable after construction and safe for concurrent reads.
 * It supports nearest-node lookup on top of a serialized KD tree.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpatialRuntime {

    private static final int FILE_IDENTIFIER = 0x4F524154; // "TARO"

    private final EdgeGraph graph;
    @Getter
    @Accessors(fluent = true)
    private final boolean enabled;

    private final int rootIndex;
    private final float[] splitValues;
    private final int[] leftChildren;
    private final int[] rightChildren;
    private final int[] itemStartIndices;
    private final int[] itemCounts;
    private final byte[] splitAxes;
    private final byte[] leafFlags;
    private final int[] leafItems;

    /**
     * Constructs an enabled runtime from the model's {@code spatial_index}.
     */
    public static SpatialRuntime fromFlatBuffer(ByteBuffer buffer, EdgeGraph graph) {
        return fromFlatBuffer(buffer, graph, true);
    }

    /**
     * Constructs runtime from model data with explicit trait-driven enablement.
     *
     * @param buffer byte buffer containing the TARO model.
     * @param graph loaded EdgeGraph used for node coordinate reads.
     * @param spatialEnabled if false, runtime is created disabled and does not require a spatial index.
     */
    public static SpatialRuntime fromFlatBuffer(ByteBuffer buffer, EdgeGraph graph, boolean spatialEnabled) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        Objects.requireNonNull(graph, "graph");

        ByteBuffer bb = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (bb.remaining() < 8) {
            throw new IllegalArgumentException("Buffer too small for .taro file header");
        }
        if (!Model.ModelBufferHasIdentifier(bb)) {
            int ident = bb.getInt(4);
            throw new IllegalArgumentException(String.format(
                    "Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X",
                    FILE_IDENTIFIER, ident));
        }

        if (!spatialEnabled) {
            return disabled(graph);
        }

        if (!graph.hasCoordinates()) {
            throw new IllegalArgumentException(
                    "Spatial runtime requires coordinates, but graph has none");
        }

        Model model = Model.getRootAsModel(bb);
        SpatialIndex spatialIndex = model.spatialIndex();
        if (spatialIndex == null) {
            throw new IllegalArgumentException("spatial_index missing while spatial runtime is enabled");
        }

        int treeNodeCount = spatialIndex.treeNodesLength();
        if (treeNodeCount <= 0) {
            throw new IllegalArgumentException("spatial_index.tree_nodes must be non-empty");
        }

        int leafItemCount = spatialIndex.leafItemsLength();
        if (leafItemCount <= 0) {
            throw new IllegalArgumentException("spatial_index.leaf_items must be non-empty");
        }

        int rootIndex = spatialIndex.rootIndex();
        if (rootIndex < 0 || rootIndex >= treeNodeCount) {
            throw new IllegalArgumentException(
                    "spatial_index.root_index out of bounds: " + rootIndex +
                            " [0, " + treeNodeCount + ")");
        }

        float[] splitValues = new float[treeNodeCount];
        int[] leftChildren = new int[treeNodeCount];
        int[] rightChildren = new int[treeNodeCount];
        int[] itemStartIndices = new int[treeNodeCount];
        int[] itemCounts = new int[treeNodeCount];
        byte[] splitAxes = new byte[treeNodeCount];
        byte[] leafFlags = new byte[treeNodeCount];

        KDNode cursor = new KDNode();
        for (int i = 0; i < treeNodeCount; i++) {
            KDNode node = spatialIndex.treeNodes(cursor, i);
            if (node == null) {
                throw new IllegalArgumentException("spatial_index.tree_nodes[" + i + "] is null");
            }

            float splitValue = node.splitValue();
            if (!Float.isFinite(splitValue)) {
                throw new IllegalArgumentException("tree_nodes[" + i + "].split_value must be finite");
            }

            int leftChild = node.leftChild();
            int rightChild = node.rightChild();
            int itemStart = node.itemStartIndex();
            int itemCount = node.itemCount();
            int splitAxis = node.splitAxis();
            int isLeaf = node.isLeaf();

            validateNode(
                    i,
                    treeNodeCount,
                    leafItemCount,
                    splitAxis,
                    isLeaf,
                    leftChild,
                    rightChild,
                    itemStart,
                    itemCount
            );

            splitValues[i] = splitValue;
            leftChildren[i] = leftChild;
            rightChildren[i] = rightChild;
            itemStartIndices[i] = itemStart;
            itemCounts[i] = itemCount;
            splitAxes[i] = (byte) splitAxis;
            leafFlags[i] = (byte) isLeaf;
        }

        int[] leafItems = new int[leafItemCount];
        int nodeCount = graph.nodeCount();
        for (int i = 0; i < leafItemCount; i++) {
            int nodeId = spatialIndex.leafItems(i);
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "leaf_items[" + i + "] out of bounds: " + nodeId +
                                " [0, " + nodeCount + ")");
            }
            leafItems[i] = nodeId;
        }

        validateReachableTree(
                rootIndex,
                leftChildren,
                rightChildren,
                leafFlags,
                itemCounts
        );

        return new SpatialRuntime(
                graph,
                true,
                rootIndex,
                splitValues,
                leftChildren,
                rightChildren,
                itemStartIndices,
                itemCounts,
                splitAxes,
                leafFlags,
                leafItems
        );
    }

    /**
     * Number of tree nodes in the loaded KD index.
     */
    public int treeNodeCount() {
        return splitValues.length;
    }

    /**
     * Number of payload node references in leaf-items vector.
     */
    public int leafItemCount() {
        return leafItems.length;
    }

    /**
     * Finds nearest graph node to query coordinates.
     *
     * @return nearest match including coordinates and squared distance.
     */
    public SpatialMatch nearest(double queryX, double queryY) {
        int nodeId = nearestNodeId(queryX, queryY);
        double nodeX = graph.getNodeX(nodeId);
        double nodeY = graph.getNodeY(nodeId);
        double dx = nodeX - queryX;
        double dy = nodeY - queryY;
        return new SpatialMatch(nodeId, nodeX, nodeY, dx * dx + dy * dy);
    }

    /**
     * Finds nearest graph node id to query coordinates.
     * Tie-break is deterministic: lower node id wins when distances are equal.
     */
    public int nearestNodeId(double queryX, double queryY) {
        ensureEnabled();
        validateQueryCoordinate(queryX, "queryX");
        validateQueryCoordinate(queryY, "queryY");

        int bestNode = -1;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;

        int[] stack = new int[Math.max(4, Math.min(64, splitValues.length))];
        int top = 0;
        stack[top++] = rootIndex;

        while (top > 0) {
            int nodeIndex = stack[--top];

            if (leafFlags[nodeIndex] != 0) {
                int start = itemStartIndices[nodeIndex];
                int count = itemCounts[nodeIndex];
                int end = start + count;

                for (int i = start; i < end; i++) {
                    int candidateNode = leafItems[i];
                    double dx = graph.getNodeX(candidateNode) - queryX;
                    double dy = graph.getNodeY(candidateNode) - queryY;
                    double distanceSquared = dx * dx + dy * dy;

                    if (distanceSquared < bestDistanceSquared
                            || (distanceSquared == bestDistanceSquared
                            && (bestNode < 0 || candidateNode < bestNode))) {
                        bestDistanceSquared = distanceSquared;
                        bestNode = candidateNode;
                    }
                }
                continue;
            }

            int axis = splitAxes[nodeIndex];
            double queryCoordinate = axis == 0 ? queryX : queryY;
            double delta = queryCoordinate - splitValues[nodeIndex];
            double splitPlaneDistanceSquared = delta * delta;

            int nearChild = delta <= 0.0 ? leftChildren[nodeIndex] : rightChildren[nodeIndex];
            int farChild = delta <= 0.0 ? rightChildren[nodeIndex] : leftChildren[nodeIndex];

            if (farChild >= 0 && splitPlaneDistanceSquared <= bestDistanceSquared) {
                if (top == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[top++] = farChild;
            }

            if (nearChild >= 0) {
                if (top == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[top++] = nearChild;
            }
        }

        if (bestNode < 0) {
            throw new IllegalStateException("Spatial index contains no reachable leaf payload items");
        }
        return bestNode;
    }

    @Override
    public String toString() {
        return "SpatialRuntime[enabled=" + enabled +
                ", treeNodes=" + splitValues.length +
                ", leafItems=" + leafItems.length + "]";
    }

    private static SpatialRuntime disabled(EdgeGraph graph) {
        return new SpatialRuntime(
                graph,
                false,
                -1,
                new float[0],
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                new byte[0],
                new byte[0],
                new int[0]
        );
    }

    private static void validateNode(
            int nodeIndex,
            int treeNodeCount,
            int leafItemCount,
            int splitAxis,
            int isLeaf,
            int leftChild,
            int rightChild,
            int itemStart,
            int itemCount
    ) {
        if (splitAxis != 0 && splitAxis != 1) {
            throw new IllegalArgumentException(
                    "tree_nodes[" + nodeIndex + "].split_axis must be 0 or 1, got " + splitAxis);
        }

        if (isLeaf != 0 && isLeaf != 1) {
            throw new IllegalArgumentException(
                    "tree_nodes[" + nodeIndex + "].is_leaf must be 0 or 1, got " + isLeaf);
        }

        if (isLeaf == 1) {
            if (leftChild != -1 || rightChild != -1) {
                throw new IllegalArgumentException(
                        "tree_nodes[" + nodeIndex + "] leaf must have left/right child = -1");
            }
            if (itemStart < 0) {
                throw new IllegalArgumentException(
                        "tree_nodes[" + nodeIndex + "].item_start_index must be >= 0");
            }
            long end = (long) itemStart + itemCount;
            if (end > leafItemCount) {
                throw new IllegalArgumentException(
                        "tree_nodes[" + nodeIndex + "] leaf item span exceeds leaf_items length");
            }
            return;
        }

        if (leftChild == -1 && rightChild == -1) {
            throw new IllegalArgumentException(
                    "tree_nodes[" + nodeIndex + "] internal node must have at least one child");
        }
        validateChildIndex(nodeIndex, "left_child", leftChild, treeNodeCount);
        validateChildIndex(nodeIndex, "right_child", rightChild, treeNodeCount);
    }

    private static void validateChildIndex(int nodeIndex, String field, int childIndex, int treeNodeCount) {
        if (childIndex != -1 && (childIndex < 0 || childIndex >= treeNodeCount)) {
            throw new IllegalArgumentException(
                    "tree_nodes[" + nodeIndex + "]." + field + " out of bounds: " + childIndex +
                            " [0, " + treeNodeCount + ") or -1");
        }
    }

    private static void validateReachableTree(
            int rootIndex,
            int[] leftChildren,
            int[] rightChildren,
            byte[] leafFlags,
            int[] itemCounts
    ) {
        int treeNodeCount = leafFlags.length;
        byte[] state = new byte[treeNodeCount]; // 0=unseen, 1=seen
        int[] stack = new int[treeNodeCount];
        int top = 0;
        int reachableLeafPayload = 0;

        stack[top++] = rootIndex;
        state[rootIndex] = 1;

        while (top > 0) {
            int nodeIndex = stack[--top];

            if (leafFlags[nodeIndex] != 0) {
                reachableLeafPayload += itemCounts[nodeIndex];
                continue;
            }

            int left = leftChildren[nodeIndex];
            int right = rightChildren[nodeIndex];

            if (left >= 0) {
                if (state[left] != 0) {
                    throw new IllegalArgumentException(
                            "spatial_index.tree_nodes is not a valid tree (cycle/shared child at " + left + ")");
                }
                state[left] = 1;
                stack[top++] = left;
            }

            if (right >= 0) {
                if (state[right] != 0) {
                    throw new IllegalArgumentException(
                            "spatial_index.tree_nodes is not a valid tree (cycle/shared child at " + right + ")");
                }
                state[right] = 1;
                stack[top++] = right;
            }
        }

        if (reachableLeafPayload <= 0) {
            throw new IllegalArgumentException("spatial_index tree has no reachable leaf payload items");
        }
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new UnsupportedOperationException(
                    "Spatial runtime is disabled. Enable spatial addressing to query nearest nodes.");
        }
    }

    private static void validateQueryCoordinate(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
