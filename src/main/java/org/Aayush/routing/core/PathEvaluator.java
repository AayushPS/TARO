package org.Aayush.routing.core;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;

/**
 * Replays edge paths with exact forward cost semantics.
 */
final class PathEvaluator {
    static final String REASON_NON_FINITE_EDGE_COST = "H13_PATH_NON_FINITE_EDGE_COST";
    static final String REASON_NON_FINITE_PATH_COST = "H13_PATH_NON_FINITE_PATH_COST";
    static final String REASON_NODE_PATH_RECONSTRUCTION = "H13_PATH_NODE_RECONSTRUCTION_FAILED";

    /**
     * Exact edge-path replay result.
     */
    record Evaluation(float totalCost, long arrivalTicks) {
    }

    /**
     * Replays one edge path through the configured cost engine.
     */
    Evaluation evaluateEdgePath(CostEngine costEngine, int[] edgePath, long departureTicks) {
        float totalCost = 0.0f;
        long arrivalTicks = departureTicks;
        int predecessorEdgeId = CostEngine.NO_PREDECESSOR;

        for (int edgeId : edgePath) {
            float transitionCost = costEngine.computeEdgeCost(edgeId, predecessorEdgeId, arrivalTicks);
            if (!Float.isFinite(transitionCost)) {
                throw new PathEvaluationException(
                        REASON_NON_FINITE_EDGE_COST,
                        "non-finite transition cost on edge " + edgeId
                );
            }

            float nextCost = totalCost + transitionCost;
            if (!Float.isFinite(nextCost)) {
                throw new PathEvaluationException(
                        REASON_NON_FINITE_PATH_COST,
                        "non-finite cumulative path cost"
                );
            }
            totalCost = nextCost;
            arrivalTicks = saturatingAdd(arrivalTicks, toArrivalTicks(transitionCost));
            predecessorEdgeId = edgeId;
        }
        return new Evaluation(totalCost, arrivalTicks);
    }

    /**
     * Converts one edge-id path to node-id path with fixed source anchor.
     */
    int[] toNodePath(EdgeGraph edgeGraph, int sourceNodeId, int[] edgePath) {
        int[] nodePath = new int[edgePath.length + 1];
        nodePath[0] = sourceNodeId;
        for (int i = 0; i < edgePath.length; i++) {
            int edgeId = edgePath[i];
            int destination = edgeGraph.getEdgeDestination(edgeId);
            int previousNode = nodePath[i];
            int expectedOrigin = edgeGraph.getEdgeOrigin(edgeId);
            if (expectedOrigin != previousNode) {
                throw new PathEvaluationException(
                        REASON_NODE_PATH_RECONSTRUCTION,
                        "edge " + edgeId + " origin " + expectedOrigin + " does not match node " + previousNode
                );
            }
            nodePath[i + 1] = destination;
        }
        return nodePath;
    }

    private static long toArrivalTicks(float transitionCost) {
        if (!Float.isFinite(transitionCost) || transitionCost <= 0.0f) {
            return 0L;
        }
        double ceil = Math.ceil(transitionCost);
        if (ceil >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) ceil;
    }

    private static long saturatingAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    /**
     * Deterministic path replay exception with reason code.
     */
    @Getter
    @Accessors(fluent = true)
    static final class PathEvaluationException extends RuntimeException {
        private final String reasonCode;

        PathEvaluationException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
