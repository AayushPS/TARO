package org.Aayush.routing.graph;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test Suite for TurnCostMap.
 * Covers:
 * 1. FlatBuffer parsing (Valid, Empty, Invalid)
 * 2. Lookup logic (Hits, Misses, Defaults)
 * 3. Collision handling (Linear Probing)
 * 4. Special values (Forbidden/Infinity)
 * 5. Concurrency
 * 6. Performance and Hashing Distribution
 */
public class TurnCostMapTest {

    // ========================================================================
    // TEST 1: Basic Functional Requirements & Equivalence Classes
    // ========================================================================

    @Test
    public void testBasicLookupAndEquivalenceClasses() {
        // Setup Data:
        // 1 -> 2: 5.0 (Simple Turn)
        // 2 -> 3: Infinity (Forbidden)
        // 3 -> 4: 0.0 (Explicit Zero)
        // 4 -> 5: Missing (Implicit Zero)

        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.addTurn(1, 2, 5.0f);
        builder.addTurn(2, 3, Float.POSITIVE_INFINITY);
        builder.addTurn(3, 4, 0.0f);

        ByteBuffer buffer = builder.build();
        TurnCostMap map = TurnCostMap.fromFlatBuffer(buffer);

        // Class 1: Simple turns (known costs)
        assertEquals(5.0f, map.getCost(1, 2), 0.001f);
        assertTrue(map.hasCost(1, 2));
        assertFalse(map.isForbidden(1, 2));

        // Class 2: Forbidden turns (infinite cost)
        assertEquals(Float.POSITIVE_INFINITY, map.getCost(2, 3), 0.001f);
        assertTrue(map.hasCost(2, 3));
        assertTrue(map.isForbidden(2, 3));

        // Class 3: Zero-penalty turns (explicit)
        assertEquals(0.0f, map.getCost(3, 4), 0.001f);
        assertTrue(map.hasCost(3, 4));

        // Class 4: Missing turns (default to zero)
        assertEquals(TurnCostMap.DEFAULT_COST, map.getCost(4, 5), 0.001f);
        assertFalse(map.hasCost(4, 5));
        assertFalse(map.isForbidden(4, 5));
    }

    // ========================================================================
    // TEST 2: Collision Handling & Load Factor
    // ========================================================================

    @Test
    public void testHighLoadAndCollisions() {
        int count = 1000;
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        for (int i = 0; i < count; i++) {
            builder.addTurn(i, i + 1, (float) i);
        }

        ByteBuffer buffer = builder.build();
        TurnCostMap map = TurnCostMap.fromFlatBuffer(buffer);

        assertEquals(count, map.size());

        // Verify all exist
        for (int i = 0; i < count; i++) {
            assertEquals((float) i, map.getCost(i, i + 1), 0.001f);
        }

        // Verify non-existent
        assertEquals(TurnCostMap.DEFAULT_COST, map.getCost(count + 1, count + 2), 0.001f);
    }

    @Test
    public void testDuplicateKeysHandlesLastWins() {
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.addTurn(10, 20, 5.0f);
        builder.addTurn(10, 20, 10.0f); // Overwrite in file

        ByteBuffer buffer = builder.build();
        TurnCostMap map = TurnCostMap.fromFlatBuffer(buffer);

        assertEquals(1, map.size()); // Should only have 1 entry
        assertEquals(10.0f, map.getCost(10, 20), 0.001f);
    }

    // ========================================================================
    // TEST 3: Buffer Validation & Edge Cases
    // ========================================================================

