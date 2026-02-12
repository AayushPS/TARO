package org.Aayush.routing.spatial;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Spatial Runtime Tests")
class SpatialRuntimeTest {

    private record KDNodeSpec(
            float splitValue,
            int leftChild,
            int rightChild,
            int itemStartIndex,
            int itemCount,
            int splitAxis,
            int isLeaf
    ) {}

    private record BuiltKDIndex(
            KDNodeSpec[] nodes,
            int[] leafItems,
            int rootIndex
    ) {}

    private record RuntimeFixture(
            EdgeGraph graph,
            SpatialRuntime runtime
    ) {}

    // =====================================================================
    // CORRECTNESS TESTS
    // =====================================================================

    @Test
    @DisplayName("Correctness: nearest lookup over serialized KD tree")
    void testNearestLookup() {
        double[] coordinates = {
                0.0, 0.0,   // node 0
                0.0, 10.0,  // node 1
                10.0, 0.0,  // node 2
                10.0, 10.0  // node 3
        };

        KDNodeSpec[] nodes = {
                new KDNodeSpec(5.0f, 1, 2, 0, 0, 0, 0),
                new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1),
                new KDNodeSpec(0.0f, -1, -1, 2, 2, 0, 1)
        };
        int[] leafItems = {0, 1, 2, 3};

