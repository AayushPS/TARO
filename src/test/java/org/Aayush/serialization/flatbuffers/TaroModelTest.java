package org.Aayush.serialization.flatbuffers;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.serialization.flatbuffers.taro.model.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TaroModelTest {

    private static final String FILE_NAME = "taro_csr_test.taro";

    private static int createMetadata(FlatBufferBuilder builder, String description) {
        int descRef = builder.createString(description);
        int modelVersionRef = builder.createString("v10.1-test");
        int timezoneRef = builder.createString("UTC");

        Metadata.startMetadata(builder);
        Metadata.addVersion(builder, 1);
        Metadata.addDescription(builder, descRef);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersionRef);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezoneRef);
        return Metadata.endMetadata(builder);
    }

    /**
     * CRITICAL: Test CSR (Compressed Sparse Row) format
     * This is THE feature that makes routing 100,000x faster!
     */
    @Test
    public void testCSRGraphTraversal() throws Exception {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        // Build a small graph:
        // Node 0 -> [1, 2]
        // Node 1 -> [2]
        // Node 2 -> [3]
        // Node 3 -> []

        // CSR first_edge array: [0, 2, 3, 4, 4]
        int[] firstEdge = {0, 2, 3, 4, 4};
        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);

        // CSR edge_target array: [1, 2, 2, 3]
        int[] edgeTarget = {1, 2, 2, 3};
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);

        // Weights for edges
        float[] weights = {10.0f, 15.0f, 5.0f, 8.0f};
        int weightsVec = GraphTopology.createBaseWeightsVector(builder, weights);

        // Coordinates (4 nodes)
        GraphTopology.startCoordinatesVector(builder, 4);
        Coordinate.createCoordinate(builder, 40.7128, -74.0060); // Node 3
        Coordinate.createCoordinate(builder, 40.7489, -73.9680); // Node 2
        Coordinate.createCoordinate(builder, 40.7580, -73.9855); // Node 1
        Coordinate.createCoordinate(builder, 40.7614, -73.9776); // Node 0
        int coordsVec = builder.endVector();

        // Build topology
        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, 4);
        GraphTopology.addEdgeCount(builder, 4);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addBaseWeights(builder, weightsVec);
        GraphTopology.addCoordinates(builder, coordsVec);
        int topoRef = GraphTopology.endGraphTopology(builder);

        int metaRef = createMetadata(builder, "CSR Test Graph");

        // Root
        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        Model.addTopology(builder, topoRef);
        int rootRef = Model.endModel(builder);

        builder.finish(rootRef);

        // Write and read back
        try (FileOutputStream fos = new FileOutputStream(FILE_NAME)) {
            fos.write(builder.sizedByteArray());
        }

        ByteBuffer buf;
        try (FileInputStream fis = new FileInputStream(FILE_NAME);
             FileChannel channel = fis.getChannel()) {
            buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        Model model = Model.getRootAsModel(buf);
        GraphTopology topo = model.topology();

        // === TEST CSR TRAVERSAL ===

        // Test Node 0: Should have edges [0, 1]
        int node0Start = topo.firstEdge(0);
        int node0End = topo.firstEdge(1);
        assertEquals(0, node0Start);
        assertEquals(2, node0End);
        assertEquals(2, node0End - node0Start, "Node 0 should have 2 outgoing edges");

        // Check Node 0's neighbors
        int[] node0Neighbors = new int[node0End - node0Start];
        for (int i = node0Start; i < node0End; i++) {
            node0Neighbors[i - node0Start] = topo.edgeTarget(i);
        }
        assertArrayEquals(new int[]{1, 2}, node0Neighbors, "Node 0 neighbors incorrect");

        // Test Node 1: Should have edge [2]
        int node1Start = topo.firstEdge(1);
        int node1End = topo.firstEdge(2);
        assertEquals(1, node1End - node1Start, "Node 1 should have 1 outgoing edge");
        assertEquals(2, topo.edgeTarget(node1Start), "Node 1 should point to node 2");

        // Test Node 3: Should have no outgoing edges
        int node3Start = topo.firstEdge(3);
        int node3End = topo.firstEdge(4);
        assertEquals(0, node3End - node3Start, "Node 3 should have 0 outgoing edges");

        // === TEST PERFORMANCE ===
        // WARMUP: Trigger Class Loading and JIT
        // This eliminates the "cold start" penalty (100x slower) seen in the first run
        for (int i = 0; i < 10000; i++) {
            topo.firstEdge(0);
            topo.edgeTarget(0);
        }

        long start = System.nanoTime();
        for (int nodeId = 0; nodeId < 4; nodeId++) {
            int edgeStart = topo.firstEdge(nodeId);
            int edgeEnd = topo.firstEdge(nodeId + 1);
            for (int i = edgeStart; i < edgeEnd; i++) {
                int target = topo.edgeTarget(i);
                float weight = topo.baseWeights(i);
                assertNotNull(target);
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.println("CSR Traversal (4 nodes, 4 edges): " + elapsed + " ns");

        // Keep this as a smoke guardrail, not a microbenchmark gate.
        assertTrue(elapsed < 5_000_000, "CSR traversal should remain fast (<5ms) after warmup");

        // Cleanup
        new File(FILE_NAME).delete();
    }

    /**
     * Test ID Mapping with Binary Search
     */
    @Test
    public void testIDMapping() throws Exception {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        // Sorted external IDs (critical for binary search!)
        long[] externalIds = {100L, 200L, 300L, 400L, 500L};
        int extIdsVec = IdMapping.createExternalIdsVector(builder, externalIds);

        IdMapping.startIdMapping(builder);
        IdMapping.addExternalIds(builder, extIdsVec);
        int mapRef = IdMapping.endIdMapping(builder);

        // Minimal model
        int metaRef = createMetadata(builder, "ID Mapping Test");

        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        Model.addIdMapping(builder, mapRef);
        int rootRef = Model.endModel(builder);

        builder.finish(rootRef);

        // Read back
        ByteBuffer buf = ByteBuffer.wrap(builder.sizedByteArray());
        Model model = Model.getRootAsModel(buf);
        IdMapping mapping = model.idMapping();

        // Test binary search functionality
        assertEquals(5, mapping.externalIdsLength());

        // Manual binary search (what you'd implement in Graph wrapper)
        long targetId = 300L;
        int internalId = -1;
        int left = 0, right = mapping.externalIdsLength() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            long midVal = mapping.externalIds(mid);
            if (midVal == targetId) {
                internalId = mid;
                break;
            } else if (midVal < targetId) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        assertEquals(2, internalId, "External ID 300 should map to internal ID 2");

        // Test missing ID
        targetId = 250L;
        internalId = -1;
        left = 0;
        right = mapping.externalIdsLength() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            long midVal = mapping.externalIds(mid);
            if (midVal == targetId) {
                internalId = mid;
                break;
            } else if (midVal < targetId) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        assertEquals(-1, internalId, "Missing external ID should return -1");
    }

    /**
     * Test Landmark Forward and Backward Distances
     */
    @Test
    public void testBidirectionalLandmarks() throws Exception {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        int nodeCount = 5;

        // Create landmark with forward and backward distances
        float[] forward = {0.0f, 10.0f, 20.0f, 30.0f, 40.0f};
        float[] backward = {0.0f, 12.0f, 22.0f, 32.0f, 42.0f};

        int fwdVec = Landmark.createForwardDistancesVector(builder, forward);
        int bwdVec = Landmark.createBackwardDistancesVector(builder, backward);

        Landmark.startLandmark(builder);
        Landmark.addNodeIdx(builder, 0); // Landmark is node 0
        Landmark.addForwardDistances(builder, fwdVec);
        Landmark.addBackwardDistances(builder, bwdVec);
        int landmarkRef = Landmark.endLandmark(builder);

        // Create landmarks vector
        Model.startLandmarksVector(builder, 1);
        builder.addOffset(landmarkRef);
        int landmarksVec = builder.endVector();

        // Minimal model
        int metaRef = createMetadata(builder, "Landmark Test");

        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        Model.addLandmarks(builder, landmarksVec);
        int rootRef = Model.endModel(builder);

        builder.finish(rootRef);

        // Read back
        ByteBuffer buf = ByteBuffer.wrap(builder.sizedByteArray());
        Model model = Model.getRootAsModel(buf);

        assertEquals(1, model.landmarksLength());
        Landmark lm = model.landmarks(0);

        // Test forward distances
        assertEquals(5, lm.forwardDistancesLength());
        assertEquals(30.0f, lm.forwardDistances(3), 0.001f);

        // Test backward distances (critical for bidirectional A*)
        assertEquals(5, lm.backwardDistancesLength());
        assertEquals(32.0f, lm.backwardDistances(3), 0.001f);

        // Verify they're different (proves it's not just one array)
        assertNotEquals(lm.forwardDistances(1), lm.backwardDistances(1), 0.001f);
    }

    /**
     * Stress Test: CSR Performance on Large Graph
     */
    @Test
    public void testCSRPerformanceStress() throws Exception {
        FlatBufferBuilder builder = new FlatBufferBuilder(10 * 1024 * 1024);

        int nodeCount = 10_000;
        int edgeCount = 50_000;

        // Generate random CSR structure
        Random rand = new Random(42);
        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTarget = new int[edgeCount];
        float[] weights = new float[edgeCount];

        // Distribute edges randomly
        int currentEdge = 0;
        for (int node = 0; node < nodeCount; node++) {
            firstEdge[node] = currentEdge;
            int degree = rand.nextInt(10); // 0-9 outgoing edges
            degree = Math.min(degree, edgeCount - currentEdge);

            for (int i = 0; i < degree; i++) {
                edgeTarget[currentEdge] = rand.nextInt(nodeCount);
                weights[currentEdge] = rand.nextFloat() * 100;
                currentEdge++;
            }
        }
        firstEdge[nodeCount] = currentEdge;

        // Build FlatBuffers
        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int weightsVec = GraphTopology.createBaseWeightsVector(builder, weights);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, currentEdge);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addBaseWeights(builder, weightsVec);
        int topoRef = GraphTopology.endGraphTopology(builder);

        int metaRef = createMetadata(builder, "Stress Test");

        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        Model.addTopology(builder, topoRef);
        int rootRef = Model.endModel(builder);

        builder.finish(rootRef);

        // Read back
        ByteBuffer buf = ByteBuffer.wrap(builder.sizedByteArray());
        Model model = Model.getRootAsModel(buf);
        GraphTopology topo = model.topology();

        // Warmup Phase for Stress Test
        for (int i = 0; i < 500; i++) {
            topo.firstEdge(rand.nextInt(nodeCount));
        }

        // Benchmark: Find neighbors of 1000 random nodes
        long start = System.nanoTime();
        int totalNeighbors = 0;

        for (int i = 0; i < 1000; i++) {
            int nodeId = rand.nextInt(nodeCount);
            int edgeStart = topo.firstEdge(nodeId);
            int edgeEnd = topo.firstEdge(nodeId + 1);

            for (int e = edgeStart; e < edgeEnd; e++) {
                int target = topo.edgeTarget(e);
                float weight = topo.baseWeights(e);
                totalNeighbors++;
            }
        }

        long elapsed = System.nanoTime() - start;
        long avgPerQuery = elapsed / 1000;

        System.out.println("CSR Stress Test Results:");
        System.out.println("  Nodes: " + nodeCount);
        System.out.println("  Edges: " + currentEdge);
        System.out.println("  Queries: 1000");
        System.out.println("  Total neighbors found: " + totalNeighbors);
        System.out.println("  Total time: " + elapsed / 1_000_000 + " ms");
        System.out.println("  Avg per query: " + avgPerQuery + " ns");

        // Performance target: <100µs per query on average
        // 2251ns (from your run) is excellent.
        assertTrue(avgPerQuery < 100_000,
                "CSR neighbor lookup should be <100µs per query (was " + avgPerQuery + " ns)");
    }

    /**
     * Test Turn Costs
     */
    @Test
    public void testTurnCosts() throws Exception {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        // Create turn costs
        TurnCost.startTurnCost(builder);
        TurnCost.addFromEdgeIdx(builder, 0);
        TurnCost.addToEdgeIdx(builder, 2);
        TurnCost.addPenaltySeconds(builder, 30.0f); // U-turn penalty
        int turn1 = TurnCost.endTurnCost(builder);

        TurnCost.startTurnCost(builder);
        TurnCost.addFromEdgeIdx(builder, 1);
        TurnCost.addToEdgeIdx(builder, 3);
        TurnCost.addPenaltySeconds(builder, -1.0f); // Forbidden turn
        int turn2 = TurnCost.endTurnCost(builder);

        // Create vector
        Model.startTurnCostsVector(builder, 2);
        builder.addOffset(turn2);
        builder.addOffset(turn1);
        int turnsVec = builder.endVector();

        // Minimal model
        int metaRef = createMetadata(builder, "Turn Cost Test");

        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        Model.addTurnCosts(builder, turnsVec);
        int rootRef = Model.endModel(builder);

        builder.finish(rootRef);

        // Read back
        ByteBuffer buf = ByteBuffer.wrap(builder.sizedByteArray());
        Model model = Model.getRootAsModel(buf);

        assertEquals(2, model.turnCostsLength());

        // Check U-turn
        TurnCost tc1 = model.turnCosts(0);
        assertEquals(0, tc1.fromEdgeIdx());
        assertEquals(2, tc1.toEdgeIdx());
        assertEquals(30.0f, tc1.penaltySeconds(), 0.001f);

        // Check forbidden turn
        TurnCost tc2 = model.turnCosts(1);
        assertEquals(-1.0f, tc2.penaltySeconds(), 0.001f);
        assertTrue(tc2.penaltySeconds() < 0, "Forbidden turns should have negative penalty");
    }

    @Test
    public void testMetadataTimeContractRoundTrip() {
        FlatBufferBuilder builder = new FlatBufferBuilder(256);
        int metaRef = createMetadata(builder, "Metadata Contract");

        Model.startModel(builder);
        Model.addMetadata(builder, metaRef);
        int rootRef = Model.endModel(builder);
        builder.finish(rootRef);

        ByteBuffer buf = ByteBuffer.wrap(builder.sizedByteArray());
        Model model = Model.getRootAsModel(buf);
        Metadata metadata = model.metadata();

        assertNotNull(metadata);
        assertEquals(1L, metadata.schemaVersion());
        assertEquals(TimeUnit.SECONDS, metadata.timeUnit());
        assertEquals(1_000_000_000L, metadata.tickDurationNs());
        assertEquals("UTC", metadata.profileTimezone());
    }
}
