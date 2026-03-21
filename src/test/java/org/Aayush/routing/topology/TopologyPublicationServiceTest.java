package org.Aayush.routing.topology;

import org.Aayush.routing.future.DefaultScenarioBundleResolver;
import org.Aayush.routing.future.FutureMatrixService;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.TopologyAwareFutureMatrixService;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.Aayush.routing.overlay.LiveUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Topology Publication Service Tests")
class TopologyPublicationServiceTest {

    @Test
    @DisplayName("Validate-only publication runs gates without swapping the active snapshot")
    void testValidateOnlyPublicationDoesNotSwap() {
        TopologyModelSource source = baseLineSource();
        AtomicInteger gateRuns = new AtomicInteger();
        TopologyValidationGate gate = context -> {
            gateRuns.incrementAndGet();
            assertTrue(context.getCandidateSnapshot().getRouteCore()
                    .route(TopologyTestFixtures.routeRequest("N0", "N3"))
                    .isReachable());
        };
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(
                source,
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(),
                List.of(gate)
        );

        TopologyPublicationResult result = harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .rolloutPolicy(StructuralChangeSet.RolloutPolicy.VALIDATE_ONLY)
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N3")
                                .x(3.0d)
                                .y(0.0d)
                                .build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E23")
                                .originNodeId("N2")
                                .destinationNodeId("N3")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        assertEquals(1, gateRuns.get());
        assertFalse(result.isReloaded());
        assertEquals("topo-initial", harness.currentSnapshot().getTopologyVersion().getTopologyVersion());
        assertEquals(3, harness.publicationService().currentSource().getNodes().size());
        assertEquals(4, result.getCandidateSource().getNodes().size());
    }

    @Test
    @DisplayName("Atomic publication swaps topology-aware future services onto the rebuilt snapshot and invalidates stale result sets")
    void testAtomicPublicationIntegratesWithFutureServices() {
        TopologyModelSource source = baseLineSource();
        InMemoryEphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK);
        InMemoryEphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(TopologyTestFixtures.FIXED_CLOCK);
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(
                source,
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(routeStore, matrixStore),
                List.of()
        );

        harness.currentRouteCore().liveOverlayContract().upsert(LiveUpdate.of(0, 0.5f, 2_000_000_000L), 0L);

