package org.Aayush.routing.topology;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.serialization.flatbuffers.ModelContractValidator;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Topology Model Compiler Tests")
class TopologyModelCompilerTest {

    @Test
    @DisplayName("Minimal coordinate-free compile retains metadata and omits spatial index cleanly")
    void testCompiledMinimalModelRetainsMetadataWithoutSpatialIndex() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("minimal-compile")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N0").build())
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N1").build())
                .edge(TopologyModelSource.EdgeDefinition.builder()
                        .edgeId("E01")
                        .originNodeId("N0")
                        .destinationNodeId("N1")
                        .baseWeight(1.0f)
                        .profileId(1)
                        .build())
                .build();

        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        ByteBuffer buffer = compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        Model model = Model.getRootAsModel(buffer);
        Metadata metadata = model.metadata();
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        SpatialRuntime spatialRuntime = SpatialRuntime.fromFlatBuffer(
                compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN),
                graph,
                false
        );

        assertFalse(compiled.isCoordinatesEnabled());
        assertEquals(TimeUtils.EngineTimeUnit.SECONDS, ModelContractValidator.validateMetadataContract(model, "TopologyModelCompilerTest"));
        assertEquals("minimal-compile", metadata.modelVersion());
        assertEquals("UTC", metadata.profileTimezone());
        assertEquals(1_000_000_000L, metadata.tickDurationNs());
        assertNull(model.spatialIndex());
        assertFalse(spatialRuntime.enabled());
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
    }

    @Test
    @DisplayName("Compiled coordinate-rich models contain a real KD tree with brute-force nearest parity")
    void testCompiledSpatialIndexBuildsBalancedTree() {
        TopologyModelSource source = TopologyTestFixtures.gridSource(6, 6);
        TopologyModelCompiler compiler = new TopologyModelCompiler();

        CompiledTopologyModel compiled = compiler.compile(source);
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        SpatialRuntime spatialRuntime = SpatialRuntime.fromFlatBuffer(
                compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN),
                graph,
                true
        );

        assertTrue(spatialRuntime.enabled());
        assertTrue(spatialRuntime.treeNodeCount() > 1, "compiled reload models should not degrade to a single-leaf spatial index");

        double[][] queries = {
                {0.1d, 0.2d},
                {2.4d, 3.6d},
                {4.9d, 1.2d},
                {5.4d, 5.1d}
        };
        for (double[] query : queries) {
            assertEquals(bruteForceNearest(graph, query[0], query[1]), spatialRuntime.nearestNodeId(query[0], query[1]));
        }
    }

    @Test
    @DisplayName("Skewed coordinate distributions still rebuild a balanced KD tree with nearest parity")
    void testCompiledSkewedSpatialIndexBuildsBalancedTree() {
        TopologyModelSource source = TopologyTestFixtures.gridSource(1, 32);
        TopologyModelCompiler compiler = new TopologyModelCompiler();

        CompiledTopologyModel compiled = compiler.compile(source);
        EdgeGraph graph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        SpatialRuntime spatialRuntime = SpatialRuntime.fromFlatBuffer(
                compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN),
                graph,
                true
        );

        assertTrue(spatialRuntime.enabled());
        assertTrue(spatialRuntime.treeNodeCount() > 1, "skewed coordinate rebuilds should still emit a multi-node KD tree");

        double[][] queries = {
                {0.0d, 0.1d},
                {0.0d, 7.6d},
                {0.0d, 15.4d},
                {0.0d, 30.8d}
        };
        for (double[] query : queries) {
            assertEquals(bruteForceNearest(graph, query[0], query[1]), spatialRuntime.nearestNodeId(query[0], query[1]));
        }
    }

    private int bruteForceNearest(EdgeGraph graph, double queryX, double queryY) {
        int bestNode = -1;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (int nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
            double dx = graph.getNodeX(nodeId) - queryX;
            double dy = graph.getNodeY(nodeId) - queryY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < bestDistanceSquared
                    || (distanceSquared == bestDistanceSquared && (bestNode < 0 || nodeId < bestNode))) {
                bestDistanceSquared = distanceSquared;
                bestNode = nodeId;
            }
        }
        return bestNode;
    }
}
