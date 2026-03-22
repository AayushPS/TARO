package org.Aayush.routing.topology;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@DisplayName("Directed Profile Divergence Tests")
class DirectedProfileDivergenceTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant MONDAY_PEAK = Instant.parse("2026-03-23T08:00:00Z");

    @Test
    @DisplayName("Compiled opposing profiles remain distinct and drive different served winners")
    void testCompiledOpposingProfilesRemainDistinct() {
        TopologyModelSource source = opposingProfileSource();
        TopologyIndexLayout layout = TopologyIndexLayout.fromSource(source);
        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertEquals(1, edgeGraph.getProfileId(layout.findEdgeIndex("E01")));
        assertEquals(2, edgeGraph.getProfileId(layout.findEdgeIndex("E10")));

        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(source, "b5-directed-divergence");

        FutureRouteResultSet forward = service.evaluate(snapshot, request("N0", "N1"));
        FutureRouteResultSet reverse = service.evaluate(snapshot, request("N1", "N0"));

        assertEquals(List.of("N0", "N2", "N1"), forward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N1", "N0"), reverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("directed-baseline")
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

    private TopologyModelSource opposingProfileSource() {
        return TopologyModelSource.builder()
                .modelVersion("b5-directed-profile-source")
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
