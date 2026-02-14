package org.Aayush.routing.core;

/**
 * Deterministic reverse-lane frontier entry.
 */
record BackwardFrontierState(
        int nodeId,
        float lowerBoundDistance
) implements Comparable<BackwardFrontierState> {
    @Override
    public int compareTo(BackwardFrontierState other) {
        int byDistance = Float.compare(this.lowerBoundDistance, other.lowerBoundDistance);
        if (byDistance != 0) {
            return byDistance;
        }
        return Integer.compare(this.nodeId, other.nodeId);
    }
}
