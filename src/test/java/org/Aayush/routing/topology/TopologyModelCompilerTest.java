package org.Aayush.routing.topology;

import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Topology Model Compiler Tests")
class TopologyModelCompilerTest {

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
