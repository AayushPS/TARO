package org.Aayush.routing.future;

import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Clock;

/**
 * Materializes deterministic future scenarios for one future-aware request.
 */
@FunctionalInterface
public interface ScenarioBundleResolver {
    ScenarioBundle resolve(
            ScenarioBundleRequest request,
            EdgeGraph edgeGraph,
            TopologyVersion topologyVersion,
            FailureQuarantine.Snapshot quarantineSnapshot,
            Clock clock
    );
}
