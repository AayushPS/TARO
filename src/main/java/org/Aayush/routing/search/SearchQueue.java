package org.Aayush.routing.search;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A specialized Min-Priority Queue optimized for Graph Search algorithms (Dijkstra/A*).
 * <p>
 * <strong>Key Features:</strong>
 * <ul>
 * <li><strong>Zero Allocation:</strong> Uses an internal stack-based Object Pool to reuse {@link SearchState} instances, preventing GC pressure during the hot path of the search.</li>
 * <li><strong>Decrease-Key Support:</strong> Supports efficient O(log n) cost updates for existing edges via an internal position tracking array.</li>
 * <li><strong>Strict Contracts:</strong> Enforces pool limits to detect leaks (forgotten recycles) during development.</li>
 * </ul>
 * </p>
 * * <p><strong>Usage Warning:</strong> This class is NOT thread-safe. It is intended for single-threaded use.</p>
 */
public class SearchQueue {

    // The Binary Heap (1-based indexing for easier parent/child math)
    private final SearchState[] heap;
    @Getter
    @Accessors(fluent = true)
    private int size = 0;

    // Position tracking for Decrease-Key: positions[edgeId] = heapIndex
    // Allows O(1) lookup of an edge's location in the heap.
    private final int[] positions;

    // The Object Pool (Stack-based)
    private final SearchState[] pool;
    private int poolTop; // Index of the top element in the pool

    // Diagnostics
    private int activeStates = 0; // Number of states currently checked out from the pool
    @Getter
    private int peakActiveStates = 0; // High-water mark for active states

