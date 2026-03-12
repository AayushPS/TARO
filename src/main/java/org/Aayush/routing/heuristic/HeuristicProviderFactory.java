package org.Aayush.routing.heuristic;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

/**
 * Extension seam for creating validated heuristic providers.
 */
public interface HeuristicProviderFactory {
    /**
     * Creates one heuristic provider for the selected runtime profile.
     *
     * @param type selected heuristic type.
     * @param edgeGraph graph runtime.
     * @param profileStore profile runtime.
     * @param costEngine cost runtime.
     * @param landmarkStore optional landmark backing store.
     * @return initialized heuristic provider.
     */
    HeuristicProvider create(
            HeuristicType type,
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            LandmarkStore landmarkStore
    );
}
