package org.Aayush.routing.heuristic;

import org.Aayush.routing.graph.EdgeGraph;

import java.util.Objects;

/**
 * Stage 11 spherical heuristic provider based on great-circle distance.
 *
 * <p>Assumes graph coordinates are geodetic latitude/longitude degrees and
 * scales great-circle distance by an admissible lower-bound cost factor.</p>
 */
public final class SphericalHeuristicProvider implements HeuristicProvider {
    private final EdgeGraph edgeGraph;
    private final double lowerBoundCostPerDistance;

    /**
     * Creates a spherical heuristic provider.
     *
     * @param edgeGraph graph runtime with geodetic coordinate support.
     * @param lowerBoundModel calibrated admissibility model.
     */
    public SphericalHeuristicProvider(EdgeGraph edgeGraph, GeometryLowerBoundModel lowerBoundModel) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(lowerBoundModel, "lowerBoundModel");
        this.lowerBoundCostPerDistance = lowerBoundModel.lowerBoundCostPerDistance();
    }

    /**
     * Returns provider type discriminator.
     */
    @Override
    public HeuristicType type() {
        return HeuristicType.SPHERICAL;
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
        return new BoundSphericalHeuristic(
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

        /**
         * Returns admissible great-circle estimate from node to bound goal.
         */
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
