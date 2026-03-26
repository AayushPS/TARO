package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Default Scenario Bundle Resolver Smoke Tests")
class DefaultScenarioBundleResolverSmokeTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Default resolver drives route and matrix serving with shared bundle lineage under live quarantine")
    void testDefaultResolverRouteAndMatrixServingSmoke() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        TopologyRuntimeSnapshot snapshot = snapshot(smokeSource());
        snapshot.getFailureQuarantine().quarantineEdge(
                1,
                departureTicks + Duration.ofHours(2).toSeconds(),
                departureTicks - 60L,
                "edge_down",
                "ops"
        );

        InMemoryEphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        InMemoryEphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK);
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                routeStore
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                matrixStore
        );

        FutureRouteResultSet routeResult = routeService.evaluate(snapshot, routeRequest(departureTicks));
        FutureMatrixResultSet matrixResult = matrixService.evaluate(snapshot, matrixRequest(departureTicks));

        assertEquals(routeResult.getScenarioBundle().getScenarioBundleId(), matrixResult.getScenarioBundle().getScenarioBundleId());
        assertEquals(routeResult.getTopologyVersion(), matrixResult.getTopologyVersion());
        assertEquals(routeResult.getQuarantineSnapshotId(), matrixResult.getQuarantineSnapshotId());
        assertEquals(2, routeResult.getScenarioBundle().getScenarios().size());
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList()
        );
        assertTrue(routeResult.getExpectedRoute().getRoute().isReachable());
        assertTrue(matrixResult.getAggregate().getReachabilityProbabilities()[0][0] > 0.0d);
        assertTrue(routeStore.get(routeResult.getResultSetId()).isPresent());
        assertTrue(matrixStore.get(matrixResult.getResultSetId()).isPresent());
    }

    private FutureRouteRequest routeRequest(long departureTicks) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .topKAlternatives(2)
                .build();
    }

    private FutureMatrixRequest matrixRequest(long departureTicks) {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
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
                        .liveOverlayCapacity(64)
                        .build()
        );
        return runtimeFactory.buildSnapshot(
                source,
                TopologyVersion.builder()
                        .modelVersion(source.getModelVersion())
                        .topologyVersion("smoke-" + source.getModelVersion())
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + source.getModelVersion())
                        .changeSetHash("change-" + source.getModelVersion())
                        .build(),
                0L,
                null
        );
    }

    private TopologyModelSource smokeSource() {
        return TopologyModelSource.builder()
                .modelVersion("default-resolver-smoke")
                .profileTimezone("UTC")
                .profile(incidentProfile(1))
                .profile(flatProfile(2))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .node(node("N3", 1.0d, 1.0d))
                .node(node("N4", 2.0d, 1.0d))
                .node(node("N5", 3.0d, 0.5d))
                .edge(edge("E01", "N0", "N1", 3_600.0f, 2))
                .edge(edge("E12", "N1", "N2", 1_500.0f, 1))
                .edge(edge("E25", "N2", "N5", 3_600.0f, 2))
                .edge(edge("E03", "N0", "N3", 3_900.0f, 2))
                .edge(edge("E34", "N3", "N4", 3_900.0f, 2))
                .edge(edge("E45", "N4", "N5", 3_900.0f, 2))
                .node(node("N6", 1.0d, -1.0d))
                .node(node("N7", 2.0d, -1.0d))
                .edge(edge("E16", "N1", "N6", 1.0f, 2))
                .edge(edge("E27", "N2", "N7", 1.0f, 2))
                .build();
    }

    private TopologyModelSource.ProfileDefinition incidentProfile(int profileId) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT)
                .recurringConfidence(0.70f)
                .recurringObservationCount(24)
                .lastObservedAtTicks(Instant.parse("2026-03-23T06:55:00Z").getEpochSecond());
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? 2.0f : 1.0f);
        }
        return builder.build();
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
}
