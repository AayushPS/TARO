package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Internal normalized route request (node ids only).
 *
 * <p>Instances are created by {@link RouteCore} after external-id translation and
 * contract validation.</p>
 *
 * @param sourceNodeId internal source node id (validated in graph bounds).
 * @param targetNodeId internal target node id (validated in graph bounds).
 * @param departureTicks departure time in engine ticks.
 * @param algorithm selected routing algorithm.
 * @param heuristicType selected heuristic type compatible with algorithm.
 */
record InternalRouteRequest(
        int sourceNodeId,
        int targetNodeId,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType
) {
}
