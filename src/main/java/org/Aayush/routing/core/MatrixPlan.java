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
    MatrixPlan(boolean[][] reachable, float[][] totalCosts, long[][] arrivalTicks, String implementationNote) {
        this(reachable, totalCosts, arrivalTicks, implementationNote, MatrixExecutionStats.empty(reachable.length));
    }

    MatrixPlan {
        if (executionStats == null) {
            executionStats = MatrixExecutionStats.empty(reachable.length);
        }
    }
}
