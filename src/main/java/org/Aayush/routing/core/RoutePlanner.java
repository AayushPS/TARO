package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;

/**
 * Internal planner abstraction for Stage 12 route orchestration.
 */
interface RoutePlanner {
    InternalRoutePlan compute(
            EdgeGraph edgeGraph,
            CostEngine costEngine,
            GoalBoundHeuristic heuristic,
            InternalRouteRequest request
    );
}

