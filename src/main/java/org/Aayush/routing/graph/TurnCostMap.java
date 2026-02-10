package org.Aayush.routing.graph;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.serialization.flatbuffers.ModelContractValidator;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Stage 5: Turn Cost Lookup Structure.
 * <p>
 * A high-performance, read-only hash map optimized for storing turn penalties.
 * It maps (from_edge, to_edge) -> penalty using an open-addressing scheme
 * over primitive arrays to ensure zero allocation during lookups and minimal memory footprint.
 * <p>
 * PERFORMANCE CHARACTERISTICS:
 * - Time Complexity: O(1) average case lookup.
 * - Memory Overhead: ~20-24 bytes per entry (depending on load factor).
 * - Thread Safety: Immutable after construction, safe for concurrent reads.
 */
public class TurnCostMap {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    public static final float DEFAULT_COST = 0.0f;
    public static final float FORBIDDEN_TURN = Float.POSITIVE_INFINITY;
    private static final long EMPTY_KEY = -1L;

    // Singleton for empty maps to avoid allocation
    private static final TurnCostMap EMPTY_MAP = new TurnCostMap(0, 1, new long[]{EMPTY_KEY}, new float[]{DEFAULT_COST});

    // ========================================================================
    // INTERNAL DATA STRUCTURES (SoA)
    // ========================================================================

    // Keys: Packed long [(from_edge << 32) | to_edge]
    private final long[] keys;

    // Values: Penalty in seconds
    private final float[] values;

    // Size of the backing arrays (must be power of 2)
    private final int capacity;

    // Bitmask for fast modulo (capacity - 1)
    private final int mask;

    // Number of active entries
    @Getter
    @Accessors(fluent = true)
    private final int size;

    /**
     * Private constructor. Use {@link #fromFlatBuffer(ByteBuffer)} to instantiate.
     */
    private TurnCostMap(int size, int capacity, long[] keys, float[] values) {
        this.size = size;
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.keys = keys;
        this.values = values;
    }

    // ========================================================================
    // PUBLIC INTERFACE
    // ========================================================================

    /**
     * Gets the turn penalty for transitioning from one edge to another.
     * * @param fromEdge The source edge index.
     * @param toEdge   The destination edge index.
     * @return The penalty in seconds, {@link #DEFAULT_COST} if not found,
     * or {@link #FORBIDDEN_TURN} if restricted.
     */
    public float getCost(int fromEdge, int toEdge) {
        // 1. Construct Key
        long key = ((long) fromEdge << 32) | (toEdge & 0xFFFFFFFFL);

        // 2. Hash
        int index = mix(key) & mask;

        // 3. Probe (Linear Probing)
        // Check current slot. If key matches, return value.
        // If key is EMPTY_KEY, entry doesn't exist.
        // If key is different (collision), continue to next slot.

        long k = keys[index];
        if (k == key) return values[index];
        if (k == EMPTY_KEY) return DEFAULT_COST;

        while (true) {
            index = (index + 1) & mask;
            k = keys[index];
            if (k == key) return values[index];
            if (k == EMPTY_KEY) return DEFAULT_COST;
        }
    }

    /**
     * Checks if an explicit turn cost definition exists for this transition.
     * Note: This returns true for explicit 0.0 costs, but false for implicit default costs.
     */
    public boolean hasCost(int fromEdge, int toEdge) {
        long key = ((long) fromEdge << 32) | (toEdge & 0xFFFFFFFFL);
        int index = mix(key) & mask;

        long k = keys[index];
        if (k == key) return true;
        if (k == EMPTY_KEY) return false;

        while (true) {
            index = (index + 1) & mask;
            k = keys[index];
            if (k == key) return true;
            if (k == EMPTY_KEY) return false;
        }
    }

    /**
     * Checks if the transition is explicitly forbidden.
     */
    public boolean isForbidden(int fromEdge, int toEdge) {
        return getCost(fromEdge, toEdge) == FORBIDDEN_TURN;
    }

