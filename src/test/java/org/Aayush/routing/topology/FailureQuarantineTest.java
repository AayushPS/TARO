package org.Aayush.routing.topology;

import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Failure Quarantine Tests")
class FailureQuarantineTest {

    @Test
    @DisplayName("Node quarantine blocks incident edges and edge quarantine remains independently addressable")
    void testNodeAndEdgeQuarantineExpansion() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
        FailureQuarantine quarantine = new FailureQuarantine("runtime-a");
        quarantine.quarantineNode(1, 80L, 10L, "node_down", "ops");
        quarantine.quarantineEdge(3, 120L, 20L, "edge_down", "ops");

        FailureQuarantine.Snapshot snapshot = quarantine.snapshot(0L);
        List<LiveUpdate> updates = snapshot.toLiveUpdates(fixture.edgeGraph());

        assertEquals(1, snapshot.activeNodeFailureCount());
        assertEquals(1, snapshot.activeEdgeFailureCount());
        assertEquals(20L, snapshot.mostRecentObservedAtTicks());
        assertEquals(List.of("edge_down", "node_down", "ops"), snapshot.explanationTags());
        assertIterableEquals(List.of(0, 2, 3), updates.stream().map(LiveUpdate::edgeId).toList());
        assertEquals(80L, updates.get(0).validUntilTicks());
        assertEquals(80L, updates.get(1).validUntilTicks());
        assertEquals(120L, updates.get(2).validUntilTicks());

        List<LiveUpdate> clipped = snapshot.toLiveUpdates(fixture.edgeGraph(), 50L);
        assertTrue(clipped.stream().allMatch(update -> update.validUntilTicks() == 50L));

        FailureQuarantine.Snapshot afterNodeExpiry = quarantine.snapshot(90L);
        List<LiveUpdate> remaining = afterNodeExpiry.toLiveUpdates(fixture.edgeGraph());
        assertEquals(0, afterNodeExpiry.activeNodeFailureCount());
        assertEquals(1, remaining.size());
        assertEquals(3, remaining.get(0).edgeId());
    }

    @Test
    @DisplayName("Topology-bound quarantine supports no-graph live-update expansion and clearAll")
    void testTopologyBoundSnapshotSupportsNoGraphExpansion() {
        TopologyRuntimeSnapshot snapshot = TopologyTestFixtures.createHarness(lineSource()).currentSnapshot();
        FailureQuarantine quarantine = snapshot.getFailureQuarantine();
        quarantine.quarantineNode(1, 80L, "node_down", "ops");

        FailureQuarantine.Snapshot active = quarantine.snapshot(0L);
        assertIterableEquals(List.of(0, 1), active.toLiveUpdates().stream().map(LiveUpdate::edgeId).toList());

        quarantine.clearAll();
        FailureQuarantine.Snapshot cleared = quarantine.snapshot(0L);
        assertFalse(cleared.hasActiveFailures());
        assertTrue(cleared.toLiveUpdates().isEmpty());
    }

    @Test
    @DisplayName("Unbound node quarantine requires graph fallback for no-graph expansion")
    void testUnboundNodeQuarantineRejectsNoGraphExpansion() {
        FailureQuarantine quarantine = new FailureQuarantine("runtime-b");
        quarantine.quarantineNode(1, 80L, "node_down", "ops");

        FailureQuarantine.Snapshot snapshot = quarantine.snapshot(0L);
        assertThrows(UnsupportedOperationException.class, snapshot::toLiveUpdates);
    }

    @Test
    @DisplayName("Blank binding ids are rejected")
    void testBlankBindingIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FailureQuarantine(" "));
    }

    private TopologyModelSource lineSource() {
        return TopologyModelSource.builder()
                .modelVersion("failure-line")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(node("N0", 0.0d))
                .node(node("N1", 1.0d))
                .node(node("N2", 2.0d))
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E12", "N1", "N2"))
                .build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(0.0d)
                .build();
    }

    private TopologyModelSource.EdgeDefinition edge(String edgeId, String originNodeId, String destinationNodeId) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(1.0f)
                .profileId(1)
                .build();
    }
}
