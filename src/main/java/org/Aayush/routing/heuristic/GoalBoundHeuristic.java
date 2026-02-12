package org.Aayush.routing.heuristic;

/**
 * Immutable goal-bound heuristic estimator.
 *
 * <p>Hot path contract: {@link #estimateFromNode(int)} must avoid allocations.</p>
 */
@FunctionalInterface
public interface GoalBoundHeuristic {

    /**
     * Estimates remaining cost from a node to a pre-bound goal.
     *
     * @param nodeId source node id.
     * @return admissible lower-bound estimate.
     */
    double estimateFromNode(int nodeId);
}
