package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 11 null heuristic provider.
 */
public final class NullHeuristicProvider implements HeuristicProvider {
    private final int nodeCount;
    private final GoalBoundHeuristic zeroEstimator;

    public NullHeuristicProvider(EdgeGraph edgeGraph) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.nodeCount = edgeGraph.nodeCount();
        this.zeroEstimator = new BoundNullHeuristic(nodeCount);
    }

    @Override
    public HeuristicType type() {
        return HeuristicType.NONE;
    }

    @Override
    public GoalBoundHeuristic bindGoal(int goalNodeId) {
        validateGoalNodeId(goalNodeId);
        return zeroEstimator;
    }

    private void validateGoalNodeId(int goalNodeId) {
        if (goalNodeId < 0 || goalNodeId >= nodeCount) {
            throw new IllegalArgumentException(
                    "goalNodeId out of bounds: " + goalNodeId + " [0, " + nodeCount + ")"
            );
        }
    }

    private static final class BoundNullHeuristic implements GoalBoundHeuristic {
        private final int nodeCount;

        private BoundNullHeuristic(int nodeCount) {
            this.nodeCount = nodeCount;
        }

        @Override
        public double estimateFromNode(int nodeId) {
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "nodeId out of bounds: " + nodeId + " [0, " + nodeCount + ")"
                );
            }
            return 0.0d;
        }
    }
}