        TopologyAwareFutureRouteService routeService = new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new org.Aayush.routing.core.FutureRouteEvaluator(
                                new DefaultScenarioBundleResolver(),
                                TopologyTestFixtures.FIXED_CLOCK
                        ),
                        routeStore
                )
        );
        TopologyAwareFutureMatrixService matrixService = new TopologyAwareFutureMatrixService(
                harness.reloadCoordinator(),
                new FutureMatrixService(
                        new org.Aayush.routing.core.FutureMatrixEvaluator(
                                new DefaultScenarioBundleResolver(),
                                TopologyTestFixtures.FIXED_CLOCK
                        ),
                        matrixStore
                )
        );

        String oldRouteResultSetId = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N2")).getResultSetId();
        String oldMatrixResultSetId = matrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N2")).getResultSetId();
        assertTrue(routeService.getResultSet(oldRouteResultSetId).isPresent());
        assertTrue(matrixService.getResultSet(oldMatrixResultSetId).isPresent());

        TopologyPublicationResult publicationResult = harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N3")
                                .x(3.0d)
                                .y(0.0d)
                                .build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E23")
                                .originNodeId("N2")
                                .destinationNodeId("N3")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        assertFalse(routeService.getResultSet(oldRouteResultSetId).isPresent());
        assertFalse(matrixService.getResultSet(oldMatrixResultSetId).isPresent());
        assertEquals(1, harness.currentRouteCore().liveOverlayContract().snapshotActiveUpdates(0L).size());

        var newRouteResult = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        var newMatrixResult = matrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N3"));
        assertEquals(publicationResult.getTopologyVersion(), newRouteResult.getTopologyVersion());
        assertEquals(publicationResult.getTopologyVersion(), newMatrixResult.getTopologyVersion());
        assertEquals(List.of("N0", "N1", "N2", "N3"), newRouteResult.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertTrue(Float.isFinite(newMatrixResult.getAggregate().getExpectedCosts()[0][0]));
        assertTrue(routeService.getResultSet(newRouteResult.getResultSetId()).isPresent());
        assertTrue(matrixService.getResultSet(newMatrixResult.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Atomic publication preserves active quarantines for subjects that still exist")
    void testAtomicPublicationPreservesActiveQuarantineState() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(diamondSource());
        long activeUntilTicks = activeUntilTicks();
        harness.currentSnapshot().getFailureQuarantine().quarantineNode(1, activeUntilTicks, "node_down", "ops");
        harness.currentSnapshot().getFailureQuarantine().quarantineEdge(1, activeUntilTicks, "edge_down", "ops");

        harness.publicationService().publish(
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

        FailureQuarantine.Snapshot snapshot = harness.currentSnapshot().getFailureQuarantine().snapshot(0L);
        assertEquals(1, snapshot.activeNodeFailureCount());
        assertEquals(1, snapshot.activeEdgeFailureCount());
        assertEquals(List.of(0, 1, 2), snapshot.toLiveUpdates().stream().map(LiveUpdate::edgeId).toList());

        TopologyAwareFutureRouteService routeService = new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new org.Aayush.routing.core.FutureRouteEvaluator(
                                TopologyTestFixtures.strictQuarantineResolver(),
                                TopologyTestFixtures.FIXED_CLOCK
                        ),
                        new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
                )
        );
        var result = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        assertFalse(result.getExpectedRoute().getRoute().isReachable());
    }

    @Test
    @DisplayName("Atomic publication drops quarantines for removed subjects while preserving remaining failures")
    void testAtomicPublicationDropsRemovedSubjectQuarantines() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(diamondSource());
        long activeUntilTicks = activeUntilTicks();
        harness.currentSnapshot().getFailureQuarantine().quarantineNode(1, activeUntilTicks, "node_down", "ops");
        harness.currentSnapshot().getFailureQuarantine().quarantineEdge(3, activeUntilTicks, "edge_down", "ops");

        harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .removedNode("N1")
                        .build()
        );

        FailureQuarantine.Snapshot snapshot = harness.currentSnapshot().getFailureQuarantine().snapshot(0L);
        assertEquals(0, snapshot.activeNodeFailureCount());
        assertEquals(1, snapshot.activeEdgeFailureCount());
        assertEquals(List.of(1), snapshot.toLiveUpdates().stream().map(LiveUpdate::edgeId).toList());

        TopologyAwareFutureRouteService routeService = new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new org.Aayush.routing.core.FutureRouteEvaluator(
                                TopologyTestFixtures.strictQuarantineResolver(),
                                TopologyTestFixtures.FIXED_CLOCK
                        ),
                        new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
                )
        );
        var result = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        assertFalse(result.getExpectedRoute().getRoute().isReachable());
    }

    private static TopologyModelSource baseLineSource() {
        return TopologyModelSource.builder()
                .modelVersion("publication-base")
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

    private static long activeUntilTicks() {
        return TopologyTestFixtures.FIXED_CLOCK.instant().getEpochSecond() + 10_000L;
    }

    private static TopologyModelSource.NodeDefinition node(String nodeId, double x) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(0.0d)
                .build();
    }

    private static TopologyModelSource.EdgeDefinition edge(String edgeId, String originNodeId, String destinationNodeId) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(1.0f)
                .profileId(1)
                .build();
    }

    private static TopologyModelSource diamondSource() {
        return TopologyModelSource.builder()
                .modelVersion("diamond-publication")
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
                .edge(edge("E01", "N0", "N1", 1.0f))
                .edge(edge("E02", "N0", "N2", 2.0f))
                .edge(edge("E13", "N1", "N3", 1.0f))
                .edge(edge("E23", "N2", "N3", 1.0f))
                .build();
    }

    private static TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
                .build();
    }

    private static TopologyModelSource.EdgeDefinition edge(
            String edgeId,
            String originNodeId,
            String destinationNodeId,
            float baseWeight
    ) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(baseWeight)
                .profileId(1)
                .build();
    }
}
