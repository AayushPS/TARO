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
    /** Whether a path was found from source to target. */
    boolean reachable;
    /** Request departure tick echoed for traceability. */
    long departureTicks;
    /** Arrival tick at target (or departure tick when unreachable). */
    long arrivalTicks;
    /** Total route cost in cost-engine units. */
    float totalCost;
    /** Number of settled search states. */
    int settledStates;
    /** Search algorithm that produced this response. */
    RoutingAlgorithm algorithm;
    /** Heuristic type that was bound during planning. */
    HeuristicType heuristicType;
    /** Path expressed in external node ids from source to target. */
    @Singular("pathNode")
    List<String> pathExternalNodeIds;
}
