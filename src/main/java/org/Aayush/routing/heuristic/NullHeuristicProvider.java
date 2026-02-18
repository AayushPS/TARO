package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Null heuristic provider.
 *
 * <p>Always returns zero estimates and therefore behaves like plain Dijkstra
 * while still honoring bound-check contracts.</p>
 */
public final class NullHeuristicProvider implements HeuristicProvider {
    private final int nodeCount;
    private final GoalBoundHeuristic zeroEstimator;

    /**
     * Creates a null heuristic provider for one graph contract.
     *
     * @param edgeGraph graph runtime used for node bound validation.
     */
    public NullHeuristicProvider(EdgeGraph edgeGraph) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.nodeCount = edgeGraph.nodeCount();
        this.zeroEstimator = new BoundNullHeuristic(nodeCount);
    }

    /**
     * Returns provider type discriminator.
     */
    @Override
    public HeuristicType type() {
        return HeuristicType.NONE;
    }

    /**
     * Binds this provider to one goal node.
     *
     * @param goalNodeId target node id in internal graph space.
     * @return zero-cost goal-bound estimator.
     */
    @Override
    public GoalBoundHeuristic bindGoal(int goalNodeId) {
        validateGoalNodeId(goalNodeId);
        return zeroEstimator;
    }

    /**
     * Validates goal node id against graph bounds.
     */
    private void validateGoalNodeId(int goalNodeId) {
        if (goalNodeId < 0 || goalNodeId >= nodeCount) {
            throw new IllegalArgumentException(
                    "goalNodeId out of bounds: " + goalNodeId + " [0, " + nodeCount + ")"
            );
        }
    }

    private static final class BoundNullHeuristic implements GoalBoundHeuristic {
        private final int nodeCount;

        /**
         * Creates a zero heuristic estimator with bound checks.
         */
        private BoundNullHeuristic(int nodeCount) {
            this.nodeCount = nodeCount;
        }

        /**
         * Returns constant zero estimate for any valid node.
         */
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
