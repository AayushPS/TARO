package org.Aayush.routing.heuristic;

/**
 * Stage 11 heuristic provider contract.
 *
 * <p>Providers are immutable and thread-safe. Binding returns an immutable goal-bound
 * estimator suitable for concurrent hot-path reads.</p>
 */
public interface HeuristicProvider {

    /**
     * @return heuristic mode of this provider.
     */
    HeuristicType type();

    /**
     * Binds a concrete goal node and returns a reusable estimator.
     *
     * @param goalNodeId internal goal node id.
     * @return immutable estimator bound to the provided goal node.
     */
    GoalBoundHeuristic bindGoal(int goalNodeId);
}
