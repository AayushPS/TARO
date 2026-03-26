package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;

/**
 * Auditable probability-calibration details for one materialized scenario.
 */
@Value
@Builder
public class ScenarioProbabilityAudit {
    String policyId;
    String evidenceSource;
    Long observedAtTicks;
    Long evidenceAgeTicks;
    double freshnessWeight;
    double horizonWeight;
    double baseProbability;
    double adjustedProbability;
    ScenarioStructuralPriorAudit structuralPriorAudit;
}
