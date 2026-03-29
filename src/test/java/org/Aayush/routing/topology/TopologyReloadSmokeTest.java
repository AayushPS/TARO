package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.future.DefaultScenarioBundleResolver;
import org.Aayush.routing.future.FutureMatrixService;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.TopologyAwareFutureMatrixService;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
@Tag("integration")
@DisplayName("Topology Reload Smoke Tests")
class TopologyReloadSmokeTest {

    @Test
    @DisplayName("Unchanged coordinate-addressed route and matrix queries remain stable across reload")
    void testUnchangedCoordinateQueriesRemainStableAcrossReload() {
        TopologyModelSource source = corridorSource();
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);
        RouteRequest routeRequest = coordinateRouteRequest(0.02d, 1.98d);
        MatrixRequest matrixRequest = coordinateMatrixRequest(0.02d, 1.98d);

        RouteResponse beforeRoute = harness.currentRouteCore().route(routeRequest);
        MatrixResponse beforeMatrix = harness.currentRouteCore().matrix(matrixRequest);

        harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N3")
                                .x(10.0d)
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

        RouteResponse afterRoute = harness.currentRouteCore().route(routeRequest);
        MatrixResponse afterMatrix = harness.currentRouteCore().matrix(matrixRequest);

        assertEquals("N0", beforeRoute.getSourceResolvedAddress().getResolvedExternalId());
        assertEquals("N2", beforeRoute.getTargetResolvedAddress().getResolvedExternalId());
        assertEquals(beforeRoute.getSourceResolvedAddress().getResolvedExternalId(), afterRoute.getSourceResolvedAddress().getResolvedExternalId());
        assertEquals(beforeRoute.getTargetResolvedAddress().getResolvedExternalId(), afterRoute.getTargetResolvedAddress().getResolvedExternalId());
        assertEquals(List.of("N0", "N1", "N2"), beforeRoute.getPathExternalNodeIds());
        assertEquals(beforeRoute.getPathExternalNodeIds(), afterRoute.getPathExternalNodeIds());
        assertEquals(beforeRoute.getTotalCost(), afterRoute.getTotalCost(), 0.0001f);
        assertEquals(beforeRoute.getArrivalTicks(), afterRoute.getArrivalTicks());
        assertEquals(beforeMatrix.getReachable()[0][0], afterMatrix.getReachable()[0][0]);
        assertEquals(beforeMatrix.getTotalCosts()[0][0], afterMatrix.getTotalCosts()[0][0], 0.0001f);
        assertEquals(beforeMatrix.getArrivalTicks()[0][0], afterMatrix.getArrivalTicks()[0][0]);
    }

    @Test
    @DisplayName("Topology add-edge reload smoke restores route and matrix reachability")
    void testTopologyAddEdgeReloadSmoke() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("add-edge")
                .profileTimezone("UTC")
                .profile(profile())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f))
                .build();
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);

        assertFalse(harness.currentRouteCore().route(TopologyTestFixtures.routeRequest("N0", "N2")).isReachable());

        harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E12")
                                .originNodeId("N1")
                                .destinationNodeId("N2")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        assertTrue(harness.currentRouteCore().route(TopologyTestFixtures.routeRequest("N0", "N2")).isReachable());
        assertTrue(Float.isFinite(
                harness.currentRouteCore().matrix(TopologyTestFixtures.matrixRequest("N0", "N2")).getTotalCosts()[0][0]
        ));
    }

    @Test
    @DisplayName("Topology add-node reload smoke publishes the new endpoint")
    void testTopologyAddNodeReloadSmoke() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("add-node")
                .profileTimezone("UTC")
                .profile(profile())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f))
                .build();
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);

        harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder()
                                .nodeId("N2")
                                .x(2.0d)
                                .y(0.0d)
                                .build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E12")
                                .originNodeId("N1")
                                .destinationNodeId("N2")
                                .baseWeight(1.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        assertTrue(harness.currentRouteCore().route(TopologyTestFixtures.routeRequest("N0", "N2")).isReachable());
    }

    @Test
    @DisplayName("Edge-drop quarantine path matches the later structural reload path")
    void testEdgeDropQuarantineAndReloadParity() {
        TopologyModelSource source = diamondSource();
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);
        TopologyAwareFutureRouteService routeService = new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new FutureRouteEvaluator(TopologyTestFixtures.strictQuarantineResolver(), TopologyTestFixtures.FIXED_CLOCK),
                        new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
                )
        );

        harness.currentSnapshot().getFailureQuarantine().quarantineEdge(2, 10_000L, "edge_down", "ops");
        var quarantined = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        assertEquals(List.of("N0", "N2", "N3"), quarantined.getExpectedRoute().getRoute().getPathExternalNodeIds());

        harness.publicationService().publish(
                StructuralChangeSet.builder().removedEdge("E13").build()
        );
        var reloaded = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        assertEquals(List.of("N0", "N2", "N3"), reloaded.getExpectedRoute().getRoute().getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Node-failure quarantine suppresses all incident edges in future-aware evaluation")
    void testNodeFailureQuarantineSmoke() {
        TopologyModelSource source = diamondSource();
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);
        TopologyAwareFutureRouteService routeService = new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new FutureRouteEvaluator(TopologyTestFixtures.strictQuarantineResolver(), TopologyTestFixtures.FIXED_CLOCK),
                        new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
                )
        );

        harness.currentSnapshot().getFailureQuarantine().quarantineNode(1, 10_000L, "node_down", "ops");
        var result = routeService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));

        assertEquals(List.of("N0", "N2", "N3"), result.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertTrue(result.getScenarioBundle().getScenarios().getFirst().getExplanationTags().contains("node_down"));
    }

    @Test
    @DisplayName("Retained results follow configured compatibility policy across reload")
    void testRetainedResultsFollowCompatibilityPolicyAcrossReload() {
        TopologyModelSource source = corridorSource();

        InMemoryEphemeralRouteResultStore strictRouteStore = new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK);
        InMemoryEphemeralMatrixResultStore strictMatrixStore = new InMemoryEphemeralMatrixResultStore(TopologyTestFixtures.FIXED_CLOCK);
        TopologyTestFixtures.Harness strictHarness = TopologyTestFixtures.createHarness(
                source,
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(strictRouteStore, strictMatrixStore),
                List.of()
        );
        TopologyAwareFutureRouteService strictRouteService = routeService(strictHarness, strictRouteStore);
        TopologyAwareFutureMatrixService strictMatrixService = matrixService(strictHarness, strictMatrixStore);

        var strictRouteResult = strictRouteService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N2"));
        var strictMatrixResult = strictMatrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N2"));
        assertTrue(strictRouteService.getResultSummary(strictRouteResult.getResultSetId()).isPresent());
        assertTrue(strictMatrixService.getResultSummary(strictMatrixResult.getResultSetId()).isPresent());

        TopologyPublicationResult strictPublication = strictHarness.publicationService().publish(
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

        assertFalse(strictRouteService.getResultSummary(strictRouteResult.getResultSetId()).isPresent());
        assertFalse(strictMatrixService.getResultSummary(strictMatrixResult.getResultSetId()).isPresent());
        var strictNewRoute = strictRouteService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        var strictNewMatrix = strictMatrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N3"));
        assertEquals(strictPublication.getTopologyVersion(), strictNewRoute.getTopologyVersion());
        assertEquals(strictPublication.getTopologyVersion(), strictNewMatrix.getTopologyVersion());

        InMemoryEphemeralRouteResultStore retainRouteStore = new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK);
        InMemoryEphemeralMatrixResultStore retainMatrixStore = new InMemoryEphemeralMatrixResultStore(TopologyTestFixtures.FIXED_CLOCK);
        TopologyTestFixtures.Harness retainHarness = TopologyTestFixtures.createHarness(
                source,
                ReloadCompatibilityPolicy.retainUntilExpiry(),
                List.of(retainRouteStore, retainMatrixStore),
                List.of()
        );
        TopologyAwareFutureRouteService retainRouteService = routeService(retainHarness, retainRouteStore);
        TopologyAwareFutureMatrixService retainMatrixService = matrixService(retainHarness, retainMatrixStore);

        var retainRouteResult = retainRouteService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N2"));
        var retainMatrixResult = retainMatrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N2"));

        TopologyPublicationResult retainPublication = retainHarness.publicationService().publish(
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

        var retainedRouteSummary = retainRouteService.getResultSummary(retainRouteResult.getResultSetId()).orElseThrow();
        var retainedMatrixSummary = retainMatrixService.getResultSummary(retainMatrixResult.getResultSetId()).orElseThrow();
        assertEquals(retainRouteResult.getTopologyVersion(), retainedRouteSummary.getTopologyVersion());
        assertEquals(retainMatrixResult.getTopologyVersion(), retainedMatrixSummary.getTopologyVersion());

        var retainNewRoute = retainRouteService.evaluate(TopologyTestFixtures.futureRouteRequest("N0", "N3"));
        var retainNewMatrix = retainMatrixService.evaluate(TopologyTestFixtures.futureMatrixRequest("N0", "N3"));
        assertEquals(retainPublication.getTopologyVersion(), retainNewRoute.getTopologyVersion());
        assertEquals(retainPublication.getTopologyVersion(), retainNewMatrix.getTopologyVersion());
    }

    private static TopologyAwareFutureRouteService routeService(
            TopologyTestFixtures.Harness harness,
            InMemoryEphemeralRouteResultStore routeStore
    ) {
        return new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), TopologyTestFixtures.FIXED_CLOCK),
                        routeStore
                )
        );
    }

    private static TopologyAwareFutureMatrixService matrixService(
            TopologyTestFixtures.Harness harness,
            InMemoryEphemeralMatrixResultStore matrixStore
    ) {
        return new TopologyAwareFutureMatrixService(
                harness.reloadCoordinator(),
                new FutureMatrixService(
                        new org.Aayush.routing.core.FutureMatrixEvaluator(
                                new DefaultScenarioBundleResolver(),
                                TopologyTestFixtures.FIXED_CLOCK
                        ),
                        matrixStore
                )
        );
    }

    private static TopologyModelSource diamondSource() {
        return TopologyModelSource.builder()
                .modelVersion("diamond")
                .profileTimezone("UTC")
                .profile(profile())
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

    private static TopologyModelSource corridorSource() {
        return TopologyModelSource.builder()
                .modelVersion("corridor")
                .profileTimezone("UTC")
                .profile(profile())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f))
                .edge(edge("E12", "N1", "N2", 1.0f))
                .build();
    }

    private static RouteRequest coordinateRouteRequest(double sourceX, double targetX) {
        return RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(sourceX, 0.0d))
                .targetAddress(AddressInput.ofXY(targetX, 0.0d))
                .maxSnapDistance(0.10d)
                .departureTicks(0L)
                .build();
    }

    private static MatrixRequest coordinateMatrixRequest(double sourceX, double targetX) {
        return MatrixRequest.builder()
                .sourceAddress(AddressInput.ofXY(sourceX, 0.0d))
                .targetAddress(AddressInput.ofXY(targetX, 0.0d))
                .maxSnapDistance(0.10d)
                .departureTicks(0L)
                .build();
    }

    private static TopologyModelSource.ProfileDefinition profile() {
        return TopologyModelSource.ProfileDefinition.builder()
                .profileId(1)
                .dayMask(0x7F)
                .bucket(1.0f)
                .multiplier(1.0f)
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
