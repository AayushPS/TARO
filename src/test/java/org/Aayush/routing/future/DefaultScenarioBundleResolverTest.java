package org.Aayush.routing.future;

import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.topology.CompiledTopologyModel;
import org.Aayush.routing.topology.TopologyModelCompiler;
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

import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Scenario Bundle Resolver Tests")
class DefaultScenarioBundleResolverTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("No active quarantine yields one baseline scenario")
    void testBaselineScenarioWhenNoFailuresActive() {
        TopologyModelSource source = lineSource();
        TopologyRuntimeSnapshot snapshot = snapshot(source);
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        ScenarioBundle bundle = resolver.resolve(
                futureRouteRequest(0L, 12L),
                edgeGraph(source),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(0L),
                FIXED_CLOCK
        );

        assertEquals(1, bundle.getScenarios().size());
        assertEquals("baseline", bundle.getScenarios().getFirst().getScenarioId());
        assertEquals(1.0d, bundle.getScenarios().getFirst().getProbability(), 1.0e-9d);
        assertEquals(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)), bundle.getValidUntil());
    }

    @Test
    @DisplayName("Active quarantines produce incident-persistent and clearing-fast scenarios with clipped recovery")
    void testBoundQuarantineProducesRecoveryScenario() {
        TopologyModelSource source = lineSource();
        TopologyRuntimeSnapshot snapshot = snapshot(source);
        snapshot.getFailureQuarantine().quarantineNode(1, 1_000L, "node_down", "ops");
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        ScenarioBundle bundle = resolver.resolve(
                futureRouteRequest(100L, 8L),
                edgeGraph(source),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(100L),
                FIXED_CLOCK
        );

        assertEquals(2, bundle.getScenarios().size());

        ScenarioDefinition incidentPersists = bundle.getScenarios().get(0);
        assertEquals("incident_persists", incidentPersists.getScenarioId());
        assertEquals(List.of("node_down", "ops"), incidentPersists.getExplanationTags());
        assertEquals(List.of(0, 1), incidentPersists.getLiveUpdates().stream().map(update -> update.edgeId()).toList());
        assertTrue(incidentPersists.getLiveUpdates().stream().allMatch(update -> update.validUntilTicks() == 1_000L));

        ScenarioDefinition clearingFast = bundle.getScenarios().get(1);
        assertEquals("clearing_fast", clearingFast.getScenarioId());
        assertTrue(clearingFast.getExplanationTags().contains("recovery_expected"));
        assertEquals(List.of(0, 1), clearingFast.getLiveUpdates().stream().map(update -> update.edgeId()).toList());
        assertTrue(clearingFast.getLiveUpdates().stream().allMatch(update -> update.validUntilTicks() == 104L));
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source) {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyRuntimeTemplate.builder()
                        .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .liveOverlayCapacity(32)
                        .build()
        );
        return runtimeFactory.buildSnapshot(source, topologyVersion(), 0L, null);
    }

    private EdgeGraph edgeGraph(TopologyModelSource source) {
        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        return EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
    }

    private FutureRouteRequest futureRouteRequest(long departureTicks, long horizonTicks) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(horizonTicks)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private TopologyVersion topologyVersion() {
        return TopologyVersion.builder()
                .modelVersion("resolver-model")
                .topologyVersion("resolver-topology")
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("resolver-lineage")
                .changeSetHash("resolver-change")
                .build();
    }

    private TopologyModelSource lineSource() {
        return TopologyModelSource.builder()
                .modelVersion("resolver-source")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E12", "N1", "N2"))
                .build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
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
