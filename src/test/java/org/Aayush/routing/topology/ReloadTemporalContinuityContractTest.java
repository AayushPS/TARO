package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.DefaultScenarioBundleResolver;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.overlay.LiveUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage D1 - reload continuity contract tests for topology-bound quarantine rebinding.
 * Satisfies closure criterion: failure snapshots remain deterministic, bounded, and reload-aware.
 */
@Tag("integration")
@DisplayName("Reload Temporal Continuity Contract Tests")
class ReloadTemporalContinuityContractTest {

    @Test
    @DisplayName("Reload preserves active failure metadata and topology-bound expansion")
    void testReloadPreservesActiveFailureMetadataAndExpansion() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(diamondSource());
        long publicationTicks = TopologyTestFixtures.FIXED_CLOCK.instant().getEpochSecond();
        long activeUntilTicks = publicationTicks + Duration.ofHours(3).toSeconds();
        long freshestObservedAtTicks = publicationTicks - 90L;

        harness.currentSnapshot().getFailureQuarantine().quarantineNode(
                1,
                activeUntilTicks,
                freshestObservedAtTicks,
                "node_down",
                "ops"
        );
        harness.currentSnapshot().getFailureQuarantine().quarantineEdge(
                3,
                activeUntilTicks,
                freshestObservedAtTicks - 30L,
                "edge_down",
                "ops"
        );

        FailureQuarantine.Snapshot before = harness.currentSnapshot().getFailureQuarantine().snapshot(publicationTicks);
        FutureRouteResultSet beforeResult = strictRouteService().evaluate(
                harness.currentSnapshot(),
                TopologyTestFixtures.futureRouteRequest("N0", "N3")
        );

        TopologyPublicationResult publicationResult = harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N4")
                                .x(3.0d)
                                .y(0.0d)
                                .build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34")
                                .originNodeId("N3")
                                .destinationNodeId("N4")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        FailureQuarantine.Snapshot after = harness.currentSnapshot().getFailureQuarantine().snapshot(publicationTicks);
        FutureRouteResultSet afterResult = strictRouteService().evaluate(
                harness.currentSnapshot(),
                TopologyTestFixtures.futureRouteRequest("N0", "N3")
        );

        assertEquals("quarantine-topo-initial:2", before.snapshotId());
        assertEquals(
                "quarantine-" + publicationResult.getTopologyVersion().getTopologyVersion() + ":2",
                after.snapshotId()
        );
        assertEquals(before.activeNodeFailureCount(), after.activeNodeFailureCount());
        assertEquals(before.activeEdgeFailureCount(), after.activeEdgeFailureCount());
        assertEquals(freshestObservedAtTicks, after.mostRecentObservedAtTicks());
        assertEquals(before.explanationTags(), after.explanationTags());
        assertEquals(edgeIds(before.toLiveUpdates()), edgeIds(after.toLiveUpdates()));
        assertEquals(validUntilTicks(before.toLiveUpdates()), validUntilTicks(after.toLiveUpdates()));
        assertFalse(beforeResult.getExpectedRoute().getRoute().isReachable());
        assertFalse(afterResult.getExpectedRoute().getRoute().isReachable());
    }

    @Test
    @DisplayName("Reload skips failures already expired at publication time")
    void testReloadSkipsFailuresExpiredAtPublicationTick() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(TopologyTestFixtures.runtimeTemplate());
        TopologyModelSource source = diamondSource();
        TopologyRuntimeSnapshot previousSnapshot = runtimeFactory.buildSnapshot(
                source,
                TopologyTestFixtures.topologyVersion("d1-expired-prev"),
                0L,
                null
        );

        previousSnapshot.getFailureQuarantine().quarantineNode(1, 90L, 40L, "node_down", "ops");
        previousSnapshot.getFailureQuarantine().quarantineEdge(3, 80L, 30L, "edge_down", "ops");

        long publicationTicks = 100L;
        TopologyRuntimeSnapshot candidateSnapshot = runtimeFactory.buildSnapshot(
                source,
                TopologyTestFixtures.topologyVersion("d1-expired-next"),
                publicationTicks,
                previousSnapshot,
                source
        );

        FailureQuarantine.Snapshot candidate = candidateSnapshot.getFailureQuarantine().snapshot(publicationTicks);
        assertEquals("quarantine-d1-expired-next:0", candidate.snapshotId());
        assertEquals(0, candidate.activeNodeFailureCount());
        assertEquals(0, candidate.activeEdgeFailureCount());
        assertTrue(candidate.toLiveUpdates().isEmpty());
    }

    @Test
    @DisplayName("Reload preserves fresh quarantine priors and observed-at timestamps in the served scenario bundle")
    void testReloadPreservesScenarioPriorContinuityForFreshQuarantine() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(diamondSource());
        long departureTicks = TopologyTestFixtures.FIXED_CLOCK.instant().getEpochSecond();
        long activeUntilTicks = departureTicks + Duration.ofHours(3).toSeconds();
        long observedAtTicks = departureTicks - 90L;

        harness.currentSnapshot().getFailureQuarantine().quarantineEdge(
                3,
                activeUntilTicks,
                observedAtTicks,
                "edge_down",
                "ops"
        );

        FutureRouteRequest request = FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(Duration.ofHours(1).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();

        FutureRouteResultSet before = probabilisticRouteService().evaluate(harness.currentSnapshot(), request);
        TopologyPublicationResult publicationResult = harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N4")
                                .x(3.0d)
                                .y(0.0d)
                                .build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34")
                                .originNodeId("N3")
                                .destinationNodeId("N4")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );
        FutureRouteResultSet after = probabilisticRouteService().evaluate(harness.currentSnapshot(), request);

        assertTrue(publicationResult.isReloaded());
        ScenarioDefinition beforeIncident = before.getScenarioBundle().getScenarios().getFirst();
        ScenarioDefinition afterIncident = after.getScenarioBundle().getScenarios().getFirst();
        assertEquals("incident_persists", beforeIncident.getScenarioId());
        assertEquals("incident_persists", afterIncident.getScenarioId());
        assertEquals(observedAtTicks, beforeIncident.getProbabilityAudit().getObservedAtTicks());
        assertEquals(observedAtTicks, afterIncident.getProbabilityAudit().getObservedAtTicks());
        assertEquals(beforeIncident.getProbability(), afterIncident.getProbability(), 1.0e-9d);
        assertEquals(beforeIncident.getProbabilityAudit().getFreshnessWeight(), afterIncident.getProbabilityAudit().getFreshnessWeight(), 1.0e-9d);
        assertEquals(publicationResult.getTopologyVersion(), after.getTopologyVersion());
    }

    private FutureRouteService strictRouteService() {
        return new FutureRouteService(
                new FutureRouteEvaluator(TopologyTestFixtures.strictQuarantineResolver(), TopologyTestFixtures.FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
        );
    }

    private FutureRouteService probabilisticRouteService() {
        return new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), TopologyTestFixtures.FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
        );
    }

    private List<Integer> edgeIds(List<LiveUpdate> updates) {
        return updates.stream().map(LiveUpdate::edgeId).toList();
    }

    private List<Long> validUntilTicks(List<LiveUpdate> updates) {
        return updates.stream().map(LiveUpdate::validUntilTicks).toList();
    }

    private TopologyModelSource diamondSource() {
        return TopologyModelSource.builder()
                .modelVersion("d1-diamond")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 1.0d))
                .node(node("N2", 1.0d, -1.0d))
                .node(node("N3", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E13", "N1", "N3"))
                .edge(edge("E02", "N0", "N2"))
                .edge(edge("E23", "N2", "N3"))
                .build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
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
