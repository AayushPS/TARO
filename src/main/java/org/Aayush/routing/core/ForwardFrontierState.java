package org.Aayush.routing.core;

/**
 * Deterministic forward-lane frontier entry.
 */
record ForwardFrontierState(
        int labelId,
        int edgeId,
        long arrivalTicks,
        double priority
) implements Comparable<ForwardFrontierState> {
    @Override
    public int compareTo(ForwardFrontierState other) {
        int byPriority = Double.compare(this.priority, other.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        int byArrival = Long.compare(this.arrivalTicks, other.arrivalTicks);
        if (byArrival != 0) {
            return byArrival;
        }
        int byEdge = Integer.compare(this.edgeId, other.edgeId);
        if (byEdge != 0) {
            return byEdge;
        }
        return Integer.compare(this.labelId, other.labelId);
    }
}
