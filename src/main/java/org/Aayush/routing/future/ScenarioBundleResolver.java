package org.Aayush.routing.future;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;

import java.time.Clock;

/**
 * Materializes deterministic future scenarios for one future-aware request.
 */
@FunctionalInterface
public interface ScenarioBundleResolver {
    ScenarioBundle resolve(
            ScenarioBundleRequest request,
            CostEngine baseCostEngine,
            ResolvedTemporalContext temporalContext,
            TopologyVersion topologyVersion,
            FailureQuarantine.Snapshot quarantineSnapshot,
            Clock clock
    );
}
