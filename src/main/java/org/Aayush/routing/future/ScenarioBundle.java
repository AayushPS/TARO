package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;
import java.util.List;

/**
 * Immutable scenario bundle bound to one topology/quarantine snapshot.
 */
@Value
@Builder
public class ScenarioBundle {
    String scenarioBundleId;
    Instant generatedAt;
    Instant validUntil;
    long horizonTicks;
    TopologyVersion topologyVersion;
    String quarantineSnapshotId;
    @Singular("scenario")
    List<ScenarioDefinition> scenarios;
}
