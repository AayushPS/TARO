package org.Aayush.routing.execution;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.heuristic.HeuristicType;

/**
 * Startup configuration for execution-profile selection.
 */
@Value
@Builder
public class ExecutionRuntimeConfig {
    String executionProfileId;
    ExecutionProfileSpec inlineExecutionProfileSpec;

    /**
     * Selects one named execution profile from the registry.
     */
    public static ExecutionRuntimeConfig ofProfileId(String executionProfileId) {
        return ExecutionRuntimeConfig.builder()
                .executionProfileId(executionProfileId)
                .build();
    }

    /**
     * Selects one explicit inline execution profile.
     */
    public static ExecutionRuntimeConfig inline(ExecutionProfileSpec inlineExecutionProfileSpec) {
        return ExecutionRuntimeConfig.builder()
                .inlineExecutionProfileSpec(inlineExecutionProfileSpec)
                .build();
    }

    /**
     * Returns an explicit inline Dijkstra execution runtime.
     */
    public static ExecutionRuntimeConfig dijkstra() {
        return inline(ExecutionProfileSpec.dijkstra(null));
    }

    /**
     * Returns an explicit inline A* execution runtime.
     */
    public static ExecutionRuntimeConfig aStar(HeuristicType heuristicType) {
        return inline(ExecutionProfileSpec.aStar(null, heuristicType));
    }

    /**
     * Returns an explicit inline A* profile using {@code NONE}.
     */
    public static ExecutionRuntimeConfig aStarNone() {
        return aStar(HeuristicType.NONE);
    }
}
