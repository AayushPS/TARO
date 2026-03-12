package org.Aayush.routing.execution;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.HeuristicProvider;
import org.Aayush.routing.heuristic.HeuristicProviderFactory;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.profile.ProfileStore;

/**
 * Extension seam for binding one immutable execution runtime.
 */
public interface ExecutionRuntimeBinder {
    /**
     * Validates and binds one execution runtime configuration.
     *
     * @param input binding inputs and runtime contracts.
     * @return immutable binding result.
     */
    Binding bind(BindInput input);

    /**
     * Full bind input for startup execution-profile binding.
     */
    @Value
    @Builder
    class BindInput {
        ExecutionRuntimeConfig executionRuntimeConfig;
        ExecutionProfileRegistry executionProfileRegistry;
        EdgeGraph edgeGraph;
        ProfileStore profileStore;
        CostEngine costEngine;
        LandmarkStore landmarkStore;
        HeuristicProviderFactory heuristicProviderFactory;
    }

    /**
     * Immutable execution-profile binding output.
     */
    @Value
    @Builder
    class Binding {
        ResolvedExecutionProfileContext resolvedExecutionProfileContext;
        HeuristicProvider heuristicProvider;
    }
}
