package org.Aayush.core.id;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


class FastUtilIDMapperTest {

    private Map<String, Integer> standardMappings;

    @BeforeEach
    void setUp() {
        standardMappings = new HashMap<>();
        standardMappings.put("NewYork", 0);
        standardMappings.put("LosAngeles", 1);
        standardMappings.put("Chicago", 2);
    }

    @Test
    @DisplayName("Baseline Correctness: Simple ASCII bidirectional mapping")
    void testSimpleMapping() {
        IDMapper mapper = IDMapper.createImmutable(standardMappings);

        // Forward
        assertEquals(0, mapper.toInternal("NewYork"));
        assertEquals(1, mapper.toInternal("LosAngeles"));

        // Reverse
        assertEquals("NewYork", mapper.toExternal(0));
        assertEquals("Chicago", mapper.toExternal(2));

        // Membership
        assertTrue(mapper.containsExternal("NewYork"));
        assertFalse(mapper.containsExternal("Miami"));
        assertTrue(mapper.containsInternal(0));
        assertFalse(mapper.containsInternal(99));

        assertEquals(3, mapper.size());
    }

    @Test
    @DisplayName("Unicode Support: Emojis and non-Latin scripts")
    void testUnicodeMapping() {
        Map<String, Integer> unicodeMap = new HashMap<>();
        String tokyo = "Tōkyō_東京";
        String rocket = "Node_🚀";

        unicodeMap.put(tokyo, 0);
        unicodeMap.put(rocket, 1);

        IDMapper mapper = new FastUtilIDMapper(unicodeMap);

        assertEquals(0, mapper.toInternal(tokyo));
        assertEquals("Node_🚀", mapper.toExternal(1));
        assertTrue(mapper.containsExternal("Tōkyō_東京"));
    }

    @Test
    @DisplayName("Exception Path: Unknown External ID")
    void testUnknownExternalId() {
        IDMapper mapper = new FastUtilIDMapper(standardMappings);

        assertThrows(IDMapper.UnknownIDException.class, () -> {
            mapper.toInternal("Atlantis");
        }, "Should throw UnknownIDException for missing string keys");
    }

    @Test
    @DisplayName("Exception Path: Invalid Internal ID")
    void testInvalidInternalId() {
        IDMapper mapper = new FastUtilIDMapper(standardMappings);

        assertThrows(IndexOutOfBoundsException.class, () -> {
            mapper.toExternal(100);
        }, "Should throw IndexOutOfBoundsException for high out-of-bounds index");

        assertThrows(IndexOutOfBoundsException.class, () -> {
            mapper.toExternal(-1);
        }, "Should throw IndexOutOfBoundsException for negative index");
    }

    @Test
    @DisplayName("Exception Path: Null external ID")
    void testNullExternalIdRejected() {
        IDMapper mapper = new FastUtilIDMapper(standardMappings);
        assertThrows(IllegalArgumentException.class, () -> mapper.toInternal(null));
        assertFalse(mapper.containsExternal(null));
    }

