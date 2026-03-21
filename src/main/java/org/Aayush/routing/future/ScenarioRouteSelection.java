package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Aggregate summary for one selected route across all scenarios.
 */
@Value
@Builder
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
    String dominantScenarioId;
    String dominantScenarioLabel;
    @Singular("explanationTag")
    List<String> explanationTags;
}
