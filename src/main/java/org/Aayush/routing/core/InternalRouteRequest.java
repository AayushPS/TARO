package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Internal normalized route request (node ids only).
 *
 * @param sourceNodeId internal source node id.
 * @param targetNodeId internal target node id.
 * @param departureTicks departure time in engine ticks.
 * @param algorithm selected routing algorithm.
 * @param heuristicType selected heuristic type.
 */
record InternalRouteRequest(
        int sourceNodeId,
        int targetNodeId,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType
) {
}
