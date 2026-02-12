package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;

/**
 * Internal planner abstraction for Stage 12 route orchestration.
 */
interface RoutePlanner {
    /**
     * Computes one point-to-point internal route plan.
     *
     * @param edgeGraph graph backing the search.
     * @param costEngine time-dependent cost model.
     * @param heuristic goal-bound heuristic bound to request goal.
     * @param request normalized request in internal node-id space.
     * @return computed internal route plan.
     */
    InternalRoutePlan compute(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            GoalBoundHeuristic heuristic,
            InternalRouteRequest request
    );
}
