package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.DefaultScenarioBundleResolver;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.RecencyCalibrationConfig;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage D1 - recency-sensitive quarantine override contract tests for near-horizon scenario materialization.
 * Satisfies closure criterion: fresh quarantine entries override stale historical throughput in scenario generation and route output.
 */
@Tag("integration")
@DisplayName("Quarantine Recency Override Tests")
class QuarantineRecencyOverrideTest {
    private static final Duration FRESHNESS_HALF_LIFE = RecencyCalibrationConfig.defaults().freshnessHalfLife();

    @Test
    @DisplayName("Fresh quarantine observations raise near-horizon incident persistence probability")
    void testFreshObservationRaisesNearHorizonIncidentPersistenceProbability() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        long validUntilTicks = departureTicks + Duration.ofHours(3).toSeconds();
        TopologyModelSource source = historicalShortcutSource();

        TopologyRuntimeSnapshot freshSnapshot = snapshot(source, "d1-recency-fresh");
        freshSnapshot.getFailureQuarantine().quarantineEdge(0, validUntilTicks, departureTicks - 60L, "edge_down", "ops");
        ScenarioBundle freshBundle = resolveBundle(
                freshSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        );
        ScenarioDefinition freshScenario = incidentScenario(freshBundle);

        TopologyRuntimeSnapshot staleSnapshot = snapshot(source, "d1-recency-stale");
        staleSnapshot.getFailureQuarantine().quarantineEdge(
                0,
                validUntilTicks,
                departureTicks - Duration.ofHours(12).toSeconds(),
                "edge_down",
                "ops"
        );
        ScenarioBundle staleBundle = resolveBundle(
                staleSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        );
        ScenarioDefinition staleScenario = incidentScenario(staleBundle);

        assertEquals("incident_persists", freshScenario.getScenarioId());
        assertEquals("quarantine", freshScenario.getProbabilityAudit().getEvidenceSource());
        assertEquals("quarantine", staleScenario.getProbabilityAudit().getEvidenceSource());
        assertEquals(departureTicks - 60L, freshScenario.getProbabilityAudit().getObservedAtTicks());
        assertEquals(departureTicks - Duration.ofHours(12).toSeconds(), staleScenario.getProbabilityAudit().getObservedAtTicks());
        assertTrue(freshScenario.getProbability() > staleScenario.getProbability());
        assertTrue(freshScenario.getProbabilityAudit().getFreshnessWeight() > staleScenario.getProbabilityAudit().getFreshnessWeight());
        assertEquals(1.0d, freshBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
    }

    @Test
    @DisplayName("The 45-minute freshness window is explicit and ordered at the just-inside versus just-outside boundary")
    void testFreshnessWindowBoundaryOrderingIsExplicit() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        long validUntilTicks = departureTicks + Duration.ofHours(3).toSeconds();
        long freshnessWindowSeconds = FRESHNESS_HALF_LIFE.toSeconds();
        TopologyModelSource source = historicalShortcutSource();

        TopologyRuntimeSnapshot insideSnapshot = snapshot(source, "d1-boundary-inside");
        insideSnapshot.getFailureQuarantine().quarantineEdge(
                0,
                validUntilTicks,
                departureTicks - freshnessWindowSeconds + 1L,
                "edge_down",
                "ops"
        );
        ScenarioDefinition inside = incidentScenario(resolveBundle(
                insideSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        ));

        TopologyRuntimeSnapshot outsideSnapshot = snapshot(source, "d1-boundary-outside");
        outsideSnapshot.getFailureQuarantine().quarantineEdge(
                0,
                validUntilTicks,
                departureTicks - freshnessWindowSeconds - 1L,
                "edge_down",
                "ops"
        );
        ScenarioDefinition outside = incidentScenario(resolveBundle(
                outsideSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        ));

        assertEquals(freshnessWindowSeconds, FRESHNESS_HALF_LIFE.toSeconds());
        assertTrue(inside.getProbability() > outside.getProbability());
        assertTrue(inside.getProbabilityAudit().getFreshnessWeight() > outside.getProbabilityAudit().getFreshnessWeight());
        assertEquals(departureTicks - freshnessWindowSeconds + 1L, inside.getProbabilityAudit().getObservedAtTicks());
        assertEquals(departureTicks - freshnessWindowSeconds - 1L, outside.getProbabilityAudit().getObservedAtTicks());
    }

    @Test
    @DisplayName("Fresh quarantine reroutes future output away from the historically fastest branch")
    void testFreshQuarantineReroutesAwayFromHistoricalShortcut() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        long validUntilTicks = departureTicks + Duration.ofHours(3).toSeconds();
        TopologyModelSource source = historicalShortcutSource();

        TopologyRuntimeSnapshot baselineSnapshot = snapshot(source, "d1-route-baseline");
        FutureRouteResultSet baseline = evaluate(
                baselineSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        );

        TopologyRuntimeSnapshot quarantinedSnapshot = snapshot(source, "d1-route-quarantined");
        quarantinedSnapshot.getFailureQuarantine().quarantineEdge(
                0,
                validUntilTicks,
                departureTicks - 60L,
                "edge_down",
                "ops"
        );
        FutureRouteResultSet quarantined = evaluate(
                quarantinedSnapshot,
                routeRequest("N0", "N3", departureTicks, Duration.ofMinutes(30))
        );

        assertEquals(List.of("N0", "N1", "N3"), baseline.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), quarantined.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), quarantined.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(
                List.of("incident_persists", "clearing_fast"),
                quarantined.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList()
        );
        assertEquals("incident_persists", quarantined.getExpectedRoute().getDominantScenarioId());
        assertTrue(quarantined.getExpectedRoute().getExplanationTags().contains("edge_down"));
        assertEquals(
                quarantinedSnapshot.getFailureQuarantine().snapshot(departureTicks).snapshotId(),
                quarantined.getQuarantineSnapshotId()
        );
    }

    private ScenarioBundle resolveBundle(TopologyRuntimeSnapshot snapshot, FutureRouteRequest request) {
        return evaluate(snapshot, request).getScenarioBundle();
    }

    private FutureRouteResultSet evaluate(TopologyRuntimeSnapshot snapshot, FutureRouteRequest request) {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), TopologyTestFixtures.FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(TopologyTestFixtures.FIXED_CLOCK)
        );
        return service.evaluate(snapshot, request);
    }

    private ScenarioDefinition incidentScenario(ScenarioBundle bundle) {
        return bundle.getScenarios().stream()
                .filter(scenario -> "incident_persists".equals(scenario.getScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private FutureRouteRequest routeRequest(String sourceExternalId, String targetExternalId, long departureTicks, Duration horizon) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(horizon.toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source, String topologyId) {
        return new TopologyRuntimeFactory(TopologyTestFixtures.runtimeTemplate()).buildSnapshot(
                source,
                TopologyTestFixtures.topologyVersion(topologyId),
                0L,
                null
        );
    }

    private TopologyModelSource historicalShortcutSource() {
        return TopologyModelSource.builder()
                .modelVersion("d1-historical-shortcut")
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
                .edge(edge("E13", "N1", "N3", 1.0f))
                .edge(edge("E02", "N0", "N2", 2.0f))
                .edge(edge("E23", "N2", "N3", 2.0f))
                .build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
                .build();
    }

    private TopologyModelSource.EdgeDefinition edge(String edgeId, String originNodeId, String destinationNodeId, float baseWeight) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(baseWeight)
                .profileId(1)
                .build();
    }
}
