package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Instant;
import java.util.List;

/**
 * Retained future-aware route result set exposed by {@code resultSetId}.
 */
@Value
@Builder(toBuilder = true)
public class FutureRouteResultSet {
    String resultSetId;
    Instant createdAt;
    Instant expiresAt;
    FutureRouteRequest request;
    TopologyVersion topologyVersion;
    String quarantineSnapshotId;
    ScenarioBundle scenarioBundle;
    CandidateDensityCalibrationReport candidateDensityCalibrationReport;
    ScenarioRouteSelection expectedRoute;
    ScenarioRouteSelection robustRoute;
    @Singular("alternative")
    List<ScenarioRouteSelection> alternatives;
    @Singular("scenarioResult")
    List<FutureRouteScenarioResult> scenarioResults;
}
