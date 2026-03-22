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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Near Horizon Prior Shift Tests")
class NearHorizonPriorShiftTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Fresh evidence shifts near-horizon incident priors more than far-horizon priors")
    void testNearHorizonIncidentPriorShiftIsStrongerThanFarHorizonShift() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        long validUntilTicks = departureTicks + Duration.ofHours(6).toSeconds();
        TopologyRuntimeSnapshot snapshot = snapshot();
        snapshot.getFailureQuarantine().quarantineNode(1, validUntilTicks, departureTicks - 60L, "node_down", "ops");

        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        ScenarioBundle nearBundle = resolveBundle(
                snapshot,
                request(departureTicks, Duration.ofMinutes(30)),
                resolver
        );
        ScenarioBundle farBundle = resolveBundle(
                snapshot,
                request(departureTicks, Duration.ofHours(6)),
                resolver
        );
        ScenarioDefinition near = incidentPersists(nearBundle);
        ScenarioDefinition far = incidentPersists(farBundle);

        assertTrue(near.getProbability() > far.getProbability());
        assertTrue(near.getProbabilityAudit().getHorizonWeight() > far.getProbabilityAudit().getHorizonWeight());
        assertEquals(1.0d, nearBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, farBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
    }

    private ScenarioBundle resolveBundle(
            TopologyRuntimeSnapshot snapshot,
            FutureRouteRequest request,
            DefaultScenarioBundleResolver resolver
    ) {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        return service.evaluate(snapshot, request).getScenarioBundle();
    }

    private ScenarioDefinition incidentPersists(ScenarioBundle bundle) {
        return bundle.getScenarios().getFirst();
    }

    private FutureRouteRequest request(long departureTicks, Duration horizon) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(horizon.toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyRuntimeSnapshot snapshot() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("near-horizon-prior-shift")
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
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyRuntimeTemplate.builder()
                        .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .liveOverlayCapacity(32)
                        .build()
        );
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion(source.getModelVersion())
                .topologyVersion("topo-near-horizon")
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-near-horizon")
                .changeSetHash("change-near-horizon")
                .build();
        return runtimeFactory.buildSnapshot(source, topologyVersion, 0L, null);
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
