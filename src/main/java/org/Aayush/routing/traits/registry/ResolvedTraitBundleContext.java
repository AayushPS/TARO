package org.Aayush.routing.traits.registry;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable resolved Stage 18 bundle context exposed to runtime components.
 */
@Value
@Builder
public class ResolvedTraitBundleContext {
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
