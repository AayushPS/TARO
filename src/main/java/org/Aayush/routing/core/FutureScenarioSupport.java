package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioProbabilityAudit;
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
                baseCostEngine.temporalSamplingPolicy(),
                edgeId -> "edge " + edgeId,
                baseCostEngine.profileValidationMode(),
                baseCostEngine.recurrenceCalibrationStore(),
                baseCostEngine.recencyCalibrationStore()
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
            validateProbabilityAudit(scenario);
            totalProbability += scenario.getProbability();
        }
        if (Math.abs(totalProbability - 1.0d) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException("scenario probabilities must sum to 1.0 within tolerance");
        }
    }

    private static void validateProbabilityAudit(ScenarioDefinition scenario) {
        ScenarioProbabilityAudit audit = scenario.getProbabilityAudit();
        if (audit == null) {
            return;
        }
        if (audit.getPolicyId() == null || audit.getPolicyId().isBlank()) {
            throw new IllegalArgumentException("scenario probabilityAudit policyId must be non-blank");
        }
        if (audit.getEvidenceSource() == null || audit.getEvidenceSource().isBlank()) {
            throw new IllegalArgumentException("scenario probabilityAudit evidenceSource must be non-blank");
        }
        if (!Double.isFinite(audit.getFreshnessWeight()) || audit.getFreshnessWeight() < 0.0d || audit.getFreshnessWeight() > 1.0d) {
            throw new IllegalArgumentException("scenario probabilityAudit freshnessWeight must be within [0.0, 1.0]");
        }
        if (!Double.isFinite(audit.getHorizonWeight()) || audit.getHorizonWeight() < 0.0d || audit.getHorizonWeight() > 1.0d) {
            throw new IllegalArgumentException("scenario probabilityAudit horizonWeight must be within [0.0, 1.0]");
        }
        if (!Double.isFinite(audit.getBaseProbability()) || audit.getBaseProbability() < 0.0d || audit.getBaseProbability() > 1.0d) {
            throw new IllegalArgumentException("scenario probabilityAudit baseProbability must be within [0.0, 1.0]");
        }
        if (!Double.isFinite(audit.getAdjustedProbability()) || audit.getAdjustedProbability() <= 0.0d || audit.getAdjustedProbability() > 1.0d) {
            throw new IllegalArgumentException("scenario probabilityAudit adjustedProbability must be within (0.0, 1.0]");
        }
        if (Math.abs(audit.getAdjustedProbability() - scenario.getProbability()) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException("scenario probabilityAudit adjustedProbability must match scenario probability");
        }
        if (audit.getObservedAtTicks() == null ^ audit.getEvidenceAgeTicks() == null) {
            throw new IllegalArgumentException("scenario probabilityAudit observedAtTicks and evidenceAgeTicks must both be set or both be null");
        }
        if (audit.getEvidenceAgeTicks() != null && audit.getEvidenceAgeTicks() < 0L) {
            throw new IllegalArgumentException("scenario probabilityAudit evidenceAgeTicks must be >= 0");
        }
    }
}
