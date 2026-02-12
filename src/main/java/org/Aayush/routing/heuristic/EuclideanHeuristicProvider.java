package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 11 Euclidean heuristic provider.
 */
public final class EuclideanHeuristicProvider implements HeuristicProvider {
    private final EdgeGraph edgeGraph;
    private final double lowerBoundCostPerDistance;

    public EuclideanHeuristicProvider(EdgeGraph edgeGraph, GeometryLowerBoundModel lowerBoundModel) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(lowerBoundModel, "lowerBoundModel");
        this.lowerBoundCostPerDistance = lowerBoundModel.lowerBoundCostPerDistance();
    }

    @Override
    public HeuristicType type() {
        return HeuristicType.EUCLIDEAN;
    }

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
