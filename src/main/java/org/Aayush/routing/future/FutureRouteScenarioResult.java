package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.Aayush.routing.core.RouteResponse;

import java.util.List;

/**
 * Per-scenario optimal route result retained for frontend diagnostics.
 */
@Value
@Builder
public class FutureRouteScenarioResult {
    String scenarioId;
    String label;
    double probability;
    RouteResponse route;
    @Singular("explanationTag")
    List<String> explanationTags;
}
