package org.Aayush.routing.core;

/**
 * Internal route planning output.
 *
 * @param reachable whether target is reachable from source.
 * @param totalCost total computed route cost (or {@code +INF} when unreachable).
 * @param arrivalTicks arrival tick at target (or departure tick when unreachable).
 * @param settledStates count of settled states during search.
 * @param nodePath internal node path from source to target (empty when unreachable).
 * @param edgePath internal edge path from source to target (empty when unreachable or source==target).
 */
public record InternalRoutePlan(
        boolean reachable,
        float totalCost,
        long arrivalTicks,
        int settledStates,
        int[] nodePath,
        int[] edgePath
) {
    /**
     * Backward-compatible constructor for callers that only provide the node path.
     */
    public InternalRoutePlan(
            boolean reachable,
            float totalCost,
            long arrivalTicks,
            int settledStates,
            int[] nodePath
    ) {
        this(reachable, totalCost, arrivalTicks, settledStates, nodePath, new int[0]);
    }

    /**
     * Creates a canonical unreachable plan result.
     */
    public static InternalRoutePlan unreachable(long departureTicks, int settledStates) {
        return new InternalRoutePlan(false, Float.POSITIVE_INFINITY, departureTicks, settledStates, new int[0], new int[0]);
    }
}
