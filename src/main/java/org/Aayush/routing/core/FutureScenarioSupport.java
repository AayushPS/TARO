package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Shared validation and scenario cost-engine helpers for future-aware evaluation.
 */
final class FutureScenarioSupport {
    static final double PROBABILITY_TOLERANCE = 1.0e-6d;

    private FutureScenarioSupport() {
    }

    static CostEngine buildScenarioCostEngine(
            CostEngine baseCostEngine,
            long departureTicks,
            List<LiveUpdate> scenarioUpdates
    ) {
        LiveOverlay scenarioOverlay = baseCostEngine.liveOverlay().copyActiveSnapshot(departureTicks);
        if (scenarioUpdates != null && !scenarioUpdates.isEmpty()) {
            scenarioOverlay.applyBatch(scenarioUpdates, departureTicks);
        }
        return new CostEngine(
                baseCostEngine.edgeGraph(),
                baseCostEngine.profileStore(),
                scenarioOverlay,
                baseCostEngine.turnCostMap(),
                baseCostEngine.engineTimeUnit(),
                baseCostEngine.bucketSizeSeconds(),
                baseCostEngine.temporalSamplingPolicy()
        );
    }

    static void validateScenarioBundle(
            ScenarioBundle bundle,
            TopologyRuntimeSnapshot snapshot,
            FailureQuarantine.Snapshot quarantineSnapshot
    ) {
        ScenarioBundle nonNullBundle = Objects.requireNonNull(bundle, "scenarioBundle");
        if (nonNullBundle.getScenarios() == null || nonNullBundle.getScenarios().isEmpty()) {
            throw new IllegalArgumentException("scenarioBundle must contain at least one scenario");
        }
        if (!Objects.equals(nonNullBundle.getTopologyVersion(), snapshot.getTopologyVersion())) {
            throw new IllegalArgumentException("scenarioBundle topologyVersion must match active topology snapshot");
        }
        if (!Objects.equals(nonNullBundle.getQuarantineSnapshotId(), quarantineSnapshot.snapshotId())) {
            throw new IllegalArgumentException("scenarioBundle quarantineSnapshotId must match captured quarantine snapshot");
        }

        double totalProbability = 0.0d;
        for (ScenarioDefinition scenario : nonNullBundle.getScenarios()) {
            if (scenario.getScenarioId() == null || scenario.getScenarioId().isBlank()) {
                throw new IllegalArgumentException("scenarioId must be non-blank");
            }
            if (!Double.isFinite(scenario.getProbability()) || scenario.getProbability() <= 0.0d) {
                throw new IllegalArgumentException("scenario probability must be finite and > 0");
            }
            totalProbability += scenario.getProbability();
        }
        if (Math.abs(totalProbability - 1.0d) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException("scenario probabilities must sum to 1.0 within tolerance");
        }
    }
}
