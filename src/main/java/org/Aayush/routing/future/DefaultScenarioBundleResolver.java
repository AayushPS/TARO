package org.Aayush.routing.future;

import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyVersion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default bounded resolver that turns active quarantines into a small scenario bundle.
 */
public final class DefaultScenarioBundleResolver implements ScenarioBundleResolver {
    private static final double INCIDENT_PERSISTS_PROBABILITY = 0.65d;
    private static final double CLEARING_FAST_PROBABILITY = 0.35d;

    @Override
    public ScenarioBundle resolve(
            ScenarioBundleRequest request,
            EdgeGraph edgeGraph,
            TopologyVersion topologyVersion,
            FailureQuarantine.Snapshot quarantineSnapshot,
            Clock clock
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(topologyVersion, "topologyVersion");
        Objects.requireNonNull(quarantineSnapshot, "quarantineSnapshot");
        Clock nonNullClock = Objects.requireNonNull(clock, "clock");

        Instant now = nonNullClock.instant();
        ScenarioBundle.ScenarioBundleBuilder bundle = ScenarioBundle.builder()
                .scenarioBundleId(UUID.randomUUID().toString())
                .generatedAt(now)
                .validUntil(now.plus(request.getResultTtl()))
                .horizonTicks(request.getHorizonTicks())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId(quarantineSnapshot.snapshotId());

        if (!quarantineSnapshot.hasActiveFailures()) {
            bundle.scenario(ScenarioDefinition.builder()
                    .scenarioId("baseline")
                    .label("baseline")
                    .probability(1.0d)
                    .build());
            return bundle.build();
        }

        List<String> explanationTags = quarantineSnapshot.explanationTags();
        long departureTicks = request.getDepartureTicks();
        long clearAtTicks = departureTicks + Math.max(1L, request.getHorizonTicks() / 2L);

        bundle.scenario(ScenarioDefinition.builder()
                .scenarioId("incident_persists")
                .label("incident_persists")
                .probability(INCIDENT_PERSISTS_PROBABILITY)
                .explanationTags(explanationTags)
                .liveUpdates(quarantineSnapshot.toLiveUpdates())
                .build());

        ArrayList<String> clearingTags = new ArrayList<>(explanationTags);
        clearingTags.add("recovery_expected");
        bundle.scenario(ScenarioDefinition.builder()
                .scenarioId("clearing_fast")
                .label("clearing_fast")
                .probability(CLEARING_FAST_PROBABILITY)
                .explanationTags(clearingTags)
                .liveUpdates(quarantineSnapshot.toLiveUpdates(edgeGraph, clearAtTicks))
                .build());

        return bundle.build();
    }
}
