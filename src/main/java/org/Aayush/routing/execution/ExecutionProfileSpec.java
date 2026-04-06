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
    /** Execution profile identifier. */
    private String profileId;
    /** Routing algorithm bound to the execution profile. */
    private RoutingAlgorithm algorithm;
    /** Heuristic strategy bound to the execution profile. */
    private HeuristicType heuristicType;

    /**
     * Returns an explicit Dijkstra execution profile.
     *
     * @param profileId Execution profile identifier.
     * @return A Dijkstra execution profile.
     */
    public static ExecutionProfileSpec dijkstra(final String profileId) {
        return ExecutionProfileSpec.builder()
                .profileId(profileId)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
    }

    /**
     * Returns an explicit A* execution profile.
     *
     * @param profileId Execution profile identifier.
     * @param heuristicType Heuristic strategy for the A* execution profile.
     * @return An A* execution profile.
     */
    public static ExecutionProfileSpec aStar(
            final String profileId,
            final HeuristicType heuristicType) {
        return ExecutionProfileSpec.builder()
                .profileId(profileId)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(heuristicType)
                .build();
    }
}
