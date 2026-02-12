package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 11 Euclidean heuristic provider.
 *
 * <p>Uses straight-line (L2) distance in graph coordinate space and scales it
 * by the calibrated admissible lower-bound cost-per-distance factor.</p>
 */
public final class EuclideanHeuristicProvider implements HeuristicProvider {
    private final EdgeGraph edgeGraph;
    private final double lowerBoundCostPerDistance;

    /**
     * Creates an Euclidean heuristic provider.
     *
     * @param edgeGraph graph runtime with coordinate support.
     * @param lowerBoundModel calibrated admissibility model.
     */
    public EuclideanHeuristicProvider(EdgeGraph edgeGraph, GeometryLowerBoundModel lowerBoundModel) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(lowerBoundModel, "lowerBoundModel");
        this.lowerBoundCostPerDistance = lowerBoundModel.lowerBoundCostPerDistance();
    }

    /**
     * Returns provider type discriminator.
     */
    @Override
    public HeuristicType type() {
        return HeuristicType.EUCLIDEAN;
    }

    /**
     * Binds this provider to one target node and returns a reusable estimator.
     *
     * @param goalNodeId target node id in internal graph space.
     * @return goal-bound heuristic estimator.
     */
    @Override
    public GoalBoundHeuristic bindGoal(int goalNodeId) {
        validateGoalNodeId(goalNodeId);
        return new BoundEuclideanHeuristic(
                edgeGraph,
                edgeGraph.getNodeX(goalNodeId),
                edgeGraph.getNodeY(goalNodeId),
                lowerBoundCostPerDistance
        );
    }

    /**
     * Validates goal node id against graph bounds.
     */
    private void validateGoalNodeId(int goalNodeId) {
        if (goalNodeId < 0 || goalNodeId >= edgeGraph.nodeCount()) {
            throw new IllegalArgumentException(
                    "goalNodeId out of bounds: " + goalNodeId + " [0, " + edgeGraph.nodeCount() + ")"
            );
        }
    }

    private static final class BoundEuclideanHeuristic implements GoalBoundHeuristic {
        private final EdgeGraph edgeGraph;
        private final int nodeCount;
        private final double goalX;
        private final double goalY;
        private final double lowerBoundCostPerDistance;

        private BoundEuclideanHeuristic(
                EdgeGraph edgeGraph,
                double goalX,
                double goalY,
                double lowerBoundCostPerDistance
        ) {
            this.edgeGraph = edgeGraph;
            this.nodeCount = edgeGraph.nodeCount();
            this.goalX = goalX;
            this.goalY = goalY;
            this.lowerBoundCostPerDistance = lowerBoundCostPerDistance;
        }

        /**
         * Returns admissible Euclidean estimate from node to bound goal.
         */
        @Override
        public double estimateFromNode(int nodeId) {
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "nodeId out of bounds: " + nodeId + " [0, " + nodeCount + ")"
                );
            }
            double nodeX = edgeGraph.getNodeX(nodeId);
            double nodeY = edgeGraph.getNodeY(nodeId);
            double distance = GeometryDistance.euclideanDistance(nodeX, nodeY, goalX, goalY);
            double estimate = distance * lowerBoundCostPerDistance;
            if (!Double.isFinite(estimate) || estimate < 0.0d) {
                // Preserve admissibility under extreme numeric ranges.
                return 0.0d;
            }
            return estimate;
        }
    }
}
