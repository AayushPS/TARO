package org.Aayush.routing.traits.temporal;

/**
 * Startup posture for temporal discretization drift.
 */
public enum TemporalGranularityLossPolicy {
    /**
     * Allow bucketed temporal execution only when its worst-case drift stays within the configured budget.
     */
    REJECT_EXCESS_DRIFT,
    /**
     * Publish the drift budget for observability but allow startup even when the configured bucket width exceeds it.
     */
    ALLOW_WITHIN_BUDGET
}
