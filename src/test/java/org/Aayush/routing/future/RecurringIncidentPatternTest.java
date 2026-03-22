package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
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

@DisplayName("Recurring Incident Pattern Tests")
@Tag("integration")
class RecurringIncidentPatternTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Recurring weekday incidents shift expected and robust routes while weekends revert to the baseline corridor")
    void testRecurringWeekdayIncidentChangesServedRoute() {
        TopologyRuntimeSnapshot snapshot = snapshot(baseRecurringSource(), "b3-recurring-incident");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet weekday = service.evaluate(snapshot, requestAt("2026-03-23T07:00:00Z"));
        FutureRouteResultSet weekend = service.evaluate(snapshot, requestAt("2026-03-22T07:00:00Z"));

        assertEquals(List.of("N0", "N2", "N3"), weekday.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), weekday.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), weekend.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), weekend.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2, weekday.getScenarioBundle().getScenarios().size());
        assertEquals(1, weekend.getScenarioBundle().getScenarios().size());
        assertEquals("recurring_incident_peak", weekday.getScenarioBundle().getScenarios().get(1).getScenarioId());
        assertEquals(
                weekday.getScenarioBundle().getScenarios().get(1).getProbability(),
                weekday.getScenarioBundle().getScenarios().get(1).getProbabilityAudit().getAdjustedProbability(),
                1.0e-9d
        );
    }

    private FutureRouteRequest requestAt(String departureIsoInstant) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(Instant.parse(departureIsoInstant).getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(15))
                .topKAlternatives(2)
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source, String topologyId) {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(runtimeTemplate());
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion(source.getModelVersion())
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("changes-" + topologyId)
                .build();
        return runtimeFactory.buildSnapshot(source, topologyVersion, 0L, null);
    }

    private TopologyRuntimeTemplate runtimeTemplate() {
        return TopologyRuntimeTemplate.builder()
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .bucketSizeSeconds(3_600)
                .liveOverlayCapacity(64)
                .build();
    }

    private TopologyModelSource baseRecurringSource() {
        return TopologyModelSource.builder()
                .modelVersion("b3-recurring-incident-source")
                .profileTimezone("UTC")
                .profile(weekdayIncidentProfile(1))
                .profile(flatProfile(2, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .node(node("N3", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 3_600.0f, 1))
                .edge(edge("E02", "N0", "N2", 4_500.0f, 2))
                .edge(edge("E13", "N1", "N3", 3_600.0f, 1))
                .edge(edge("E23", "N2", "N3", 4_500.0f, 2))
                .build();
    }

    private TopologyModelSource.ProfileDefinition weekdayIncidentProfile(int profileId) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT)
                .recurringConfidence(0.72f)
                .recurringObservationCount(18)
                .lastObservedAtTicks(Instant.parse("2026-03-23T06:15:00Z").getEpochSecond());
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? 2.0f : 1.0f);
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
