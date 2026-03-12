package org.Aayush.routing.execution;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Immutable resolved execution profile exposed by runtime components.
 */
@Value
@Builder
public class ResolvedExecutionProfileContext {
    String profileId;
    String configSource;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;
}
