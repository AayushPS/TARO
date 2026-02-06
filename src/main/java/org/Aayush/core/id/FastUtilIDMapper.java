package org.Aayush.core.id;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;

/**
 * Stage 1: ID Translation Layer (Implementation)
 * * A standalone implementation of IDMapper using the FastUtil library
 * for memory efficiency and performance.
 * * This class is immutable and thread-safe for concurrent reads.
 */
public class FastUtilIDMapper implements IDMapper{
    
    // fastutil map for String -> Int (Forward lookup)
    private final Object2IntOpenHashMap<String> forward;
    // Simple array for Int -> String (Reverse lookup) - Zero allocation read
    private final String[] reverse;

    /**
     * Constructs the mapper from a standard Java Map.
     * Validates that the indices are dense and 0-indexed.
     */
    public FastUtilIDMapper(Map<String, Integer> mappings) {
        if(mappings == null){
            throw new IllegalArgumentException("Mappings cannot be null");
        }
        int size = mappings.size();
        
        // Initialize forward map with expected size to minimize rehashing
        // Load factor 0.75 is standard, can be tuned for space vs time
        this.forward = new Object2IntOpenHashMap<>(size);
        this.forward.defaultReturnValue(-1); // Sentinel value
        
        // Initialize reverse lookup array
        this.reverse = new String[size];

        for (Map.Entry<String, Integer> entry : mappings.entrySet()) {
            String key = entry.getKey();
            int value = getValueAndCheckState(entry, size, this.reverse);

            this.forward.put(key, value);
            this.reverse[value] = key;
        }
        
        // Trim to ensure minimal memory usage
        this.forward.trim();
    }

    private static int getValueAndCheckState(Map.Entry<String, Integer> entry, int size, String[] reverse) {
        int value = entry.getValue();

        if (value < 0 || value >= size) {
            throw new IllegalArgumentException(
                "Input indices must be dense and 0-indexed. Found out of bounds: " + value
            );
        }

        // Check for duplicates in indices
        if (reverse[value] != null) {
            throw new IllegalArgumentException(
                "Duplicate internal index detected in input map: " + value
            );
        }
        return value;
    }

    @Override
    public int toInternal(String externalId) throws UnknownIDException {
        // fast util's getInt avoids auto-boxing/unboxing
        int id = forward.getInt(externalId);
        if (id == -1) {
            throw new UnknownIDException("External ID not found: " + externalId);
        }
        return id;
    }

    @Override
    public String toExternal(int internalId) {
        try {
            return reverse[internalId];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException("Internal ID out of bounds: " + internalId);
        }
    }

    @Override
    public boolean containsExternal(String externalId) {
        return forward.containsKey(externalId);
    }

    @Override
    public boolean containsInternal(int internalId) {
        return internalId >= 0 && internalId < reverse.length;
    }

    @Override
    public int size() {
        return reverse.length;
    }
}