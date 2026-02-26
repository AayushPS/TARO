package org.Aayush.routing.traits.transition;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable transition-runtime telemetry snapshot.
 */
@Value
@Builder
public class TransitionTelemetry {

    /**
     * Bound transition trait id.
     */
    String transitionTraitId;

    /**
     * Bound transition strategy id.
     */
    String transitionStrategyId;

    /**
     * True when finite turn penalties are enabled in the active strategy.
     */
    boolean finiteTurnPenaltiesEnabled;
}
