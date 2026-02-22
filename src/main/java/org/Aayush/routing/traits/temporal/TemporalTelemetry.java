package org.Aayush.routing.traits.temporal;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable temporal-runtime telemetry snapshot.
 */
@Value
@Builder
public class TemporalTelemetry {

    /**
     * Bound temporal trait id.
     */
    String temporalTraitId;

    /**
     * Bound temporal strategy id.
     */
    String temporalStrategyId;

    /**
     * Bound timezone policy id, or {@code null} when not applicable.
     */
    String timezonePolicyId;

    /**
     * Bound zone id used for calendar resolution, or {@code null} for linear mode.
     */
    String zoneId;
}