    // ========================================================================
    // HASHING STRATEGY
    // ========================================================================

    /**
     * Mixes the key bits to spread them across the array.
     * Uses MurmurHash3's 64-bit finalizer mix function.
     * Package-private for testing distribution.
     */
    static int mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int) k;
    }

    // ========================================================================
    // FACTORY / FLATBUFFERS LOADING
    // ========================================================================

    /**
     * Parses the FlatBuffer to build the optimized hash map.
     * * @param buffer The ByteBuffer containing the root Taro Model.
     * @return A ready-to-use TurnCostMap.
     */
    public static TurnCostMap fromFlatBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }

        ByteBuffer bb = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);

        // 1. Validate Buffer Size and Identifier
        if (bb.remaining() < 8) {
            throw new IllegalArgumentException("Buffer too small for .taro file header");
        }

        if (!Model.ModelBufferHasIdentifier(bb)) {
            int ident = bb.getInt(4);
            throw new IllegalArgumentException(String.format(
                    "Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X",
                    0x4F524154, ident));
        }

        Model model = Model.getRootAsModel(bb);
        ModelContractValidator.validateMetadataContract(model, "TurnCostMap");
        int vectorLen = model.turnCostsLength();
        if (vectorLen == 0) {
            return EMPTY_MAP;
        }

        // 3. Determine Capacity (Power of 2, Load Factor ~0.6)
        // Target Load Factor 0.6: capacity > count / 0.6 => capacity > count * 1.66
        int targetCapacity = (int) Math.ceil(vectorLen * 1.67);
        int capacity = 1;
        while (capacity < targetCapacity) {
            capacity <<= 1;
        }

        // 4. Allocate Arrays
        long[] keys = new long[capacity];
        float[] values = new float[capacity];
        Arrays.fill(keys, EMPTY_KEY);

        int mask = capacity - 1;
        int size = 0;

        // 5. Populate Map
        for (int i = 0; i < vectorLen; i++) {
            TurnCost turnCost = model.turnCosts(i);
            if (turnCost == null) {
                throw new IllegalArgumentException("turn_costs[" + i + "] is null");
            }
            int from = turnCost.fromEdgeIdx();
            int to = turnCost.toEdgeIdx();
            float penalty = turnCost.penaltySeconds();

            if (from < 0 || to < 0) {
                throw new IllegalArgumentException(
                        "turn_costs[" + i + "] contains negative edge id: from=" + from + ", to=" + to);
            }
            validatePenalty(penalty, i);

            long key = ((long) from << 32) | (to & 0xFFFFFFFFL);
            if (insert(keys, values, mask, key, penalty)) {
                size++;
            }
        }

        return new TurnCostMap(size, capacity, keys, values);
    }

    /**
     * internal insert helper for construction only.
     * @return true if a new key was inserted, false if an existing key was updated.
     */
    private static boolean insert(long[] keys, float[] values, int mask, long key, float value) {
        int index = mix(key) & mask;

        // Linear probe for empty slot
        while (keys[index] != EMPTY_KEY) {
            // In case of duplicate definitions in file, overwrite (last wins)
            if (keys[index] == key) {
                // Log warning to stderr (lightweight logging)
                // System.err.printf("WARN: Duplicate turn cost for edges %d->%d. Overwriting %.2f with %.2f%n",
                //     key >>> 32, key & 0xFFFFFFFFL, values[index], value);
                values[index] = value;
                return false; // Updated existing
            }
            index = (index + 1) & mask;
        }

        keys[index] = key;
        values[index] = value;
        return true; // Inserted new
    }

    private static void validatePenalty(float penalty, int index) {
        if (Float.isNaN(penalty) || penalty < 0.0f || penalty == Float.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException(
                    "turn_costs[" + index + "].penalty_seconds must be >= 0 and not NaN/-INF");
        }
    }

    @Override
    public String toString() {
        return String.format("TurnCostMap[size=%d, capacity=%d, load=%.2f%%]",
                size, capacity, (size * 100.0) / capacity);
    }
}
