package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.topology.TopologyModelSource;
import org.Aayush.routing.topology.TopologyRuntimeFactory;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyRuntimeTemplate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@DisplayName("Directionality Asymmetry Tests")
class DirectionalityAsymmetryTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("One-way corridor asymmetry survives future-serving output")
    void testOneWayCorridorAsymmetrySurvivesFutureServing() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(oneWayCorridorSource(), "b5-one-way");

        FutureRouteResultSet forward = service.evaluate(snapshot, request("A", "C", 0L));
        FutureRouteResultSet reverse = service.evaluate(snapshot, request("C", "A", 0L));

        assertEquals(List.of("A", "B", "C"), forward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("C", "D", "A"), reverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, forward.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, reverse.getExpectedRoute().getRouteSelectionProvenance());
        assertNotNull(forward.getCandidateDensityCalibrationReport());
        assertNotNull(reverse.getCandidateDensityCalibrationReport());
    }

    @Test
    @DisplayName("Turn-sensitive directional asymmetry survives future-serving output")
    void testTurnSensitiveDirectionalAsymmetrySurvivesFutureServing() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(turnSensitiveSource(), "b5-turn-sensitive");

        FutureRouteResultSet forward = service.evaluate(snapshot, request("N0", "N2", 0L));
        FutureRouteResultSet reverse = service.evaluate(snapshot, request("N2", "N0", 0L));

        assertEquals(List.of("N0", "N3", "N4", "N2"), forward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N2", "N1", "N0"), reverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, forward.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, reverse.getExpectedRoute().getRouteSelectionProvenance());
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("direction-baseline")
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

    private FutureRouteRequest request(String sourceExternalId, String targetExternalId, long departureTicks) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(3_600L)
                .topKAlternatives(2)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source, String topologyId) {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyRuntimeTemplate.builder()
                        .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .bucketSizeSeconds(3_600)
                        .liveOverlayCapacity(32)
                        .build()
        );
        return runtimeFactory.buildSnapshot(
                source,
                TopologyVersion.builder()
                        .modelVersion(source.getModelVersion())
                        .topologyVersion(topologyId)
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + topologyId)
                        .changeSetHash("changes-" + topologyId)
                        .build(),
                0L,
                null
        );
    }

    private TopologyModelSource oneWayCorridorSource() {
        return TopologyModelSource.builder()
                .modelVersion("b5-one-way-source")
                .profileTimezone("UTC")
                .profile(flatProfile(1))
                .node(node("A", 0.0d, 0.0d))
                .node(node("B", 1.0d, 0.0d))
                .node(node("C", 2.0d, 0.0d))
                .node(node("D", 1.0d, -1.0d))
                .edge(edge("AB", "A", "B", 1.0f, 1))
                .edge(edge("BC", "B", "C", 1.0f, 1))
                .edge(edge("CD", "C", "D", 1.0f, 1))
                .edge(edge("DA", "D", "A", 1.0f, 1))
                .build();
    }

    private TopologyModelSource turnSensitiveSource() {
        return TopologyModelSource.builder()
                .modelVersion("b5-turn-sensitive-source")
                .profileTimezone("UTC")
                .profile(flatProfile(1))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .node(node("N3", 0.5d, -1.0d))
                .node(node("N4", 1.5d, -1.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .edge(edge("E12", "N1", "N2", 1.0f, 1))
                .edge(edge("E21", "N2", "N1", 1.0f, 1))
                .edge(edge("E10", "N1", "N0", 1.0f, 1))
                .edge(edge("E03", "N0", "N3", 1.0f, 1))
                .edge(edge("E34", "N3", "N4", 1.0f, 1))
                .edge(edge("E42", "N4", "N2", 1.0f, 1))
                .edge(edge("E24", "N2", "N4", 1.0f, 1))
                .edge(edge("E43", "N4", "N3", 1.0f, 1))
                .edge(edge("E30", "N3", "N0", 1.0f, 1))
                .turnCost(turn("E01", "E12", 4.0f))
                .build();
    }

    private TopologyModelSource.ProfileDefinition flatProfile(int profileId) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(1.0f);
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

    private TopologyModelSource.TurnCostDefinition turn(String fromEdgeId, String toEdgeId, float penaltySeconds) {
        return TopologyModelSource.TurnCostDefinition.builder()
                .fromEdgeId(fromEdgeId)
                .toEdgeId(toEdgeId)
                .penaltySeconds(penaltySeconds)
                .build();
    }
}
