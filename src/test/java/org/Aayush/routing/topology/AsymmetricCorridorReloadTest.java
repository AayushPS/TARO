package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage D3 - reload contract tests for preserving compiled directional asymmetry through topology rebuilds.
 * Satisfies closure criterion: no compile path silently normalizes asymmetric temporal behavior into symmetric fallback output.
 */
@Tag("integration")
@DisplayName("Asymmetric Corridor Reload Tests")
class AsymmetricCorridorReloadTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant MONDAY_PEAK = Instant.parse("2026-03-23T08:00:00Z");

    @Test
    @DisplayName("Asymmetric corridor survives reload when the directional subject still exists")
    void testAsymmetricCorridorSurvivesReloadWhenSubjectStillExists() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(opposingProfileSource());
        TopologyAwareFutureRouteService routeService = routeService(harness);

        FutureRouteResultSet beforeForward = routeService.evaluate(request("N0", "N1"));
        FutureRouteResultSet beforeReverse = routeService.evaluate(request("N1", "N0"));

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
                                .profileId(3)
                                .build())
                        .build()
        );

        FutureRouteResultSet afterForward = routeService.evaluate(request("N0", "N1"));
        FutureRouteResultSet afterReverse = routeService.evaluate(request("N1", "N0"));

        assertEquals(List.of("N0", "N2", "N1"), beforeForward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N1", "N0"), beforeReverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertTrue(publicationResult.isReloaded());
        assertNotEquals(
                beforeForward.getTopologyVersion().getTopologyVersion(),
                publicationResult.getTopologyVersion().getTopologyVersion()
        );
        assertEquals(publicationResult.getTopologyVersion(), harness.currentSnapshot().getTopologyVersion());
        assertEquals(publicationResult.getTopologyVersion(), afterForward.getTopologyVersion());
        assertEquals(publicationResult.getTopologyVersion(), afterReverse.getTopologyVersion());
        assertEquals(List.of("N0", "N2", "N1"), afterForward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N1", "N0"), afterReverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Removed asymmetric subject drops cleanly instead of normalizing reverse output")
    void testRemovedAsymmetricSubjectDropsCleanlyInsteadOfNormalizing() {
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(opposingProfileSource());
        TopologyAwareFutureRouteService routeService = routeService(harness);

        FutureRouteResultSet beforeReverse = routeService.evaluate(request("N1", "N0"));
        assertEquals(List.of("N1", "N0"), beforeReverse.getExpectedRoute().getRoute().getPathExternalNodeIds());

        TopologyPublicationResult publicationResult = harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .removedEdge("E10")
                        .build()
        );

        FutureRouteResultSet afterForward = routeService.evaluate(request("N0", "N1"));
        FutureRouteResultSet afterReverse = routeService.evaluate(request("N1", "N0"));

        assertTrue(publicationResult.isReloaded());
        assertEquals(publicationResult.getTopologyVersion(), afterForward.getTopologyVersion());
        assertEquals(publicationResult.getTopologyVersion(), afterReverse.getTopologyVersion());
        assertEquals(List.of("N0", "N2", "N1"), afterForward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N1", "N2", "N0"), afterReverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
    }

    private TopologyAwareFutureRouteService routeService(TopologyTestFixtures.Harness harness) {
        return new TopologyAwareFutureRouteService(
                harness.reloadCoordinator(),
                new FutureRouteService(
                        new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                        new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
                )
        );
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("d3-asymmetric-baseline")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(1.0d)
                                .build())
                        .build();
    }

    private FutureRouteRequest request(String sourceExternalId, String targetExternalId) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(MONDAY_PEAK.getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(1).toSeconds())
                .topKAlternatives(2)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyModelSource opposingProfileSource() {
        return TopologyModelSource.builder()
                .modelVersion("d3-asymmetric-corridor")
                .profileTimezone("UTC")
                .profile(peakProfile(1, 4.0f))
                .profile(flatProfile(2, 1.0f))
                .profile(flatProfile(3, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 2.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .edge(edge("E01", "N0", "N1", 10.0f, 1))
                .edge(edge("E10", "N1", "N0", 10.0f, 2))
                .edge(edge("E02", "N0", "N2", 8.0f, 3))
                .edge(edge("E21", "N2", "N1", 8.0f, 3))
                .edge(edge("E12", "N1", "N2", 8.0f, 3))
                .edge(edge("E20", "N2", "N0", 8.0f, 3))
                .build();
    }

    private TopologyModelSource.ProfileDefinition peakProfile(int profileId, float peakMultiplier) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? peakMultiplier : 1.0f);
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition flatProfile(int profileId, float bucketValue) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(bucketValue);
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
