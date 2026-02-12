package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Client-facing point-to-point route request.
 * Internal node ids are intentionally hidden from callers.
 */
@Value
@Builder
public class RouteRequest {
    String sourceExternalId;
    String targetExternalId;
    long departureTicks;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
}

