package org.Aayush.routing.core;

/**
 * Internal route planning output.
 */
record InternalRoutePlan(
        boolean reachable,
        float totalCost,
        long arrivalTicks,
        int settledStates,
        int[] nodePath
) {
    static InternalRoutePlan unreachable(long departureTicks, int settledStates) {
        return new InternalRoutePlan(false, Float.POSITIVE_INFINITY, departureTicks, settledStates, new int[0]);
    }
}

