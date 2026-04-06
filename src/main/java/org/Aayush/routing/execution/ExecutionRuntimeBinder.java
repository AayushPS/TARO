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
        /** Runtime configuration to bind. */
        private ExecutionRuntimeConfig executionRuntimeConfig;
        /** Registry of available execution profiles. */
        private ExecutionProfileRegistry executionProfileRegistry;
        /** Graph used to bind execution-time routing dependencies. */
        private EdgeGraph edgeGraph;
        /** Profile storage used to resolve execution-time profile data. */
        private ProfileStore profileStore;
        /** Cost engine used to materialize execution-time edge costs. */
        private CostEngine costEngine;
        /** Landmark storage used by heuristic providers during binding. */
        private LandmarkStore landmarkStore;
        /** Heuristic-provider factory used during runtime binding. */
        private HeuristicProviderFactory heuristicProviderFactory;
    }

    /**
     * Immutable execution-profile binding output.
     */
    @Value
    @Builder
    class Binding {
        /** Resolved execution-profile context bound for runtime use. */
        private ResolvedExecutionProfileContext resolvedExecutionProfileContext;
        /** Heuristic provider resolved for the bound execution profile. */
        private HeuristicProvider heuristicProvider;
    }
}
