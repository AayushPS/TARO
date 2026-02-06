package org.Aayush.routing.graph;

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
    private static final int FILE_IDENTIFIER = 0x4F524154; // "TARO"

    // Field indices in Model table (Root)
    // Schema Version: Optimization V3 (Taro Model)
    // NOTE: If the FlatBuffer schema changes, verify this index matches the 'turn_costs' field.
    private static final int FIELD_TURN_COSTS = 6;

    // Field indices in TurnCost table
    private static final int TC_FIELD_FROM = 0;
    private static final int TC_FIELD_TO = 1;
    private static final int TC_FIELD_PENALTY = 2;

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

    public int size() {
        return size;
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
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 1. Validate Buffer Size and Identifier
        if (buffer.remaining() < 8) {
            throw new IllegalArgumentException("Buffer too small for .taro file header");
        }

        // Check identifier at offset 4 (FlatBuffers standard)
        // We use absolute get(pos + 4) to respect current buffer position
        int ident = buffer.getInt(buffer.position() + 4);
        if (ident != FILE_IDENTIFIER) {
            throw new IllegalArgumentException(
                    String.format("Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X",
                            FILE_IDENTIFIER, ident));
        }

        // 2. Navigate to Turn Costs Vector
        int rootOffset = buffer.getInt(buffer.position()) + buffer.position();
        Table model = new Table(buffer, rootOffset);

        int turnCostsVectorOffset = model.getOffset(FIELD_TURN_COSTS);

        if (turnCostsVectorOffset == 0) {
            return EMPTY_MAP;
        }

        int vectorStart = model.getVectorStart(FIELD_TURN_COSTS);
        int vectorLen = model.getVectorLength(FIELD_TURN_COSTS);

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

        // 5. Populate Map (Parsing Table entries manually)
        // TurnCost is a table, so the vector contains offsets (ints) to the tables.

        // Create a reusable Table cursor
        Table cursor = new Table(buffer, 0);

        for (int i = 0; i < vectorLen; i++) {
            // Get offset to the i-th TurnCost table
            int tableOffset = buffer.getInt(vectorStart + i * 4) + (vectorStart + i * 4);
            cursor.setPos(tableOffset);

            int from = cursor.getIntField(TC_FIELD_FROM, -1);
            int to = cursor.getIntField(TC_FIELD_TO, -1);
            float penalty = cursor.getFloatField(TC_FIELD_PENALTY, 0.0f);

            if (from != -1 && to != -1) {
                long key = ((long) from << 32) | (to & 0xFFFFFFFFL);
                // Fix: Only increment size if a NEW key was inserted
                if (insert(keys, values, mask, key, penalty)) {
                    size++;
                }
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

    // ========================================================================
    // HELPER: Minimal FlatBuffer Table Reader
    // ========================================================================

    private static class Table {
        private final ByteBuffer bb;
        private int pos;
        private int vtablePos;
        private int vtableLen;

        Table(ByteBuffer bb, int pos) {
            this.bb = bb;
            setPos(pos);
        }

        void setPos(int pos) {
            this.pos = pos;
            if (pos == 0) {
                this.vtablePos = 0;
                this.vtableLen = 0;
            } else {
                this.vtablePos = pos - bb.getInt(pos);
                this.vtableLen = bb.getShort(vtablePos);
            }
        }

        int getOffset(int fieldIndex) {
            int vtableOffset = 4 + (fieldIndex * 2);
            return (vtableOffset < vtableLen) ? bb.getShort(vtablePos + vtableOffset) : 0;
        }

        int getIntField(int fieldIndex, int defaultValue) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? bb.getInt(pos + offset) : defaultValue;
        }

        float getFloatField(int fieldIndex, float defaultValue) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? bb.getFloat(pos + offset) : defaultValue;
        }

        int getVectorStart(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? pos + offset + bb.getInt(pos + offset) + 4 : 0;
        }

        int getVectorLength(int fieldIndex) {
            int offset = getOffset(fieldIndex);
            return (offset != 0) ? bb.getInt(pos + offset + bb.getInt(pos + offset)) : 0;
        }
    }

    @Override
    public String toString() {
        return String.format("TurnCostMap[size=%d, capacity=%d, load=%.2f%%]",
                size, capacity, (size * 100.0) / capacity);
    }
}