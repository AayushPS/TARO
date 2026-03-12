package org.Aayush.routing.execution;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Immutable startup execution-profile specification.
 */
@Value
@Builder
public class ExecutionProfileSpec {
    String profileId;
    RoutingAlgorithm algorithm;
    HeuristicType heuristicType;

    /**
     * Returns an explicit Dijkstra execution profile.
     */
    public static ExecutionProfileSpec dijkstra(String profileId) {
        return ExecutionProfileSpec.builder()
                .profileId(profileId)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
    }

    /**
     * Returns an explicit A* execution profile.
     */
    public static ExecutionProfileSpec aStar(String profileId, HeuristicType heuristicType) {
        return ExecutionProfileSpec.builder()
                .profileId(profileId)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(heuristicType)
                .build();
    }
}
