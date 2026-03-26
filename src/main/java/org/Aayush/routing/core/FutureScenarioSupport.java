package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioProbabilityAudit;
import org.Aayush.routing.future.ScenarioStructuralPriorAudit;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        if (nonNullBundle.getScenarioBundleId() == null || nonNullBundle.getScenarioBundleId().isBlank()) {
            throw new IllegalArgumentException("scenarioBundleId must be non-blank");
        }
        if (nonNullBundle.getGeneratedAt() == null) {
            throw new IllegalArgumentException("scenarioBundle generatedAt must be non-null");
        }
        if (nonNullBundle.getValidUntil() == null) {
            throw new IllegalArgumentException("scenarioBundle validUntil must be non-null");
        }
        if (!nonNullBundle.getValidUntil().isAfter(nonNullBundle.getGeneratedAt())) {
            throw new IllegalArgumentException("scenarioBundle validUntil must be after generatedAt");
        }
        if (nonNullBundle.getHorizonTicks() <= 0L) {
            throw new IllegalArgumentException("scenarioBundle horizonTicks must be > 0");
        }
        if (nonNullBundle.getTopologyVersion() == null) {
            throw new IllegalArgumentException("scenarioBundle topologyVersion must be non-null");
        }
        if (nonNullBundle.getQuarantineSnapshotId() == null || nonNullBundle.getQuarantineSnapshotId().isBlank()) {
            throw new IllegalArgumentException("scenarioBundle quarantineSnapshotId must be non-blank");
        }
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
        int edgeCount = snapshot.getRouteCore().costEngineContract().edgeGraph().edgeCount();
        Set<String> scenarioIds = new HashSet<>();
        for (ScenarioDefinition scenario : nonNullBundle.getScenarios()) {
            if (scenario == null) {
                throw new IllegalArgumentException("scenarioBundle scenarios must not contain null entries");
            }
            if (scenario.getScenarioId() == null || scenario.getScenarioId().isBlank()) {
                throw new IllegalArgumentException("scenarioId must be non-blank");
            }
            if (!scenarioIds.add(scenario.getScenarioId())) {
                throw new IllegalArgumentException("scenarioId values must be unique within one scenarioBundle");
            }
            if (scenario.getLabel() == null || scenario.getLabel().isBlank()) {
                throw new IllegalArgumentException("scenario label must be non-blank");
            }
            if (!Double.isFinite(scenario.getProbability()) || scenario.getProbability() <= 0.0d) {
                throw new IllegalArgumentException("scenario probability must be finite and > 0");
            }
            validateExplanationTags(scenario.getExplanationTags());
            validateLiveUpdates(scenario.getLiveUpdates(), edgeCount);
            validateProbabilityAudit(scenario);
            totalProbability += scenario.getProbability();
        }
        if (Math.abs(totalProbability - 1.0d) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException("scenario probabilities must sum to 1.0 within tolerance");
        }
    }

    private static void validateExplanationTags(List<String> explanationTags) {
        if (explanationTags == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String tag : explanationTags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("scenario explanationTags must contain only non-blank values");
            }
            if (!seen.add(tag)) {
                throw new IllegalArgumentException("scenario explanationTags must be unique within one scenario");
            }
        }
    }

    private static void validateLiveUpdates(List<LiveUpdate> liveUpdates, int edgeCount) {
        if (liveUpdates == null) {
            return;
        }
        Set<Integer> edgeIds = new HashSet<>();
        for (LiveUpdate liveUpdate : liveUpdates) {
            if (liveUpdate == null) {
                throw new IllegalArgumentException("scenario liveUpdates must not contain null entries");
            }
            if (liveUpdate.edgeId() >= edgeCount) {
                throw new IllegalArgumentException("scenario liveUpdates edgeId must exist in the active topology snapshot");
            }
            if (!edgeIds.add(liveUpdate.edgeId())) {
                throw new IllegalArgumentException(
                        "scenario liveUpdates must not contain multiple updates for the same edgeId"
                );
            }
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
        validateStructuralPriorAudit(audit.getStructuralPriorAudit());
    }

    private static void validateStructuralPriorAudit(ScenarioStructuralPriorAudit audit) {
        if (audit == null) {
            return;
        }
        if (audit.getPolicyId() == null || audit.getPolicyId().isBlank()) {
            throw new IllegalArgumentException("scenario structuralPriorAudit policyId must be non-blank");
        }
        if (!Double.isFinite(audit.getNormalizedDegreeScore())
                || audit.getNormalizedDegreeScore() < 0.0d
                || audit.getNormalizedDegreeScore() > 1.0d) {
            throw new IllegalArgumentException("scenario structuralPriorAudit normalizedDegreeScore must be within [0.0, 1.0]");
        }
        if (!Double.isFinite(audit.getCenteredDegreeSignal())
                || audit.getCenteredDegreeSignal() < -1.0d
                || audit.getCenteredDegreeSignal() > 1.0d) {
            throw new IllegalArgumentException("scenario structuralPriorAudit centeredDegreeSignal must be within [-1.0, 1.0]");
        }
        if (!Double.isFinite(audit.getAppliedAdjustment())
                || audit.getAppliedAdjustment() < -1.0d
                || audit.getAppliedAdjustment() > 1.0d) {
            throw new IllegalArgumentException("scenario structuralPriorAudit appliedAdjustment must be within [-1.0, 1.0]");
        }
        if (!Double.isFinite(audit.getHomophilyScore())
                || audit.getHomophilyScore() < 0.0d
                || audit.getHomophilyScore() > 1.0d) {
            throw new IllegalArgumentException("scenario structuralPriorAudit homophilyScore must be within [0.0, 1.0]");
        }
        if (audit.getAffectedEdgeCount() < 0) {
            throw new IllegalArgumentException("scenario structuralPriorAudit affectedEdgeCount must be >= 0");
        }
    }
}
