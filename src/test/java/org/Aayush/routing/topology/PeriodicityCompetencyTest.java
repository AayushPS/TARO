package org.Aayush.routing.topology;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Periodicity Competency Tests")
@Tag("smoke")
class PeriodicityCompetencyTest {

    @Test
    @DisplayName("Weekly day-mask transitions that break FIFO are rejected at snapshot build time")
    void testRejectsWeeklyBoundaryFifoViolation() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyTestFixtures.runtimeTemplate().toBuilder().bucketSizeSeconds(10).build()
        );
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b3-weekly-boundary-fifo-violation")
                .profileTimezone("UTC")
                .profile(profile(1, 0x1F, 2.0f, 2.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 20.0f, 1))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> runtimeFactory.buildSnapshot(source, TopologyTestFixtures.topologyVersion("b3-periodic-fifo"), 0L, null)
        );
        assertTrue(ex.getMessage().contains("E01"));
        assertTrue(ex.getMessage().contains("Fri/bucket 1"));
        assertTrue(ex.getMessage().contains("Sat/bucket 0"));
    }

    @Test
    @DisplayName("Safe weekly periodic transitions are accepted when later departures cannot arrive earlier")
    void testAcceptsSafeWeeklyBoundaryTransition() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyTestFixtures.runtimeTemplate().toBuilder().bucketSizeSeconds(10).build()
        );
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b3-weekly-boundary-fifo-safe")
                .profileTimezone("UTC")
                .profile(profile(1, 0x1F, 2.0f, 2.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 4.0f, 1))
                .build();

        assertDoesNotThrow(() -> runtimeFactory.buildSnapshot(
                source,
                TopologyTestFixtures.topologyVersion("b3-periodic-safe"),
                0L,
                null
        ));
    }

    @Test
    @DisplayName("Daily-only cost-engine validation preserves linear workloads with partial-day profiles")
    void testDailyOnlyValidationDoesNotRejectCalendarOnlyBoundaryIssue() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b3-linear-compatible")
                .profileTimezone("UTC")
                .profile(profile(1, 0x1F, 2.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 20.0f, 1))
                .build();
        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertDoesNotThrow(() -> new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(16),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                10,
                CostEngine.TemporalSamplingPolicy.INTERPOLATED
        ));
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId, int dayMask, float... buckets) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(dayMask)
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
