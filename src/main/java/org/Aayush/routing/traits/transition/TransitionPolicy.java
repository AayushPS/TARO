package org.Aayush.routing.traits.transition;

import org.Aayush.routing.core.RouteCore;

import java.util.Objects;

/**
 * Stage 17 compatibility policy for trait/strategy tuples.
 */
public final class TransitionPolicy {

    /**
     * Validates one transition trait configuration tuple.
     */
    public void validateCompatibility(TransitionTrait trait, TransitionCostStrategy strategy) {
        TransitionTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
        TransitionCostStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");

        boolean nodeBasedTrait = TransitionTraitCatalog.TRAIT_NODE_BASED.equals(nonNullTrait.id());
        boolean edgeBasedTrait = TransitionTraitCatalog.TRAIT_EDGE_BASED.equals(nonNullTrait.id());

        if (!nodeBasedTrait && !edgeBasedTrait) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "transition trait is not recognized by Stage 17 policy: " + nonNullTrait.id()
            );
        }

        if (nodeBasedTrait && nonNullStrategy.appliesFiniteTurnPenalties()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "NODE_BASED trait requires strategy that ignores finite turn penalties"
            );
        }

        if (edgeBasedTrait && !nonNullStrategy.appliesFiniteTurnPenalties()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "EDGE_BASED trait requires strategy that applies finite turn penalties"
            );
        }
    }

    /**
     * Returns default Stage 17 compatibility policy.
     */
    public static TransitionPolicy defaults() {
        return new TransitionPolicy();
    }

    /**
     * Reason-coded compatibility validation failure.
     */
    public static final class CompatibilityException extends RuntimeException {
        private final String reasonCode;

        public CompatibilityException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
