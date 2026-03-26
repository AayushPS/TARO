package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;

/**
 * Auditable B6 structural-prior context for one materialized scenario.
 */
@Value
@Builder
public class ScenarioStructuralPriorAudit {
    String policyId;
    double normalizedDegreeScore;
    double centeredDegreeSignal;
    double appliedAdjustment;
    double homophilyScore;
    int affectedEdgeCount;
}
