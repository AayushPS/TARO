package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;

import java.util.Objects;

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
 * @param temporalContext locked Stage 16 temporal context.
 */
record InternalRouteRequest(
        int sourceNodeId,
        int targetNodeId,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType,
        ResolvedTemporalContext temporalContext
) {
    /**
     * Backward-compatible constructor that defaults to calendar-UTC temporal context.
     */
    InternalRouteRequest(
            int sourceNodeId,
            int targetNodeId,
            long departureTicks,
            RoutingAlgorithm algorithm,
            HeuristicType heuristicType
    ) {
        this(sourceNodeId, targetNodeId, departureTicks, algorithm, heuristicType, ResolvedTemporalContext.defaultCalendarUtc());
    }

    InternalRouteRequest {
        temporalContext = Objects.requireNonNullElseGet(temporalContext, ResolvedTemporalContext::defaultCalendarUtc);
    }
}