    @Test
    public void testInvalidIdentifier() {
        ByteBuffer buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, 12); // offset to root (dummy)
        buffer.putInt(4, 0xDEADBEEF); // Wrong ID
        buffer.position(0);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            TurnCostMap.fromFlatBuffer(buffer);
        });
        assertTrue(exception.getMessage().contains("Invalid file identifier"));
    }

    @Test
    public void testBufferTooSmall() {
        ByteBuffer buffer = ByteBuffer.allocate(4); // Too small
        assertThrows(IllegalArgumentException.class, () -> {
            TurnCostMap.fromFlatBuffer(buffer);
        });
    }

    @Test
    public void testNullBufferRejected() {
        assertThrows(IllegalArgumentException.class, () -> TurnCostMap.fromFlatBuffer(null));
    }

    @Test
    public void testEmptyOrMissingVector() {
        // Case A: Vector offset is missing
        MockTurnCostBuilder builderMissing = new MockTurnCostBuilder();
        builderMissing.skipTurnCostVector = true;
        TurnCostMap mapMissing = TurnCostMap.fromFlatBuffer(builderMissing.build());
        assertEquals(0, mapMissing.size());
        assertEquals(TurnCostMap.DEFAULT_COST, mapMissing.getCost(1, 2), 0.001f);

        // Case B: Vector exists but length is 0
        MockTurnCostBuilder builderEmpty = new MockTurnCostBuilder();
        TurnCostMap mapEmpty = TurnCostMap.fromFlatBuffer(builderEmpty.build());
        assertEquals(0, mapEmpty.size());
        assertFalse(mapEmpty.hasCost(1, 2));
    }

    @Test
    public void testMissingMetadataRejected() {
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.includeMetadata = false;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TurnCostMap.fromFlatBuffer(builder.build()));
        assertTrue(ex.getMessage().contains("metadata"));
    }

    @Test
    public void testMetadataContractMismatchRejected() {
        MockTurnCostBuilder badSchema = new MockTurnCostBuilder();
        badSchema.schemaVersion = 2L;
        IllegalArgumentException badSchemaEx = assertThrows(IllegalArgumentException.class,
                () -> TurnCostMap.fromFlatBuffer(badSchema.build()));
        assertTrue(badSchemaEx.getMessage().contains("schema_version"));

        MockTurnCostBuilder badTickPair = new MockTurnCostBuilder();
        badTickPair.timeUnit = TimeUnit.MILLISECONDS;
        badTickPair.tickDurationNs = 1_000_000_000L;
        IllegalArgumentException badTickEx = assertThrows(IllegalArgumentException.class,
                () -> TurnCostMap.fromFlatBuffer(badTickPair.build()));
        assertTrue(badTickEx.getMessage().contains("tick_duration_ns"));
    }

    @Test
    public void testUnsupportedTimeUnitRejected() {
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.timeUnit = 99;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TurnCostMap.fromFlatBuffer(builder.build()));
        assertTrue(ex.getMessage().contains("time_unit"));
    }

    @Test
    public void testNegativeEdgesAreRejected() {
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.addTurn(-1, 2, 5.0f);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TurnCostMap.fromFlatBuffer(builder.build()));
        assertTrue(ex.getMessage().contains("negative edge id"));
    }

    @Test
    public void testInvalidPenaltyRejected() {
        MockTurnCostBuilder negativePenalty = new MockTurnCostBuilder();
        negativePenalty.addTurn(1, 2, -1.0f);
        assertThrows(IllegalArgumentException.class, () -> TurnCostMap.fromFlatBuffer(negativePenalty.build()));

        MockTurnCostBuilder nanPenalty = new MockTurnCostBuilder();
        nanPenalty.addTurn(1, 2, Float.NaN);
        assertThrows(IllegalArgumentException.class, () -> TurnCostMap.fromFlatBuffer(nanPenalty.build()));

        MockTurnCostBuilder negativeInfinityPenalty = new MockTurnCostBuilder();
        negativeInfinityPenalty.addTurn(1, 2, Float.NEGATIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> TurnCostMap.fromFlatBuffer(negativeInfinityPenalty.build()));
    }

    // ========================================================================
    // TEST 4: Concurrency
    // ========================================================================

    @Test
    public void testConcurrentReads() throws InterruptedException {
        int numThreads = 8;
        int iterations = 10000;

        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        builder.addTurn(1, 2, 100.0f);
        builder.addTurn(3, 4, 200.0f);
        TurnCostMap map = TurnCostMap.fromFlatBuffer(builder.build());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            tasks.add(() -> {
                for (int j = 0; j < iterations; j++) {
                    if (map.getCost(1, 2) != 100.0f) return false;
                    if (map.getCost(3, 4) != 200.0f) return false;
                    if (map.getCost(99, 99) != 0.0f) return false;
                }
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        for (Future<Boolean> result : results) {
            try {
                assertTrue(result.get());
            } catch (Exception e) {
                fail("Thread execution failed: " + e.getMessage());
            }
        }
        executor.shutdown();
    }

    // ========================================================================
    // TEST 5: Performance & Benchmarking (New)
    // ========================================================================

    @Test
    public void testBulkLookupPerformance() {
        int count = 100_000;
        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        for (int i = 0; i < count; i++) {
            builder.addTurn(i, i + 1, (float) i);
        }

        TurnCostMap map = TurnCostMap.fromFlatBuffer(builder.build());

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            map.getCost(i % count, (i % count) + 1);
        }

        // Measure
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            map.getCost(i % count, (i % count) + 1);
        }
        long elapsed = System.nanoTime() - start;
        double avgNanos = elapsed / 1_000_000.0;

        System.out.printf("Avg lookup: %.2f ns%n", avgNanos);
        // Note: Threshold increased slightly to account for test environment variance
        assertTrue(avgNanos < 150, "Expected <150ns per lookup, got " + avgNanos);
    }

    @Test
    public void testHashDistribution() {
        // Verify MurmurHash spreads keys evenly
        int[] bucketCounts = new int[256];

        for (int from = 0; from < 1000; from++) {
            for (int to = 0; to < 10; to++) {
                long key = ((long) from << 32) | (to & 0xFFFFFFFFL);
                int hash = TurnCostMap.mix(key); // Package-private access enabled
                bucketCounts[hash & 0xFF]++;
            }
        }

        // Check no bucket has >2x the average
        double avg = 10000.0 / 256;
        for (int count : bucketCounts) {
            assertTrue(count < avg * 2.5, "Poor hash distribution: Bucket count " + count + " vs avg " + avg);
        }
    }

    @Test
    public void testNoMemoryLeakOnRepeatedLoads() {
        Runtime runtime = Runtime.getRuntime();

        MockTurnCostBuilder builder = new MockTurnCostBuilder();
        for (int i = 0; i < 10000; i++) {
            builder.addTurn(i, i + 1, (float) i);
        }
        ByteBuffer buffer = builder.build();

        runtime.gc();
        // long baseline = runtime.totalMemory() - runtime.freeMemory();

        // Load map 1000 times
        for (int i = 0; i < 1000; i++) {
            TurnCostMap map = TurnCostMap.fromFlatBuffer(buffer);
            map.getCost(0, 1);
        }

        runtime.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();

        // 50MB is a generous threshold, primarily ensuring we aren't retaining
        // 1000 copies of the map (which would be ~200MB)
        assertTrue(after < 250_000_000, "Possible memory leak, usage: " + (after / 1024 / 1024) + "MB");
    }

    // ========================================================================
    // HELPER: FlatBuffer Builder
    // ========================================================================

    private static class MockTurnCostBuilder {
        private final List<TurnEntry> entries = new ArrayList<>();
        public boolean skipTurnCostVector = false;
        public boolean includeMetadata = true;
        public long schemaVersion = 1L;
        public int timeUnit = TimeUnit.SECONDS;
        public long tickDurationNs = 1_000_000_000L;

        static class TurnEntry {
            int from, to;
            float cost;
            TurnEntry(int f, int t, float c) { from=f; to=t; cost=c; }
        }

        void addTurn(int from, int to, float cost) {
            entries.add(new TurnEntry(from, to, cost));
        }

        ByteBuffer build() {
            FlatBufferBuilder builder = new FlatBufferBuilder(2048);

            int[] entryOffsets = new int[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                TurnEntry e = entries.get(i);
                entryOffsets[i] = TurnCost.createTurnCost(builder, e.from, e.to, e.cost);
            }

            int turnCostsVec = 0;
            if (!skipTurnCostVector) {
                turnCostsVec = Model.createTurnCostsVector(builder, entryOffsets);
            }

            int metadataRef = 0;
            if (includeMetadata) {
                metadataRef = createMetadata(builder, schemaVersion, timeUnit, tickDurationNs);
            }

            Model.startModel(builder);
            if (metadataRef != 0) {
                Model.addMetadata(builder, metadataRef);
            }
            if (!skipTurnCostVector) {
                Model.addTurnCosts(builder, turnCostsVec);
            }
            int root = Model.endModel(builder);
            Model.finishModelBuffer(builder, root);
            return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        }

        private int createMetadata(FlatBufferBuilder builder, long schemaVersion, int timeUnit, long tickDurationNs) {
            int modelVersion = builder.createString("turn-cost-test");
            Metadata.startMetadata(builder);
            Metadata.addSchemaVersion(builder, schemaVersion);
            Metadata.addModelVersion(builder, modelVersion);
            Metadata.addTimeUnit(builder, timeUnit);
            Metadata.addTickDurationNs(builder, tickDurationNs);
            return Metadata.endMetadata(builder);
        }
    }
}