    @Test
    @DisplayName("Constructor Validation: Enforce Dense Indices")
    void testDenseIndexRequirement() {
        Map<String, Integer> sparseMap = new HashMap<>();
        sparseMap.put("A", 0);
        sparseMap.put("B", 2); // Missing 1

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new FastUtilIDMapper(sparseMap);
        });

        assertTrue(exception.getMessage().contains("out of bounds") ||
                        exception.getMessage().contains("must be dense"),
                "Constructor should reject sparse indices");
    }

    @Test
    @DisplayName("Constructor Validation: Reject null key/value entries")
    void testRejectNullEntries() {
        Map<String, Integer> nullKeyMap = new HashMap<>();
        nullKeyMap.put(null, 0);
        assertThrows(IllegalArgumentException.class, () -> new FastUtilIDMapper(nullKeyMap));

        Map<String, Integer> nullValueMap = new HashMap<>();
        nullValueMap.put("A", null);
        assertThrows(IllegalArgumentException.class, () -> new FastUtilIDMapper(nullValueMap));
    }

    @Test
    @DisplayName("Constructor Validation: Detect Duplicate Indices")
    void testDuplicateIndexDetection() {
        Map<String, Integer> duplicateMap = new HashMap<>();
        duplicateMap.put("A", 0);
        duplicateMap.put("B", 0); // Duplicate index 0

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new FastUtilIDMapper(duplicateMap);
        });

        assertTrue(exception.getMessage().contains("Duplicate internal index"),
                "Constructor should reject maps where multiple keys point to same index");
    }

    @Test
    @DisplayName("Stress Test: Large Volume and Long Strings")
    void testLargeVolume() {
        int count = 30_000_000;
        Map<String, Integer> bigMap = new HashMap<>(count);
        String prefix = "Node_";

        for (int i = 0; i < count; i++) {
            bigMap.put(prefix + i, i);
        }

        // Add a very long string entry
        String longKey = "A".repeat(2000);
        bigMap.put(longKey, count);

        IDMapper mapper = new FastUtilIDMapper(bigMap);

        assertEquals(count + 1, mapper.size());
        assertEquals(count, mapper.toInternal(longKey));
        assertEquals(longKey, mapper.toExternal(count));
        assertEquals(999, mapper.toInternal("Node_999"));
    }

    @Test
    @DisplayName("Concurrency: Thread-safe Read Operations")
    void testConcurrentReads() throws InterruptedException {
        IDMapper mapper = new FastUtilIDMapper(standardMappings);
        int threads = 20;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        // Read ops
                        int id = mapper.toInternal("NewYork");
                        String val = mapper.toExternal(id);
                        if (!val.equals("NewYork")) {
                            errors.incrementAndGet();
                        }

                        // Mixed reads
                        mapper.containsExternal("Chicago");
                        mapper.toExternal(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(finished, "Executor did not finish in time");
        assertEquals(0, errors.get(), "Concurrent reads caused errors or data corruption");
    }

    @Test
    @DisplayName("Performance: O(1) lookup verification")
    void testConstantTimeLookup() {
        // Populate 100K entries
        Map<String, Integer> bigMap = new HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            bigMap.put("Node_" + i, i);
        }
        IDMapper mapper = new FastUtilIDMapper(bigMap);

        // Time first 10% vs last 10%
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            mapper.toExternal(i % 10_000);
        }
        long earlyTime = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 90_000; i < 100_000; i++) {
            mapper.toExternal(i);
        }
        long lateTime = System.nanoTime() - start;

        assertTrue(lateTime < earlyTime * 1.2,
                "Late lookups should be within 20% of early lookups");
    }
    @Test
    @DisplayName("Memory: Verify overhead per entry")
    void testMemoryFootprint() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();

        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            map.put("Node_" + i, i);
        }
        IDMapper mapper = new FastUtilIDMapper(map);

        runtime.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();
        long perEntry = (after - before) / 100_000;

        assertTrue(perEntry < 120,
                "Memory per entry should be <120 bytes, got: " + perEntry);
    }

    @Test
    @DisplayName("Immutability: Constructor defensive copy")
    void testImmutability() {
        Map<String, Integer> mutableMap = new HashMap<>();
        mutableMap.put("A", 0);
        mutableMap.put("B", 1);

        IDMapper mapper = new FastUtilIDMapper(mutableMap);

        // Modify original map
        mutableMap.put("C", 2);
        mutableMap.remove("A");

        // Mapper should be unaffected
        assertEquals(2, mapper.size());
        assertTrue(mapper.containsExternal("A"));
        assertFalse(mapper.containsExternal("C"));
    }

    @Test
    @DisplayName("Hash Collisions: Verify correctness under collisions")
    void testHashCollisionHandling() {
        Map<String, Integer> map = new HashMap<>();
        // Generate strings that will definitely collide in FastUtil's hash
        for (int i = 0; i < 50_000; i++) {
            map.put("collision_test_" + String.format("%05d", i), i);
        }

        IDMapper mapper = new FastUtilIDMapper(map);

        // Verify every mapping survived
        for (int i = 0; i < 50_000; i++) {
            String key = "collision_test_" + String.format("%05d", i);
            assertEquals(i, mapper.toInternal(key),
                    "Failed at index " + i);
        }
    }
}
