package org.Aayush.routing.core;

/**
 * Internal matrix plan output.
 *
 * @param reachable per-pair reachability flags.
 * @param totalCosts per-pair total costs.
 * @param arrivalTicks per-pair arrival ticks.
 * @param implementationNote implementation detail note propagated to callers.
 * @param executionStats deterministic planner telemetry for one matrix execution.
 */
record MatrixPlan(
        boolean[][] reachable,
        float[][] totalCosts,
        long[][] arrivalTicks,
        String implementationNote,
        MatrixExecutionStats executionStats
) {
    /**
     * Creates plan with explicit implementation note and empty execution telemetry.
     */
    MatrixPlan(boolean[][] reachable, float[][] totalCosts, long[][] arrivalTicks, String implementationNote) {
        this(reachable, totalCosts, arrivalTicks, implementationNote, MatrixExecutionStats.empty(reachable.length));
    }

    /**
     * Normalizes null telemetry to an empty stats object.
     */
    MatrixPlan {
        if (executionStats == null) {
            executionStats = MatrixExecutionStats.empty(reachable.length);
        }
    }
}
