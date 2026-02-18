package org.Aayush.routing.core;

/**
 * Internal route planning output.
 *
 * @param reachable whether target is reachable from source.
 * @param totalCost total computed route cost (or {@code +INF} when unreachable).
 * @param arrivalTicks arrival tick at target (or departure tick when unreachable).
 * @param settledStates count of settled states during search.
 * @param nodePath internal node path from source to target (empty when unreachable).
 */
record InternalRoutePlan(
        boolean reachable,
        float totalCost,
        long arrivalTicks,
        int settledStates,
        int[] nodePath
) {
    /**
     * Creates a canonical unreachable plan result.
     */
    static InternalRoutePlan unreachable(long departureTicks, int settledStates) {
        return new InternalRoutePlan(false, Float.POSITIVE_INFINITY, departureTicks, settledStates, new int[0]);
    }
}
