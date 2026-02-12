package org.Aayush.routing.core;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.List;

/**
 * Client-facing point-to-point route response.
 */
@Value
@Builder
public class RouteResponse {
    boolean reachable;
    long departureTicks;
    long arrivalTicks;
    float totalCost;
    int settledStates;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
    @Singular("pathNode")
    List<String> pathExternalNodeIds;
}

