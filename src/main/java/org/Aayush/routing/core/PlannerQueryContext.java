package org.Aayush.routing.core;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.PriorityQueue;

/**
 * Thread-confined reusable query buffers for Stage 13 planner internals.
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
     * Resets all per-query mutable structures.
     */
    void reset() {
        labelStore.clear();

        for (int i = 0; i < touchedEdges.size(); i++) {
            int edgeId = touchedEdges.getInt(i);
            IntArrayList labels = activeLabelsByEdge.get(edgeId);
            if (labels != null) {
                labels.clear();
            }
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

    float reverseBest(int nodeId) {
        return reverseBestByNode.get(nodeId);
    }

    void setReverseBest(int nodeId, float distance) {
        reverseBestByNode.put(nodeId, distance);
    }

    boolean isReverseSettled(int nodeId) {
        return reverseSettledByNode.contains(nodeId);
    }

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
