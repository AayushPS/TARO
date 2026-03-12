package org.Aayush.routing.traits.registry;

import lombok.Builder;
import lombok.Value;

/**
 * Startup telemetry for the selected Stage 18 trait bundle.
 */
@Value
@Builder
public class TraitBundleTelemetry {
    String bundleId;
    String configSource;
    String addressingTraitId;
    String coordinateDistanceStrategyId;
    String temporalTraitId;
    String temporalStrategyId;
    String timezonePolicyId;
    String modelProfileTimezone;
    String zoneId;
    String transitionTraitId;
    String transitionStrategyId;
    String traitHash;
}
