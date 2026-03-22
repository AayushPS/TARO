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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Recency Calibration Tests")
class RecencyCalibrationTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Fresher recurring evidence increases scenario probability while result TTL does not")
    void testFresherRecurringEvidenceRaisesScenarioProbabilityWithoutTtlDependence() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        ScenarioBundle fresh = resolveBundle(
                periodicSource("fresh", departureTicks - 300L),
                request(departureTicks, Duration.ofHours(2), Duration.ofMinutes(10)),
                resolver
        );
        ScenarioBundle stale = resolveBundle(
                periodicSource("stale", departureTicks - Duration.ofHours(12).toSeconds()),
                request(departureTicks, Duration.ofHours(2), Duration.ofMinutes(10)),
                resolver
        );
        ScenarioBundle freshDifferentTtl = resolveBundle(
                periodicSource("fresh-ttl", departureTicks - 300L),
                request(departureTicks, Duration.ofHours(2), Duration.ofMinutes(30)),
                resolver
        );

        ScenarioDefinition freshScenario = fresh.getScenarios().get(1);
        ScenarioDefinition staleScenario = stale.getScenarios().get(1);
        ScenarioDefinition freshDifferentTtlScenario = freshDifferentTtl.getScenarios().get(1);

        assertEquals("periodic_peak", freshScenario.getScenarioId());
        assertTrue(freshScenario.getProbability() > staleScenario.getProbability());
        assertEquals(freshScenario.getProbability(), freshDifferentTtlScenario.getProbability(), 1.0e-9d);
        assertNotNull(freshScenario.getProbabilityAudit());
        assertNotNull(staleScenario.getProbabilityAudit());
        assertTrue(freshScenario.getProbabilityAudit().getFreshnessWeight() > staleScenario.getProbabilityAudit().getFreshnessWeight());
        assertEquals(freshScenario.getProbability(), freshScenario.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
    }

    private ScenarioBundle resolveBundle(
            TopologyModelSource source,
            FutureRouteRequest request,
            DefaultScenarioBundleResolver resolver
    ) {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        return service.evaluate(snapshot(source), request).getScenarioBundle();
    }

    private FutureRouteRequest request(long departureTicks, Duration horizon, Duration ttl) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(horizon.toSeconds())
                .resultTtl(ttl)
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source) {
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
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion(source.getModelVersion())
                .topologyVersion("topo-" + source.getModelVersion())
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + source.getModelVersion())
                .changeSetHash("change-" + source.getModelVersion())
                .build();
        return runtimeFactory.buildSnapshot(source, topologyVersion, 0L, null);
    }

    private TopologyModelSource periodicSource(String suffix, long lastObservedAtTicks) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder profile = TopologyModelSource.ProfileDefinition.builder()
                .profileId(1)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC)
                .recurringConfidence(0.75f)
                .recurringObservationCount(24)
                .lastObservedAtTicks(lastObservedAtTicks);
        for (int hour = 0; hour < 24; hour++) {
            profile.bucket(hour == 8 ? 4.0f : 1.0f);
        }
        return TopologyModelSource.builder()
                .modelVersion("recency-calibration-" + suffix)
                .profileTimezone("UTC")
                .profile(profile.build())
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
