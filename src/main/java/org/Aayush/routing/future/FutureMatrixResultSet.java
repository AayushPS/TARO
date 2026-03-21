package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;
import java.util.List;

/**
 * Retained future-aware matrix result set exposed by {@code resultSetId}.
 */
@Value
@Builder
public class FutureMatrixResultSet {
    String resultSetId;
    Instant createdAt;
    Instant expiresAt;
    FutureMatrixRequest request;
    TopologyVersion topologyVersion;
    String quarantineSnapshotId;
    ScenarioBundle scenarioBundle;
    FutureMatrixAggregate aggregate;
    @Singular("scenarioResult")
    List<FutureMatrixScenarioResult> scenarioResults;
}