        ByteBuffer model = buildModelBuffer(coordinates, nodes, leafItems, 0, true);
        EdgeGraph graph = loadGraph(model);
        SpatialRuntime runtime = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, true);

        assertTrue(runtime.enabled());
        assertEquals(3, runtime.treeNodeCount());
        assertEquals(4, runtime.leafItemCount());

        SpatialMatch q1 = runtime.nearest(9.0, 9.0);
        assertEquals(3, q1.nodeId());
        assertEquals(2.0, q1.distanceSquared(), 1e-9);

        SpatialMatch q2 = runtime.nearest(0.2, 9.6);
        assertEquals(1, q2.nodeId());

        SpatialMatch q3 = runtime.nearest(9.6, 0.3);
        assertEquals(2, q3.nodeId());
    }

    @Test
    @DisplayName("Correctness: deterministic tie-break picks lower node id")
    void testDeterministicTieBreak() {
        double[] coordinates = {
                0.0, 0.0, // node 0
                0.0, 2.0  // node 1
        };
        KDNodeSpec[] nodes = {
                new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)
        };
        int[] leafItems = {1, 0}; // Reverse order; tie should still pick node 0.

        ByteBuffer model = buildModelBuffer(coordinates, nodes, leafItems, 0, true);
        EdgeGraph graph = loadGraph(model);
        SpatialRuntime runtime = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, true);

        assertEquals(0, runtime.nearestNodeId(0.0, 1.0));
    }

    @Test
    @DisplayName("Correctness: runtime can be disabled via trait gate")
    void testDisabledRuntime() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };
        KDNodeSpec[] nodes = {
                new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)
        };
        int[] leafItems = {0, 1};

        ByteBuffer model = buildModelBuffer(coordinates, nodes, leafItems, 0, true);
        EdgeGraph graph = loadGraph(model);
        SpatialRuntime runtime = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, false);

        assertFalse(runtime.enabled());
        assertEquals(0, runtime.treeNodeCount());
        assertEquals(0, runtime.leafItemCount());

        assertThrows(UnsupportedOperationException.class, () -> runtime.nearestNodeId(0.0, 0.0));
    }

    @Test
    @DisplayName("Correctness: two-arg loader defaults to enabled runtime")
    void testTwoArgLoaderDefaultsToEnabled() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };
        KDNodeSpec[] nodes = {
                new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)
        };
        int[] leafItems = {0, 1};

        ByteBuffer model = buildModelBuffer(coordinates, nodes, leafItems, 0, true);
        EdgeGraph graph = loadGraph(model);
        SpatialRuntime runtime = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph);

        assertTrue(runtime.enabled());
        assertEquals(1, runtime.treeNodeCount());
        assertEquals(2, runtime.leafItemCount());
    }

    @Test
    @DisplayName("Correctness: toString reports enabled state and sizes")
    void testToStringContainsRuntimeShape() {
        RuntimeFixture fixture = buildFixture(new double[]{
                0.0, 0.0,
                1.0, 1.0,
                2.0, 2.0
        }, 2);
        String text = fixture.runtime().toString();

        assertTrue(text.contains("enabled=true"));
        assertTrue(text.contains("treeNodes="));
        assertTrue(text.contains("leafItems="));
    }

    @Test
    @DisplayName("Correctness: enabled runtime rejects missing spatial index")
    void testMissingSpatialIndexRejectedWhenEnabled() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };
        ByteBuffer model = buildModelBuffer(coordinates, null, null, 0, false);
        EdgeGraph graph = loadGraph(model);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, true)
        );
        assertTrue(ex.getMessage().contains("spatial_index"));

        SpatialRuntime disabled = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, false);
        assertFalse(disabled.enabled());
    }

    @Test
    @DisplayName("Correctness: missing metadata contract is rejected")
    void testMissingMetadataRejected() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };
        KDNodeSpec[] nodes = {
                new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)
        };
        int[] leafItems = {0, 1};

        ByteBuffer validModel = buildModelBuffer(coordinates, nodes, leafItems, 0, true);
        EdgeGraph graph = loadGraph(validModel);
        ByteBuffer modelWithoutMetadata = buildModelBufferWithoutMetadata(coordinates, nodes, leafItems, 0, true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SpatialRuntime.fromFlatBuffer(modelWithoutMetadata.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, true)
        );
        assertTrue(ex.getMessage().contains("metadata"));
    }

    @Test
    @DisplayName("Correctness: loader rejects malformed KD contracts")
    void testMalformedKdContracts() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };

        // root_index out of bounds
        ByteBuffer badRoot = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)},
                new int[]{0, 1},
                7,
                true
        );
        EdgeGraph badRootGraph = loadGraph(badRoot);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badRoot.duplicate().order(ByteOrder.LITTLE_ENDIAN), badRootGraph, true));

        // invalid split axis
        ByteBuffer badAxis = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 2, 2, 1)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badAxisGraph = loadGraph(badAxis);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badAxis.duplicate().order(ByteOrder.LITTLE_ENDIAN), badAxisGraph, true));

        // leaf span overflow
        ByteBuffer badSpan = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 3, 0, 1)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badSpanGraph = loadGraph(badSpan);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badSpan.duplicate().order(ByteOrder.LITTLE_ENDIAN), badSpanGraph, true));

        // leaf item out of node-id bounds
        ByteBuffer badLeafItem = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 1)},
                new int[]{0, 5},
                0,
                true
        );
        EdgeGraph badLeafItemGraph = loadGraph(badLeafItem);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badLeafItem.duplicate().order(ByteOrder.LITTLE_ENDIAN), badLeafItemGraph, true));

        // invalid is_leaf flag
        ByteBuffer badLeafFlag = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 2, 0, 2)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badLeafFlagGraph = loadGraph(badLeafFlag);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badLeafFlag.duplicate().order(ByteOrder.LITTLE_ENDIAN), badLeafFlagGraph, true));

        // leaf nodes must not have child links
        ByteBuffer badLeafChildren = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, 0, -1, 0, 2, 0, 1)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badLeafChildrenGraph = loadGraph(badLeafChildren);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badLeafChildren.duplicate().order(ByteOrder.LITTLE_ENDIAN), badLeafChildrenGraph, true));

        // internal nodes must have at least one child
        ByteBuffer badInternalNoChildren = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 0, 0, 0)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badInternalNoChildrenGraph = loadGraph(badInternalNoChildren);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(
                        badInternalNoChildren.duplicate().order(ByteOrder.LITTLE_ENDIAN),
                        badInternalNoChildrenGraph,
                        true
                ));

        // child indices must be in bounds (or -1)
        ByteBuffer badChildOutOfBounds = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, 3, -1, 0, 0, 0, 0)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badChildOutOfBoundsGraph = loadGraph(badChildOutOfBounds);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(
                        badChildOutOfBounds.duplicate().order(ByteOrder.LITTLE_ENDIAN),
                        badChildOutOfBoundsGraph,
                        true
                ));

        // child indices below -1 are invalid
        ByteBuffer badChildNegative = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -2, -1, 0, 0, 0, 0)},
                new int[]{0, 1},
                0,
                true
        );
        EdgeGraph badChildNegativeGraph = loadGraph(badChildNegative);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(
                        badChildNegative.duplicate().order(ByteOrder.LITTLE_ENDIAN),
                        badChildNegativeGraph,
                        true
                ));

        // reachable tree cannot share a child (must be a strict tree)
        ByteBuffer badSharedChild = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{
                        new KDNodeSpec(0.0f, 1, 1, 0, 0, 0, 0),
                        new KDNodeSpec(0.0f, -1, -1, 0, 1, 0, 1)
                },
                new int[]{0},
                0,
                true
        );
        EdgeGraph badSharedChildGraph = loadGraph(badSharedChild);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(badSharedChild.duplicate().order(ByteOrder.LITTLE_ENDIAN), badSharedChildGraph, true));

        // tree must expose at least one reachable payload item
        ByteBuffer badReachablePayload = buildModelBuffer(
                coordinates,
                new KDNodeSpec[]{new KDNodeSpec(0.0f, -1, -1, 0, 0, 0, 1)},
                new int[]{0},
                0,
                true
        );
        EdgeGraph badReachablePayloadGraph = loadGraph(badReachablePayload);
        assertThrows(IllegalArgumentException.class, () ->
                SpatialRuntime.fromFlatBuffer(
                        badReachablePayload.duplicate().order(ByteOrder.LITTLE_ENDIAN),
                        badReachablePayloadGraph,
                        true
                ));
    }

    @Test
    @DisplayName("Correctness: randomized KD parity with brute-force nearest")
    void testRandomizedParityWithBruteForce() {
        int nodeCount = 2_000;
        double[] coordinates = randomCoordinates(nodeCount, 42L, 0.0, 10_000.0);
        RuntimeFixture fixture = buildFixture(coordinates, 16);

        Random queryRandom = new Random(99L);
        for (int i = 0; i < 2_500; i++) {
            double queryX = queryRandom.nextDouble(0.0, 10_000.0);
            double queryY = queryRandom.nextDouble(0.0, 10_000.0);

            int expected = bruteForceNearest(fixture.graph(), queryX, queryY);
            int actual = fixture.runtime().nearestNodeId(queryX, queryY);
            assertEquals(expected, actual, "Mismatch at query index " + i);
        }
    }

    @Test
    @DisplayName("Correctness: query input validation rejects non-finite coordinates")
    void testQueryInputValidation() {
        double[] coordinates = {
                0.0, 0.0,
                1.0, 1.0
        };
        RuntimeFixture fixture = buildFixture(coordinates, 2);

        assertThrows(IllegalArgumentException.class, () -> fixture.runtime().nearestNodeId(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> fixture.runtime().nearestNodeId(0.0, Double.POSITIVE_INFINITY));
    }

    // =====================================================================
    // PERFORMANCE TEST (SMOKE GUARDRAIL)
    // =====================================================================

    @Test
    @DisplayName("Performance: nearest lookup remains fast after warmup")
    void testNearestLookupPerformanceSmoke() {
        int nodeCount = 10_000;
        int warmupQueries = 10_000;
        int measuredQueries = 30_000;

        double[] coordinates = randomCoordinates(nodeCount, 2026L, 0.0, 10_000.0);
        RuntimeFixture fixture = buildFixture(coordinates, 16);
        SpatialRuntime runtime = fixture.runtime();

        Random warmupRandom = new Random(11L);
        for (int i = 0; i < warmupQueries; i++) {
            double qx = warmupRandom.nextDouble(0.0, 10_000.0);
            double qy = warmupRandom.nextDouble(0.0, 10_000.0);
            runtime.nearestNodeId(qx, qy);
        }

        Random measureRandom = new Random(12L);
        long start = System.nanoTime();
        for (int i = 0; i < measuredQueries; i++) {
            double qx = measureRandom.nextDouble(0.0, 10_000.0);
            double qy = measureRandom.nextDouble(0.0, 10_000.0);
            int nodeId = runtime.nearestNodeId(qx, qy);
            assertTrue(nodeId >= 0 && nodeId < nodeCount);
        }
        long elapsedNs = System.nanoTime() - start;

        System.out.println("Spatial KD nearest (" + measuredQueries + " queries): " + elapsedNs + " ns");
        // Smoke guardrail only. Keep loose to avoid hardware-dependent flakiness.
        assertTrue(elapsedNs < 4_000_000_000L, "Nearest lookup should remain under 4s for smoke perf guardrail");
    }

    // =====================================================================
    // STRESS / CONCURRENCY TEST
    // =====================================================================

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Stress: concurrent nearest lookups remain stable")
    void testConcurrentReadStress() throws InterruptedException {
        int nodeCount = 8_000;
        double[] coordinates = randomCoordinates(nodeCount, 555L, 0.0, 10_000.0);
        RuntimeFixture fixture = buildFixture(coordinates, 16);
        SpatialRuntime runtime = fixture.runtime();
        EdgeGraph graph = fixture.graph();

        int threads = 8;
        int queriesPerThread = 25_000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int seed = 1_000 + t;
            executor.execute(() -> {
                try {
                    Random random = new Random(seed);
                    for (int i = 1; i <= queriesPerThread; i++) {
                        double qx = random.nextDouble(0.0, 10_000.0);
                        double qy = random.nextDouble(0.0, 10_000.0);
                        int nodeId = runtime.nearestNodeId(qx, qy);

                        if (nodeId < 0 || nodeId >= nodeCount) {
                            failed.set(true);
                            break;
                        }

                        if (i % 5_000 == 0) {
                            int brute = bruteForceNearest(graph, qx, qy);
                            if (brute != nodeId) {
                                failed.set(true);
                                break;
                            }
                        }
                    }
                } catch (Throwable t1) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Concurrent stress test timed out");
        executor.shutdownNow();
        assertFalse(failed.get(), "Concurrent nearest lookups produced invalid or inconsistent results");
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private RuntimeFixture buildFixture(double[] coordinates, int leafSize) {
        BuiltKDIndex index = buildBalancedKdIndex(coordinates, leafSize);
        ByteBuffer model = buildModelBuffer(coordinates, index.nodes(), index.leafItems(), index.rootIndex(), true);
        EdgeGraph graph = loadGraph(model);
        SpatialRuntime runtime = SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), graph, true);
        return new RuntimeFixture(graph, runtime);
    }

    private EdgeGraph loadGraph(ByteBuffer modelBuffer) {
        return EdgeGraph.fromFlatBuffer(modelBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
    }

    private double[] randomCoordinates(int nodeCount, long seed, double min, double max) {
        Random random = new Random(seed);
        double[] coordinates = new double[nodeCount * 2];
        for (int i = 0; i < nodeCount; i++) {
            coordinates[i * 2] = random.nextDouble(min, max);
            coordinates[i * 2 + 1] = random.nextDouble(min, max);
        }
        return coordinates;
    }

    private int bruteForceNearest(EdgeGraph graph, double queryX, double queryY) {
        int bestNode = -1;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;

        for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
            double dx = graph.getNodeX(nodeId) - queryX;
            double dy = graph.getNodeY(nodeId) - queryY;
            double distanceSquared = dx * dx + dy * dy;

            if (distanceSquared < bestDistanceSquared
                    || (distanceSquared == bestDistanceSquared
                    && (bestNode < 0 || nodeId < bestNode))) {
                bestDistanceSquared = distanceSquared;
                bestNode = nodeId;
            }
        }
        return bestNode;
    }

    private BuiltKDIndex buildBalancedKdIndex(double[] coordinates, int leafSize) {
        if (coordinates == null || coordinates.length == 0 || (coordinates.length & 1) != 0) {
            throw new IllegalArgumentException("coordinates must be non-empty lat/lon pairs");
        }
        if (leafSize <= 0) {
            throw new IllegalArgumentException("leafSize must be > 0");
        }

        int nodeCount = coordinates.length / 2;
        int[] nodeIds = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            nodeIds[i] = i;
        }

        KDBuilder builder = new KDBuilder(coordinates, leafSize);
        int root = builder.build(nodeIds, 0);
        return new BuiltKDIndex(
                builder.nodes.toArray(new KDNodeSpec[0]),
                builder.toLeafItemsArray(),
                root
        );
    }

    private ByteBuffer buildModelBuffer(
            double[] coordinates,
            KDNodeSpec[] treeNodes,
            int[] leafItems,
            int rootIndex,
            boolean includeSpatialIndex
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int nodeCount = coordinates.length / 2;
        int edgeCount = 0;

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTargets = new int[0];

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTargets);
        int coordinatesVec = createCoordinatesVector(builder, coordinates);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeCount);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addCoordinates(builder, coordinatesVec);
        int topologyRef = GraphTopology.endGraphTopology(builder);

        int spatialIndexRef = 0;
        if (includeSpatialIndex) {
            int treeNodesVec = 0;
            int leafItemsVec = 0;

            if (treeNodes != null) {
                SpatialIndex.startTreeNodesVector(builder, treeNodes.length);
                for (int i = treeNodes.length - 1; i >= 0; i--) {
                    KDNodeSpec node = treeNodes[i];
                    KDNode.createKDNode(
                            builder,
                            node.splitValue(),
                            node.leftChild(),
                            node.rightChild(),
                            node.itemStartIndex(),
                            node.itemCount(),
                            node.splitAxis(),
                            node.isLeaf()
                    );
                }
                treeNodesVec = builder.endVector();
            }

            if (leafItems != null) {
                leafItemsVec = SpatialIndex.createLeafItemsVector(builder, leafItems);
            }

            SpatialIndex.startSpatialIndex(builder);
            if (treeNodesVec != 0) {
                SpatialIndex.addTreeNodes(builder, treeNodesVec);
            }
            if (leafItemsVec != 0) {
                SpatialIndex.addLeafItems(builder, leafItemsVec);
            }
            SpatialIndex.addRootIndex(builder, rootIndex);
            spatialIndexRef = SpatialIndex.endSpatialIndex(builder);
        }

        int metadataRef = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataRef);
        Model.addTopology(builder, topologyRef);
        if (spatialIndexRef != 0) {
            Model.addSpatialIndex(builder, spatialIndexRef);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private ByteBuffer buildModelBufferWithoutMetadata(
            double[] coordinates,
            KDNodeSpec[] treeNodes,
            int[] leafItems,
            int rootIndex,
            boolean includeSpatialIndex
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int nodeCount = coordinates.length / 2;
        int edgeCount = 0;

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTargets = new int[0];

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTargets);
        int coordinatesVec = createCoordinatesVector(builder, coordinates);

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeCount);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addCoordinates(builder, coordinatesVec);
        int topologyRef = GraphTopology.endGraphTopology(builder);

        int spatialIndexRef = 0;
        if (includeSpatialIndex) {
            int treeNodesVec = 0;
            int leafItemsVec = 0;

            if (treeNodes != null) {
                SpatialIndex.startTreeNodesVector(builder, treeNodes.length);
                for (int i = treeNodes.length - 1; i >= 0; i--) {
                    KDNodeSpec node = treeNodes[i];
                    KDNode.createKDNode(
                            builder,
                            node.splitValue(),
                            node.leftChild(),
                            node.rightChild(),
                            node.itemStartIndex(),
                            node.itemCount(),
                            node.splitAxis(),
                            node.isLeaf()
                    );
                }
                treeNodesVec = builder.endVector();
            }

            if (leafItems != null) {
                leafItemsVec = SpatialIndex.createLeafItemsVector(builder, leafItems);
            }

            SpatialIndex.startSpatialIndex(builder);
            if (treeNodesVec != 0) {
                SpatialIndex.addTreeNodes(builder, treeNodesVec);
            }
            if (leafItemsVec != 0) {
                SpatialIndex.addLeafItems(builder, leafItemsVec);
            }
            SpatialIndex.addRootIndex(builder, rootIndex);
            spatialIndexRef = SpatialIndex.endSpatialIndex(builder);
        }

        Model.startModel(builder);
        Model.addTopology(builder, topologyRef);
        if (spatialIndexRef != 0) {
            Model.addSpatialIndex(builder, spatialIndexRef);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("spatial-test");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }

    private int createCoordinatesVector(FlatBufferBuilder builder, double[] coordinates) {
        int count = coordinates.length / 2;
        GraphTopology.startCoordinatesVector(builder, count);
        for (int i = count - 1; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    coordinates[i * 2],
                    coordinates[i * 2 + 1]
            );
        }
        return builder.endVector();
    }

    private static final class KDBuilder {
        private final double[] coordinates;
        private final int leafSize;
        private final List<KDNodeSpec> nodes = new ArrayList<>();
        private final List<Integer> leafItems = new ArrayList<>();

        private KDBuilder(double[] coordinates, int leafSize) {
            this.coordinates = coordinates;
            this.leafSize = leafSize;
        }

        private int build(int[] pointIds, int depth) {
            if (pointIds.length <= leafSize) {
                int start = leafItems.size();
                for (int id : pointIds) {
                    leafItems.add(id);
                }
                int leafNodeIndex = nodes.size();
                nodes.add(new KDNodeSpec(
                        0.0f,
                        -1,
                        -1,
                        start,
                        pointIds.length,
                        depth & 1,
                        1
                ));
                return leafNodeIndex;
            }

            int axis = depth & 1;
            Integer[] sorted = new Integer[pointIds.length];
            for (int i = 0; i < pointIds.length; i++) {
                sorted[i] = pointIds[i];
            }
            Arrays.sort(sorted, Comparator.comparingDouble(id -> coordinate(id, axis)));

            int mid = sorted.length >>> 1;
            float splitValue = (float) coordinate(sorted[mid], axis);

            int[] leftIds = new int[mid];
            int[] rightIds = new int[sorted.length - mid];

            for (int i = 0; i < mid; i++) {
                leftIds[i] = sorted[i];
            }
            for (int i = mid; i < sorted.length; i++) {
                rightIds[i - mid] = sorted[i];
            }

            int nodeIndex = nodes.size();
            nodes.add(new KDNodeSpec(splitValue, -1, -1, 0, 0, axis, 0));

            int leftChild = build(leftIds, depth + 1);
            int rightChild = build(rightIds, depth + 1);

            nodes.set(nodeIndex, new KDNodeSpec(splitValue, leftChild, rightChild, 0, 0, axis, 0));
            return nodeIndex;
        }

        private double coordinate(int nodeId, int axis) {
            int base = nodeId * 2;
            return axis == 0 ? coordinates[base] : coordinates[base + 1];
        }

        private int[] toLeafItemsArray() {
            int[] result = new int[leafItems.size()];
            for (int i = 0; i < leafItems.size(); i++) {
                result[i] = leafItems.get(i);
            }
            return result;
        }
    }
}
