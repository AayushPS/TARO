package org.Aayush.routing.core;

/**
 * Internal matrix plan output.
 */
record MatrixPlan(
        boolean[][] reachable,
        float[][] totalCosts,
        long[][] arrivalTicks,
        String implementationNote
) {
}

