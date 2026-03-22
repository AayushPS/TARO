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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Temporal Freshness Ordering Tests")
class TemporalFreshnessOrderingTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Fresh quarantine evidence outranks stale and legacy-unknown freshness")
    void testFreshQuarantineEvidenceProducesMonotonicProbabilityOrdering() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        long validUntilTicks = departureTicks + Duration.ofHours(2).toSeconds();
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        TopologyRuntimeSnapshot freshSnapshot = snapshot("fresh");
        freshSnapshot.getFailureQuarantine().quarantineNode(1, validUntilTicks, departureTicks - 60L, "node_down", "ops");

        TopologyRuntimeSnapshot staleSnapshot = snapshot("stale");
        staleSnapshot.getFailureQuarantine().quarantineNode(
                1,
                validUntilTicks,
                departureTicks - Duration.ofHours(6).toSeconds(),
                "node_down",
                "ops"
        );

        TopologyRuntimeSnapshot legacySnapshot = snapshot("legacy");
        legacySnapshot.getFailureQuarantine().quarantineNode(1, validUntilTicks, "node_down", "ops");

        ScenarioDefinition fresh = incidentPersists(resolveBundle(freshSnapshot, request(departureTicks), resolver));
        ScenarioDefinition stale = incidentPersists(resolveBundle(staleSnapshot, request(departureTicks), resolver));
        ScenarioDefinition legacy = incidentPersists(resolveBundle(legacySnapshot, request(departureTicks), resolver));

        assertTrue(fresh.getProbability() > stale.getProbability());
        assertTrue(stale.getProbability() > legacy.getProbability());
        assertTrue(fresh.getProbabilityAudit().getFreshnessWeight() > stale.getProbabilityAudit().getFreshnessWeight());
        assertTrue(stale.getProbabilityAudit().getFreshnessWeight() > legacy.getProbabilityAudit().getFreshnessWeight());
        assertNull(legacy.getProbabilityAudit().getObservedAtTicks());
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

    private FutureRouteRequest request(long departureTicks) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(String suffix) {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("freshness-ordering-" + suffix)
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
                .topologyVersion("topo-" + suffix)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + suffix)
                .changeSetHash("change-" + suffix)
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
