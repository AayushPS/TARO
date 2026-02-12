package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Internal normalized route request (node ids only).
 */
record InternalRouteRequest(
        int sourceNodeId,
        int targetNodeId,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType
) {
}

