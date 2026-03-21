package org.Aayush.routing.future;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteRequest;

import java.time.Duration;
import java.util.Objects;

/**
 * Client-facing future-aware route request.
 */
@Value
@Builder
public class FutureRouteRequest implements ScenarioBundleRequest {
    RouteRequest routeRequest;
    @Builder.Default
    long horizonTicks = 3_600L;
    @Builder.Default
    FutureRouteObjective preferredObjective = FutureRouteObjective.EXPECTED_ETA;
    @Builder.Default
    int topKAlternatives = 3;
    @Builder.Default
    Duration resultTtl = Duration.ofMinutes(10);

    @Override
    public long getDepartureTicks() {
        return Objects.requireNonNull(routeRequest, "routeRequest").getDepartureTicks();
    }
}
