package org.Aayush.Utils;

/**
 * Represents a state in the time-dependent search graph.
 * Designed to be mutable and reusable to support object pooling (Zero Allocation).
 */
public class SearchState implements Comparable<SearchState> {
    public int edgeId;
    public long arrivalTime;
    public float cost;
    public int predecessor;

    public SearchState() {
        // Default constructor for pooling
    }

    /**
     * Re-initializes the state with new values.
     * Used by the object pool to recycle objects.
     */
    public void set(int edgeId, long arrivalTime, float cost, int predecessor) {
        this.edgeId = edgeId;
        this.arrivalTime = arrivalTime;
        this.cost = cost;
        this.predecessor = predecessor;
    }

    /**
     * Compare based on cost (primary) and arrivalTime (secondary/tiebreaker).
     * Returns negative if this is cheaper/earlier than other.
     */
    @Override
    public int compareTo(SearchState other) {
        // Primary: Cost
        int costCompare = Float.compare(this.cost, other.cost);
        if (costCompare != 0) {
            return costCompare;
        }
        // Secondary: Arrival Time (earlier is better/preferred for tie-breaking)
        return Long.compare(this.arrivalTime, other.arrivalTime);
    }

    @Override
    public String toString() {
        return "SearchState{" +
                "edge=" + edgeId +
                ", time=" + arrivalTime +
                ", cost=" + cost +
                ", pred=" + predecessor +
                '}';
    }
}