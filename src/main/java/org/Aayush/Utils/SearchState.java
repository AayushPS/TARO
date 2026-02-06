package org.Aayush.Utils;

/**
 * Represents a mutable state in the time-dependent search graph.
 * <p>
 * <strong>Design Pattern: Object Pooling</strong><br>
 * This class is designed to be instantiated once and reused throughout the application's lifecycle
 * via the {@link SearchQueue} to ensure zero garbage collection overhead during search operations.
 * </p>
 */
public class SearchState implements Comparable<SearchState> {

    /** The unique identifier of the edge in the graph. */
    public int edgeId;

    /** The arrival time at the target node of the edge. */
    public long arrivalTime;

    /** The cumulative cost (weight) to reach this state. */
    public float cost;

    /** The edge ID of the predecessor in the path (for path reconstruction). */
    public int predecessor;

    /**
     * Default constructor.
     * Intended for pre-allocation within an object pool.
     */
    public SearchState() {
        // Default constructor for pooling
    }

    /**
     * Mutator to re-initialize the state with new values.
     * Used by the {@link SearchQueue} when recycling objects from the pool.
     *
     * @param edgeId      The destination edge ID.
     * @param arrivalTime The time of arrival.
     * @param cost        The cumulative cost.
     * @param predecessor The previous edge ID.
     */
    public void set(int edgeId, long arrivalTime, float cost, int predecessor) {
        this.edgeId = edgeId;
        this.arrivalTime = arrivalTime;
        this.cost = cost;
        this.predecessor = predecessor;
    }

    /**
     * Compares two states for ordering in the Priority Queue.
     * <p>
     * <strong>Ordering Logic:</strong>
     * <ol>
     * <li><strong>Primary:</strong> Cost (Lower is better).</li>
     * <li><strong>Secondary:</strong> Arrival Time (Earlier is better).</li>
     * </ol>
     * </p>
     *
     * @param other The state to compare against.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
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