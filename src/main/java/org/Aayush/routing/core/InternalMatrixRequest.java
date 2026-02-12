package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Internal normalized matrix request (node ids only).
 */
record InternalMatrixRequest(
        int[] sourceNodeIds,
        int[] targetNodeIds,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType
) {
}

