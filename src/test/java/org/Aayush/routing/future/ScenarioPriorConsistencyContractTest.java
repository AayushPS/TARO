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
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@DisplayName("Scenario Prior Consistency Contract Tests")
class ScenarioPriorConsistencyContractTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Route and matrix serving expose the same scenario bundle identity and probabilities for the same topology and horizon")
    void testRouteAndMatrixServingShareScenarioProbabilities() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyRuntimeSnapshot snapshot = snapshot(highDegreeContractSource(), "prior-parity");

        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet routeResult = routeService.evaluate(snapshot, routeRequest());
        FutureMatrixResultSet matrixResult = matrixService.evaluate(snapshot, matrixRequest());

        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarioBundleId(),
                matrixResult.getScenarioBundle().getScenarioBundleId()
        );
    }

    @Test
    @DisplayName("Route serving dominant scenario explanation matches the materialized scenario bundle")
    void testRouteServingDominantScenarioProbabilityMatchesScenarioBundle() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyRuntimeSnapshot snapshot = snapshot(highDegreeContractSource(), "prior-dominant-route");

        FutureRouteResultSet routeResult = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, routeRequest());

        ScenarioDefinition expectedDominant = dominantScenario(routeResult, routeResult.getExpectedRoute());
        ScenarioDefinition robustDominant = dominantScenario(routeResult, routeResult.getRobustRoute());

        assertEquals(expectedDominant.getProbability(), routeResult.getExpectedRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertEquals(expectedDominant.getLabel(), routeResult.getExpectedRoute().getDominantScenarioLabel());
        assertEquals(expectedDominant.getExplanationTags(), routeResult.getExpectedRoute().getExplanationTags());
        assertEquals(robustDominant.getProbability(), routeResult.getRobustRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertEquals(robustDominant.getLabel(), routeResult.getRobustRoute().getDominantScenarioLabel());
        assertEquals(robustDominant.getExplanationTags(), routeResult.getRobustRoute().getExplanationTags());
    }

    private FutureRouteRequest routeRequest() {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(Instant.parse("2026-03-23T07:00:00Z").getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .topKAlternatives(2)
                .build();
    }

    private FutureMatrixRequest matrixRequest() {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(Instant.parse("2026-03-23T07:00:00Z").getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private ScenarioDefinition dominantScenario(FutureRouteResultSet routeResult, ScenarioRouteSelection selection) {
        return routeResult.getScenarioBundle().getScenarios().stream()
                .filter(scenario -> scenario.getScenarioId().equals(selection.getDominantScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source, String topologyId) {
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
                        .topologyVersion(topologyId)
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + topologyId)
                        .changeSetHash("changes-" + topologyId)
                        .build(),
                0L,
                null
        );
    }

    private TopologyModelSource highDegreeContractSource() {
        return contractSource("contract-high");
    }

    private TopologyModelSource contractSource(String modelVersion) {
        return TopologyModelSource.builder()
                .modelVersion(modelVersion)
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
                .recurringConfidence(0.55f)
                .recurringObservationCount(12)
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
