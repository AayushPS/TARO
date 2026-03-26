package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Stage C4 aggregate summary for one selected route across all scenarios.
 * Satisfies closure criterion: explanation and confidence fields remain consistent with
 * scenario probabilities and aggregate objectives.
 */
@Value
@Builder(toBuilder = true)
public class ScenarioRouteSelection {
    RouteShape route;
    float expectedCost;
    float p50Cost;
    float p90Cost;
    float minCost;
    float maxCost;
    long minArrivalTicks;
    long maxArrivalTicks;
    double optimalityProbability;
    float expectedRegret;
    long etaBandLowerArrivalTicks;
    long etaBandUpperArrivalTicks;
    String dominantScenarioId;
    double dominantScenarioProbability;
    String dominantScenarioLabel;
    RouteSelectionProvenance routeSelectionProvenance;
    @Singular("explanationTag")
    List<String> explanationTags;
}
