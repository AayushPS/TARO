package org.Aayush.Utils;

import java.util.BitSet;

/**
 * A memory-efficient Set implementation for tracking visited edges in a graph.
 * <p>
 * This class wraps a {@link java.util.BitSet} to provide O(1) access time and
 * extremely low memory footprint (~1 bit per edge).
 * </p>
 * <p>
 * <strong>Thread Safety:</strong> This class is NOT thread-safe. It is intended
 * for use within a thread-local search context.
 * </p>
 */
public class VisitedSet {

    private final BitSet visited;

    /**
     * Constructs a new VisitedSet.
     *
     * @param initialCapacity The initial size of the bit set.
     * It is recommended to set this to the total number of edges
     * in the graph to avoid resizing overhead.
     */
    public VisitedSet(int initialCapacity) {
        this.visited = new BitSet(initialCapacity);
    }

    /**
     * Marks an edge as visited if it hasn't been visited already.
     *
     * @param edgeId The unique identifier of the edge.
     * @return {@code true} if the edge was successfully marked (was NOT previously visited).
     * {@code false} if the edge was already marked as visited.
     */
    public boolean markVisited(int edgeId) {
        if (visited.get(edgeId)) {
            return false;
        }
        visited.set(edgeId);
        return true;
    }

    /**
     * Checks if an edge has been visited.
     *
     * @param edgeId The unique identifier of the edge.
     * @return {@code true} if the edge has been visited, {@code false} otherwise.
     */
    public boolean isVisited(int edgeId) {
        return visited.get(edgeId);
    }

    /**
     * Resets the set for reuse.
     * This clears all bits, effectively marking all edges as unvisited.
     */
    public void clear() {
        visited.clear();
    }
}