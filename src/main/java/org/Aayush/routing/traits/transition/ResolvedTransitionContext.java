package org.Aayush.routing.traits.transition;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable resolved transition context attached to normalized internal requests.
 */
@Value
@Builder
public class ResolvedTransitionContext {
    /**
     * Locked transition trait id.
     */
    String transitionTraitId;

    /**
     * Locked transition strategy id.
     */
    String transitionStrategyId;

    /**
     * True when finite turn penalties are enabled in the active strategy.
     */
    boolean finiteTurnPenaltiesEnabled;

    /**
     * Bound transition-cost strategy used by cost-engine hot path.
     */
    TransitionCostStrategy strategy;
}
