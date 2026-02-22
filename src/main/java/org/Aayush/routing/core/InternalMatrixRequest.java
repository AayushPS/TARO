package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;

import java.util.Objects;

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
 * @param temporalContext locked Stage 16 temporal context.
 */
record InternalMatrixRequest(
        int[] sourceNodeIds,
        int[] targetNodeIds,
        long departureTicks,
        RoutingAlgorithm algorithm,
        HeuristicType heuristicType,
        ResolvedTemporalContext temporalContext
) {
    /**
     * Backward-compatible constructor that defaults to calendar-UTC temporal context.
     */
    InternalMatrixRequest(
            int[] sourceNodeIds,
            int[] targetNodeIds,
            long departureTicks,
            RoutingAlgorithm algorithm,
            HeuristicType heuristicType
    ) {
        this(
                sourceNodeIds,
                targetNodeIds,
                departureTicks,
                algorithm,
                heuristicType,
                ResolvedTemporalContext.defaultCalendarUtc()
        );
    }

    InternalMatrixRequest {
        temporalContext = Objects.requireNonNullElseGet(temporalContext, ResolvedTemporalContext::defaultCalendarUtc);
    }
}
