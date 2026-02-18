package org.Aayush.routing.core;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable reverse adjacency index for incoming-edge traversal by node.
 *
 * <p>Backed by CSR-style arrays where each node maps to a contiguous range inside
 * {@code incomingEdgeIds}. This enables allocation-free incoming-edge scans.</p>
 */
final class ReverseEdgeIndex {
    private final int nodeCount;
    private final int[] firstIncomingByNode;
    private final int[] incomingEdgeIds;

    private ReverseEdgeIndex(int nodeCount, int[] firstIncomingByNode, int[] incomingEdgeIds) {
        this.nodeCount = nodeCount;
        this.firstIncomingByNode = firstIncomingByNode;
        this.incomingEdgeIds = incomingEdgeIds;
    }

    /**
     * Builds reverse adjacency for one immutable edge graph instance.
     */
    static ReverseEdgeIndex build(EdgeGraph edgeGraph) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        int nodeCount = edgeGraph.nodeCount();
        int edgeCount = edgeGraph.edgeCount();

        int[] incomingDegree = new int[nodeCount];
        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            int destinationNode = edgeGraph.getEdgeDestination(edgeId);
            incomingDegree[destinationNode]++;
        }

        int[] firstIncoming = new int[nodeCount + 1];
        int cursor = 0;
        for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
            firstIncoming[nodeId] = cursor;
            cursor += incomingDegree[nodeId];
        }
        firstIncoming[nodeCount] = edgeCount;

        int[] fillCursor = Arrays.copyOf(firstIncoming, firstIncoming.length);
        int[] incomingEdges = new int[edgeCount];
        for (int edgeId = 0; edgeId < edgeCount; edgeId++) {
            int destinationNode = edgeGraph.getEdgeDestination(edgeId);
            int position = fillCursor[destinationNode]++;
            incomingEdges[position] = edgeId;
        }
        return new ReverseEdgeIndex(nodeCount, firstIncoming, incomingEdges);
    }

    /**
     * Returns start index (inclusive) in incoming-edge array for one node.
     */
    int incomingStart(int nodeId) {
        validateNode(nodeId);
        return firstIncomingByNode[nodeId];
    }

    /**
     * Returns end index (exclusive) in incoming-edge array for one node.
     */
    int incomingEnd(int nodeId) {
        validateNode(nodeId);
        return firstIncomingByNode[nodeId + 1];
    }

    /**
     * Returns incoming edge id at one reverse-array position.
     */
    int incomingEdgeIdAt(int reverseIndexPosition) {
        return incomingEdgeIds[reverseIndexPosition];
    }

    /**
     * Returns node count bound used by this reverse index.
     */
    int nodeCount() {
        return nodeCount;
    }

    private void validateNode(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IndexOutOfBoundsException("nodeId out of bounds: " + nodeId);
        }
    }
}