    /**
     * Initializes the SearchQueue with fixed capacity.
     *
     * @param maxEdgeId The maximum valid edge ID in the graph. Used to size the position tracking array.
     * Must be non-negative.
     * @param capacity  The maximum number of states the queue can hold simultaneously.
     * Also defines the size of the object pool.
     * @throws IllegalArgumentException if maxEdgeId is negative.
     */
    public SearchQueue(int maxEdgeId, int capacity) {
        if (maxEdgeId < 0) {
            throw new IllegalArgumentException("maxEdgeId must be non-negative");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        // +1 for 1-based heap indexing
        this.heap = new SearchState[capacity + 1];

        // positions[edgeId] maps to index in heap. 0 means not present.
        // Size is maxEdgeId + 1 to support IDs from 0 to maxEdgeId.
        this.positions = new int[maxEdgeId + 1];

        this.pool = new SearchState[capacity];

        // Pre-allocate pool to ensure zero allocation during runtime
        for (int i = 0; i < capacity; i++) {
            pool[i] = new SearchState();
        }
        poolTop = capacity - 1;
    }

    /**
     * Inserts a new state into the queue or updates an existing one if the new path is better.
     * <p>
     * <strong>Behavior:</strong>
     * <ul>
     * <li>If {@code edgeId} is not in the queue: Acquires a state from the pool, sets it, and adds to heap.</li>
     * <li>If {@code edgeId} IS in the queue: Checks if new cost is lower. If so, updates the existing state and adjusts heap (Decrease-Key).</li>
     * </ul>
     * </p>
     *
     * @param edgeId      Destination edge ID (must be &le; maxEdgeId).
     * @param arrivalTime Time of arrival.
     * @param cost        Cumulative cost.
     * @param predecessor ID of the previous edge.
     * @throws IllegalArgumentException if edgeId is out of bounds.
     * @throws IllegalStateException    if the pool is exhausted or heap is full.
     */
    public void insert(int edgeId, long arrivalTime, float cost, int predecessor) {
        // 0. Strict Bounds Check
        if (edgeId < 0 || edgeId >= positions.length) {
            throw new IllegalArgumentException("edgeId " + edgeId + " out of bounds (max: " + (positions.length - 1) + ")");
        }

        // 1. Check if edge is already in heap (Decrease-Key / Update)
        int existingIdx = positions[edgeId];

        if (existingIdx > 0 && existingIdx <= size) {
            SearchState existing = heap[existingIdx];
            // If new path is better (lower cost), update
            if (cost < existing.cost) {
                existing.set(edgeId, arrivalTime, cost, predecessor);
                swim(existingIdx);
            } else if (cost == existing.cost && arrivalTime < existing.arrivalTime) {
                // Tie-breaker: Same cost, but earlier arrival time is better
                existing.set(edgeId, arrivalTime, cost, predecessor);
                swim(existingIdx);
            }
            return; // Updated existing, no new allocation needed
        }

        // 2. Insert New
        if (size >= heap.length - 1) {
            throw new IllegalStateException("Heap full. Increase capacity.");
        }

        if (poolTop < 0) {
            throw new IllegalStateException(
                    "Pool exhausted. Call clear() or recycle extracted states. " +
                            "Active: " + activeStates + ", Capacity: " + pool.length
            );
        }

        // Get from pool
        SearchState newState = pool[poolTop--];
        activeStates++;
        if (activeStates > peakActiveStates) {
            peakActiveStates = activeStates;
        }

        // Set data
        newState.set(edgeId, arrivalTime, cost, predecessor);

        // Add to heap
        size++;
        heap[size] = newState;

        // Track position
        positions[edgeId] = size;

        swim(size);
    }

    /**
     * Extracts the state with the minimum cost from the queue.
     * <p>
     * <strong>Contract:</strong> The caller MUST return the extracted state to the pool
     * via {@link #recycle(SearchState)} once processing is complete. Failure to do so
     * will exhaust the pool.
     * </p>
     *
     * @return The minimum {@link SearchState}.
     * @throws EmptyQueueException if queue is empty.
     */
    public SearchState extractMin() {
        if (isEmpty()) {
            throw new EmptyQueueException("Queue is empty");
        }

        SearchState min = heap[1];
        int lastIndex = size;

        // Single-element fast path.
        if (lastIndex == 1) {
            heap[1] = null;
            positions[min.edgeId] = 0;
            size = 0;
            return min;
        }

        SearchState last = heap[lastIndex];
        heap[1] = last;
        heap[lastIndex] = null; // Remove reference from heap to prevent accidental access
        size = lastIndex - 1;

        positions[last.edgeId] = 1;
        positions[min.edgeId] = 0;

        sink(1);

        return min;
    }

    /**
     * Returns a {@link SearchState} to the object pool for reuse.
     * <p>
     * This method is critical for the "Zero Allocation" strategy.
     * </p>
     *
     * @param state The state to recycle. If null, method does nothing.
     * @throws IllegalStateException if pool overflow is detected (double-recycle).
     */
    public void recycle(SearchState state) {
        if (state == null) return;

        // Basic sanity check to prevent obvious double-recycle issues
        if (poolTop >= pool.length - 1) {
            throw new IllegalStateException("Pool overflow or double-recycle detected");
        }
        if (activeStates <= 0) {
            throw new IllegalStateException("Recycle called with no active states");
        }

        activeStates--;
        pool[++poolTop] = state;
    }

    /**
     * Checks if the queue is empty.
     * @return true if empty, false otherwise.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears the queue and returns all active heap states to the pool.
     * <p>
     * This prepares the queue for a new search query. It also checks for leaked states
     * (states extracted but not recycled) and prints a warning if found.
     * </p>
     */
    public void clear() {
        for (int i = 1; i <= size; i++) {
            SearchState s = heap[i];
            if (s != null) {
                if (positions[s.edgeId] == i) {
                    positions[s.edgeId] = 0;
                }
                recycle(s);
                heap[i] = null;
            }
        }
        size = 0;

        // Diagnostic: Detect leaked states
        if (activeStates != 0) {
            int leaked = activeStates;
            System.err.println("WARNING: " + activeStates +
                    " states leaked (extracted but not recycled). Replenishing pool.");

            // Replenish missing pool slots so subsequent searches keep full capacity.
            for (int i = 0; i < leaked && poolTop < pool.length - 1; i++) {
                pool[++poolTop] = new SearchState();
            }

            // Reset accounting after replenishment.
            activeStates = 0;
        }
    }

    /**
     * Diagnostic: Returns current pool utilization ratio (0.0 to 1.0).
     * @return The ratio of active states to total capacity.
     */
    public double getPoolUtilization() {
        return (double) activeStates / pool.length;
    }

    // --- Heap Helper Methods ---

    /**
     * Heap up-heap operation for newly inserted or decreased-key states.
     */
    private void swim(int k) {
        while (k > 1 && greater(k / 2, k)) {
            swap(k, k / 2);
            k = k / 2;
        }
    }

    /**
     * Heap down-heap operation after min extraction.
     */
    private void sink(int k) {
        while (2 * k <= size) {
            int j = 2 * k;
            if (j < size && greater(j, j + 1)) j++;
            if (!greater(k, j)) break;
            swap(k, j);
            k = j;
        }
    }

    /**
     * Returns whether heap index {@code i} has lower priority than index {@code j}.
     */
    private boolean greater(int i, int j) {
        return heap[i].compareTo(heap[j]) > 0;
    }

    /**
     * Swaps two heap entries and updates position map accordingly.
     */
    private void swap(int i, int j) {
        SearchState s1 = heap[i];
        SearchState s2 = heap[j];

        heap[i] = s2;
        heap[j] = s1;

        // Update position tracking
        // Optimization: Removed bounds checks; strictly validated at insert
        positions[s1.edgeId] = j;
        positions[s2.edgeId] = i;
    }
}
