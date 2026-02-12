package org.Aayush.routing.core;

/**
 * Internal matrix plan output.
 *
 * @param reachable per-pair reachability flags.
 * @param totalCosts per-pair total costs.
 * @param arrivalTicks per-pair arrival ticks.
 * @param implementationNote implementation detail note propagated to callers.
 */
record MatrixPlan(
        boolean[][] reachable,
        float[][] totalCosts,
        long[][] arrivalTicks,
        String implementationNote
) {
}
