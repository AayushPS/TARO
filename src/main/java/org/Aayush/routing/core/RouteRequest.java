package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Client-facing point-to-point route request.
 *
 * <p>Node identifiers are expressed in external id space. Mapping into internal
 * graph node ids is handled by {@link RouteCore}.</p>
 */
@Value
@Builder
public class RouteRequest {
    /** External identifier for route origin node. */
    String sourceExternalId;
    /** External identifier for route destination node. */
    String targetExternalId;
    /** Departure time in engine ticks. */
    long departureTicks;
    /** Search algorithm to execute. */
    RoutingAlgorithm algorithm;
    /** Heuristic mode to use (must match algorithm constraints). */
    HeuristicType heuristicType;
}
