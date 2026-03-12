package org.Aayush.routing.heuristic;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

/**
 * Default heuristic-provider factory backed by {@link HeuristicFactory}.
 */
public final class DefaultHeuristicProviderFactory implements HeuristicProviderFactory {
    /**
     * Creates one heuristic provider using the built-in validation rules.
     */
    @Override
    public HeuristicProvider create(
            HeuristicType type,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            LandmarkStore landmarkStore
    ) {
        return HeuristicFactory.create(type, edgeGraph, profileStore, costEngine, landmarkStore);
    }
}
