package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 11 spherical heuristic provider based on great-circle distance.
 */
public final class SphericalHeuristicProvider implements HeuristicProvider {
    private final EdgeGraph edgeGraph;
    private final double lowerBoundCostPerDistance;

    public SphericalHeuristicProvider(EdgeGraph edgeGraph, GeometryLowerBoundModel lowerBoundModel) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(lowerBoundModel, "lowerBoundModel");
        this.lowerBoundCostPerDistance = lowerBoundModel.lowerBoundCostPerDistance();
    }

    @Override
    public HeuristicType type() {
        return HeuristicType.SPHERICAL;
    }

    @Override
    public GoalBoundHeuristic bindGoal(int goalNodeId) {
        validateGoalNodeId(goalNodeId);
        return new BoundSphericalHeuristic(
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

    private static final class BoundSphericalHeuristic implements GoalBoundHeuristic {
        private final EdgeGraph edgeGraph;
        private final int nodeCount;
        private final double goalLatDeg;
        private final double goalLonDeg;
        private final double lowerBoundCostPerDistance;

        private BoundSphericalHeuristic(
                EdgeGraph edgeGraph,
                double goalLatDeg,
                double goalLonDeg,
                double lowerBoundCostPerDistance
        ) {
            this.edgeGraph = edgeGraph;
            this.nodeCount = edgeGraph.nodeCount();
            this.goalLatDeg = goalLatDeg;
            this.goalLonDeg = goalLonDeg;
            this.lowerBoundCostPerDistance = lowerBoundCostPerDistance;
        }

        @Override
        public double estimateFromNode(int nodeId) {
            if (nodeId < 0 || nodeId >= nodeCount) {
                throw new IllegalArgumentException(
                        "nodeId out of bounds: " + nodeId + " [0, " + nodeCount + ")"
                );
            }
            double nodeLatDeg = edgeGraph.getNodeX(nodeId);
            double nodeLonDeg = edgeGraph.getNodeY(nodeId);
            double distanceMeters = GeometryDistance.greatCircleDistanceMeters(
                    nodeLatDeg,
                    nodeLonDeg,
                    goalLatDeg,
                    goalLonDeg
            );
            return distanceMeters * lowerBoundCostPerDistance;
        }
    }
}
