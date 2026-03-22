package org.Aayush.routing.topology;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Profile Contract Perf Tests")
@Tag("perf")
class ProfileContractPerfTest {

    @Test
    @DisplayName("Perf smoke: directed-edge profile validation remains practical on large topology")
    void testDirectedEdgeProfileValidationThroughput() {
        TopologyModelSource source = largeGridSource(120, 120);
        TopologyModelCompiler compiler = new TopologyModelCompiler();
        CompiledTopologyModel compiled = compiler.compile(source);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));

        for (int i = 0; i < 2; i++) {
            CostEngine.validateDirectedEdgeProfileContracts(
                    edgeGraph,
                    profileStore,
                    TimeUtils.EngineTimeUnit.SECONDS,
                    3_600,
                    edgeId -> "edge " + edgeId
            );
        }

        int iterations = 5;
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CostEngine.validateDirectedEdgeProfileContracts(
                    edgeGraph,
                    profileStore,
                    TimeUtils.EngineTimeUnit.SECONDS,
                    3_600,
                    edgeId -> "edge " + edgeId
            );
        }
        long elapsedNs = System.nanoTime() - started;
        double avgMillis = elapsedNs / 1_000_000.0d / iterations;

        assertTrue(
                avgMillis < 500.0d,
                "profile validation average latency should stay below 500ms in this perf smoke, got " + avgMillis + "ms"
        );
    }

    private TopologyModelSource largeGridSource(int width, int height) {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("b2-profile-contract-perf")
                .profileTimezone("UTC")
                .profile(profile24(1, 1.0f));

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int nodeId = row * width + col;
                builder.node(TopologyModelSource.NodeDefinition.builder()
                        .nodeId("N" + nodeId)
                        .x((double) col)
                        .y((double) row)
                        .build());
            }
        }

        int edgeId = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int node = row * width + col;
                if (col + 1 < width) {
                    builder.edge(edge("E" + edgeId++, node, node + 1));
                    builder.edge(edge("E" + edgeId++, node + 1, node));
                }
                if (row + 1 < height) {
                    builder.edge(edge("E" + edgeId++, node, node + width));
                    builder.edge(edge("E" + edgeId++, node + width, node));
                }
            }
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition profile24(int profileId, float multiplier) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .multiplier(1.0f);
        for (int i = 0; i < 24; i++) {
            builder.bucket(multiplier);
        }
        return builder.build();
    }

    private TopologyModelSource.EdgeDefinition edge(String edgeId, int origin, int destination) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId("N" + origin)
                .destinationNodeId("N" + destination)
                .baseWeight(1.0f)
                .profileId(1)
                .build();
    }
}
