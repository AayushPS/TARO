package org.Aayush.routing.core;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Encapsulates deterministic planner stop and safety checks.
 */
final class TerminationPolicy {
    static final String REASON_NON_FINITE_PRIORITY = "H13_NUMERIC_NON_FINITE_PRIORITY";
    static final String REASON_NEGATIVE_PRIORITY = "H13_NUMERIC_NEGATIVE_PRIORITY";

    /**
     * Default termination policy.
     */
    static TerminationPolicy defaults() {
        return new TerminationPolicy();
    }

    /**
     * Returns whether forward search can stop safely for current goal upper bound.
     */
    boolean shouldTerminate(float bestGoalCost, double nextForwardPriority) {
        ensureValidPriority(nextForwardPriority);
        if (!Float.isFinite(bestGoalCost)) {
            return false;
        }
        return Double.compare(nextForwardPriority, bestGoalCost) > 0;
    }

    private static void ensureValidPriority(double priority) {
        if (!Double.isFinite(priority)) {
            throw new NumericSafetyException(
                    REASON_NON_FINITE_PRIORITY,
                    "frontier priority must be finite, got " + priority
            );
        }
        if (priority < 0.0d) {
            throw new NumericSafetyException(
                    REASON_NEGATIVE_PRIORITY,
                    "frontier priority must be >= 0, got " + priority
            );
        }
    }

    /**
     * Deterministic exception for numeric guardrail failures.
     */
    @Getter
    @Accessors(fluent = true)
    static final class NumericSafetyException extends RuntimeException {
        private final String reasonCode;

        NumericSafetyException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }
    }
}
