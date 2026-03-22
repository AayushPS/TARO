package org.Aayush.routing.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Profile FIFO Repair Gate Tests")
@Tag("smoke")
class ProfileFifoRepairGateTest {

    @Test
    @DisplayName("Rejects FIFO-violating profile on the final directed edge representation")
    void testRejectsFifoViolatingDirectedEdge() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyTestFixtures.runtimeTemplate().toBuilder().bucketSizeSeconds(10).build()
        );
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b2-fifo-violating-edge")
                .profileTimezone("UTC")
                .profile(profile(1, 3.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 4.0f, 1))
                .edge(edge("E10", "N1", "N0", 8.0f, 1))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> runtimeFactory.buildSnapshot(source, TopologyTestFixtures.topologyVersion("b2-fifo"), 0L, null));
        assertTrue(ex.getMessage().contains("E10"));
        assertTrue(ex.getMessage().contains("violates FIFO"));
    }

    @Test
    @DisplayName("Accepts edge profile whose boundary drop is exactly FIFO-neutral")
    void testAcceptsBoundaryNeutralArrivalTimeline() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyTestFixtures.runtimeTemplate().toBuilder().bucketSizeSeconds(10).build()
        );
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b2-fifo-boundary-valid")
                .profileTimezone("UTC")
                .profile(profile(1, 2.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 10.0f, 1))
                .build();

        assertDoesNotThrow(() -> runtimeFactory.buildSnapshot(
                source,
                TopologyTestFixtures.topologyVersion("b2-fifo-valid"),
                0L,
                null
        ));
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId, float... buckets) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .multiplier(1.0f);
        for (float bucket : buckets) {
            builder.bucket(bucket);
        }
        return builder.build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
                .build();
    }

    private TopologyModelSource.EdgeDefinition edge(
            String edgeId,
            String originNodeId,
            String destinationNodeId,
            float baseWeight,
            int profileId
    ) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(baseWeight)
                .profileId(profileId)
                .build();
    }
}
