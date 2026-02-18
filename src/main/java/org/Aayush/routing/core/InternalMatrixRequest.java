package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Internal normalized matrix request (node ids only).
 *
 * <p>Instances are created by {@link RouteCore} after validating non-empty source/target
 * lists and translating all external ids.</p>
 *
 * @param sourceNodeIds internal source node ids mapped from external ids.
 * @param targetNodeIds internal target node ids mapped from external ids.
 * @param departureTicks departure time in engine ticks.
 * @param algorithm selected routing algorithm.
 * @param heuristicType selected heuristic type.
 */
record InternalMatrixRequest(
        int[] sourceNodeIds,
        int[] targetNodeIds,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType
) {
}
