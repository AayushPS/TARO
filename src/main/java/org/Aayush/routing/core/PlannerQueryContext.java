package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.PriorityQueue;

/**
 * Thread-confined mutable state for one bidirectional A* query.
 *
 * <p>Holds dominance labels, reverse-lane distance maps, and both frontiers. The
 * instance is reused per thread to reduce allocation pressure in hot paths.</p>
 */
final class PlannerQueryContext {
    private static final float INF = Float.POSITIVE_INFINITY;

    private final DominanceLabelStore labelStore = new DominanceLabelStore();
    private final IntArrayList touchedEdges = new IntArrayList();
    private final Int2ObjectOpenHashMap<IntArrayList> activeLabelsByEdge = new Int2ObjectOpenHashMap<>();
    private final Int2FloatOpenHashMap reverseBestByNode = new Int2FloatOpenHashMap();
    private final IntOpenHashSet reverseSettledByNode = new IntOpenHashSet();
    private final PriorityQueue<ForwardFrontierState> forwardFrontier = new PriorityQueue<>();
    private final PriorityQueue<BackwardFrontierState> backwardFrontier = new PriorityQueue<>();

    PlannerQueryContext() {
        reverseBestByNode.defaultReturnValue(INF);
    }

    /**
     * Resets all mutable structures for a new query.
     */
    void reset() {
        labelStore.clear();

        for (int i = 0; i < touchedEdges.size(); i++) {
            int edgeId = touchedEdges.getInt(i);
            activeLabelsByEdge.remove(edgeId);
        }
        touchedEdges.clear();

        reverseBestByNode.clear();
        reverseSettledByNode.clear();
        forwardFrontier.clear();
        backwardFrontier.clear();
    }

    DominanceLabelStore labelStore() {
        return labelStore;
    }

    /**
     * Returns active-label list for one edge, allocating on first touch.
     */
    IntArrayList activeLabelsForEdge(int edgeId) {
        IntArrayList labels = activeLabelsByEdge.get(edgeId);
        if (labels == null) {
            labels = new IntArrayList();
            activeLabelsByEdge.put(edgeId, labels);
        }
        if (labels.isEmpty()) {
            touchedEdges.add(edgeId);
        }
        return labels;
    }

    /**
     * Returns current best reverse-lane lower bound for a node.
     */
    float reverseBest(int nodeId) {
        return reverseBestByNode.get(nodeId);
    }

    /**
     * Publishes improved reverse-lane lower bound for a node.
     */
    void setReverseBest(int nodeId, float distance) {
        reverseBestByNode.put(nodeId, distance);
    }

    /**
     * Returns whether reverse lane settled this node.
     */
    boolean isReverseSettled(int nodeId) {
        return reverseSettledByNode.contains(nodeId);
    }

    /**
     * Marks node as settled in reverse lane.
     */
    void markReverseSettled(int nodeId) {
        reverseSettledByNode.add(nodeId);
    }

    PriorityQueue<ForwardFrontierState> forwardFrontier() {
        return forwardFrontier;
    }

    PriorityQueue<BackwardFrontierState> backwardFrontier() {
        return backwardFrontier;
    }
}
