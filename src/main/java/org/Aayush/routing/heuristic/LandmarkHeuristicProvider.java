package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 12 ALT heuristic provider backed by precomputed landmark arrays.
 */
public final class LandmarkHeuristicProvider implements HeuristicProvider {
    private final int nodeCount;
    private final LandmarkStore landmarkStore;

    /**
     * Creates a landmark heuristic provider.
     *
     * @param edgeGraph graph runtime used for node-count contract checks.
     * @param landmarkStore precomputed landmark distance store.
     */
    public LandmarkHeuristicProvider(EdgeGraph edgeGraph, LandmarkStore landmarkStore) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.landmarkStore = Objects.requireNonNull(landmarkStore, "landmarkStore");
        this.nodeCount = edgeGraph.nodeCount();
        if (landmarkStore.getNodeCount() != nodeCount) {
            throw new IllegalArgumentException(
                    "landmarkStore node count mismatch: " + landmarkStore.getNodeCount() + " != " + nodeCount
            );
        }
        if (landmarkStore.landmarkCount() <= 0) {
            throw new IllegalArgumentException("landmarkStore must contain at least one landmark");
        }
    }

    @Override
    public HeuristicType type() {
        return HeuristicType.LANDMARK;
    }

    /**
     * Binds this provider to one target node and returns a reusable estimator.
     *
     * @param goalNodeId target node id in internal graph space.
     * @return goal-bound heuristic estimator.
     */
    @Override
    public GoalBoundHeuristic bindGoal(int goalNodeId) {
        validateNodeId(goalNodeId, "goalNodeId");
        return new BoundLandmarkHeuristic(landmarkStore, nodeCount, goalNodeId);
    }

    private void validateNodeId(int nodeId, String fieldName) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            throw new IllegalArgumentException(
                    fieldName + " out of bounds: " + nodeId + " [0, " + nodeCount + ")"
            );
        }
    }

    private static final class BoundLandmarkHeuristic implements GoalBoundHeuristic {
        private final LandmarkStore store;
        private final int nodeCount;
        private final int goalNodeId;

        private BoundLandmarkHeuristic(LandmarkStore store, int nodeCount, int goalNodeId) {
            this.store = store;
            this.nodeCount = nodeCount;
            this.goalNodeId = goalNodeId;
        }

        @Override
        public double estimateFromNode(int nodeId) {
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "nodeId out of bounds: " + nodeId + " [0, " + nodeCount + ")"
                );
            }
            return store.lowerBound(nodeId, goalNodeId);
        }
    }
}
