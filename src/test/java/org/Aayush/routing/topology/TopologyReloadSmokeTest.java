package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
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
