package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;

/**
 * Auditable B5 report describing how much candidate-route coverage survived into served output.
 */
@Value
@Builder(toBuilder = true)
public class CandidateDensityCalibrationReport {
    String policyId;
    int scenarioCount;
    int scenarioOptimalRouteCount;
    int uniqueScenarioOptimalRouteCount;
    int uniqueCandidateRouteCount;
    int aggregateAddedCandidateCount;
    boolean expectedRouteAggregateOnly;
    boolean robustRouteAggregateOnly;
    int selectedAlternativeCount;
    double scenarioCoverageRatio;
    double candidateCoverageRatio;
    double aggregateExpansionRatio;
    CandidateDensityClass densityClass;
}
