package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.overlay.LiveUpdate;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Scenario Prior Consistency Contract Tests")
class ScenarioPriorConsistencyContractTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final long DEPARTURE_TICKS = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
    private static final long HORIZON_TICKS = Duration.ofHours(2).toSeconds();
    private static final double HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY = 0.45d;
    private static final double LOW_DEGREE_HISTORICAL_BUCKET_FREQUENCY = 0.43d;

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
    @DisplayName("Route and matrix serving preserve the same audited scenario prior metadata")
    void testRouteAndMatrixServingShareScenarioPriorAudits() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyRuntimeSnapshot snapshot = snapshot(highDegreeContractSource(), "prior-audits");

        FutureRouteResultSet routeResult = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, routeRequest());
        FutureMatrixResultSet matrixResult = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, matrixRequest());

        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getScenarioId).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getLabel).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getLabel).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbability).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbabilityAudit).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getProbabilityAudit).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getExplanationTags).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(ScenarioDefinition::getExplanationTags).toList()
        );
        assertEquals(
                routeResult.getScenarioBundle().getScenarios().stream().map(this::liveUpdateSignature).toList(),
                matrixResult.getScenarioBundle().getScenarios().stream().map(this::liveUpdateSignature).toList()
        );
        ScenarioDefinition auditedScenario = routeResult.getScenarioBundle().getScenarios().stream()
                .filter(scenario -> scenario.getProbabilityAudit() != null)
                .findFirst()
                .orElseThrow();
        assertNotNull(auditedScenario.getProbabilityAudit());
        assertNotNull(auditedScenario.getProbabilityAudit().getStructuralPriorAudit());
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

    @Test
    @DisplayName("Evidence-present contract thresholds hold consistently across route and matrix serving")
    void testEvidencePresentThresholdsHoldAcrossRouteAndMatrixServing() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyRuntimeSnapshot highSnapshot = quarantinedSnapshot(
                highDegreeThresholdSource(),
                "prior-threshold-high-fresh",
                "E01",
                DEPARTURE_TICKS - 60L
        );
        TopologyRuntimeSnapshot lowSnapshot = quarantinedSnapshot(
                lowDegreeThresholdSource(),
                "prior-threshold-low-fresh",
                "E01",
                DEPARTURE_TICKS - 60L
        );

        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet highRoute = routeService.evaluate(highSnapshot, thresholdRouteRequest());
        FutureMatrixResultSet highMatrix = matrixService.evaluate(highSnapshot, thresholdMatrixRequest());
        FutureRouteResultSet lowRoute = routeService.evaluate(lowSnapshot, thresholdRouteRequest());
        FutureMatrixResultSet lowMatrix = matrixService.evaluate(lowSnapshot, thresholdMatrixRequest());

        ScenarioDefinition highRouteIncident = incidentScenario(highRoute.getScenarioBundle());
        ScenarioDefinition highMatrixIncident = incidentScenario(highMatrix.getScenarioBundle());
        ScenarioDefinition lowRouteIncident = incidentScenario(lowRoute.getScenarioBundle());
        ScenarioDefinition lowMatrixIncident = incidentScenario(lowMatrix.getScenarioBundle());

        assertEquals(highRouteIncident.getProbability(), highMatrixIncident.getProbability(), 1.0e-9d);
        assertEquals(lowRouteIncident.getProbability(), lowMatrixIncident.getProbability(), 1.0e-9d);
        assertTrue(highRouteIncident.getProbability() >= HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY - 0.05d);
        assertTrue(highMatrixIncident.getProbability() >= HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY - 0.05d);
        assertTrue(highRouteIncident.getProbability() >= lowRouteIncident.getProbability() + 0.10d);
        assertTrue(highMatrixIncident.getProbability() >= lowMatrixIncident.getProbability() + 0.10d);
    }

    @Test
    @DisplayName("No-evidence baseline thresholds stay bounded across route and matrix serving")
    void testNoRecentEvidenceBaselineThresholdsHoldAcrossRouteAndMatrixServing() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyRuntimeSnapshot highSnapshot = quarantinedSnapshot(
                highDegreeThresholdSource(),
                "prior-threshold-high-stale",
                "E01",
                null
        );
        TopologyRuntimeSnapshot lowSnapshot = quarantinedSnapshot(
                lowDegreeThresholdSource(),
                "prior-threshold-low-stale",
                "E01",
                null
        );

        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet highRoute = routeService.evaluate(highSnapshot, thresholdRouteRequest());
        FutureMatrixResultSet highMatrix = matrixService.evaluate(highSnapshot, thresholdMatrixRequest());
        FutureRouteResultSet lowRoute = routeService.evaluate(lowSnapshot, thresholdRouteRequest());
        FutureMatrixResultSet lowMatrix = matrixService.evaluate(lowSnapshot, thresholdMatrixRequest());

        double highRouteDeviation = Math.abs(
                incidentScenario(highRoute.getScenarioBundle()).getProbability() - HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY
        );
        double lowRouteDeviation = Math.abs(
                incidentScenario(lowRoute.getScenarioBundle()).getProbability() - LOW_DEGREE_HISTORICAL_BUCKET_FREQUENCY
        );
        double highMatrixDeviation = Math.abs(
                incidentScenario(highMatrix.getScenarioBundle()).getProbability() - HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY
        );
        double lowMatrixDeviation = Math.abs(
                incidentScenario(lowMatrix.getScenarioBundle()).getProbability() - LOW_DEGREE_HISTORICAL_BUCKET_FREQUENCY
        );

        assertTrue(highRouteDeviation <= 0.05d);
        assertTrue(lowRouteDeviation <= 0.05d);
        assertTrue(highMatrixDeviation <= 0.05d);
        assertTrue(lowMatrixDeviation <= 0.05d);
        assertTrue(highRouteDeviation <= lowRouteDeviation + 0.03d);
        assertTrue(highMatrixDeviation <= lowMatrixDeviation + 0.03d);
    }

    private FutureRouteRequest routeRequest() {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
                .resultTtl(Duration.ofMinutes(10))
                .topKAlternatives(2)
                .build();
    }

    private FutureMatrixRequest matrixRequest() {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N5")
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private FutureRouteRequest thresholdRouteRequest() {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
                .resultTtl(Duration.ofMinutes(10))
                .topKAlternatives(2)
                .build();
    }

    private FutureMatrixRequest thresholdMatrixRequest() {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private ScenarioDefinition dominantScenario(FutureRouteResultSet routeResult, ScenarioRouteSelection selection) {
        return routeResult.getScenarioBundle().getScenarios().stream()
                .filter(scenario -> scenario.getScenarioId().equals(selection.getDominantScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> liveUpdateSignature(ScenarioDefinition scenario) {
        return scenario.getLiveUpdates().stream()
                .map(this::liveUpdateSignature)
                .toList();
    }

    private String liveUpdateSignature(LiveUpdate update) {
        return update.edgeId()
                + "|"
                + update.speedFactor()
                + "|"
                + update.validFromTicks()
                + "|"
                + update.validUntilTicks();
    }

    private ScenarioDefinition incidentScenario(ScenarioBundle bundle) {
        return bundle.getScenarios().stream()
                .filter(scenario -> "incident_persists".equals(scenario.getScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private TopologyRuntimeSnapshot quarantinedSnapshot(
            TopologyModelSource source,
            String topologyId,
            String affectedEdgeId,
            Long observedAtTicks
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        int edgeIndex = edgeIndex(source, affectedEdgeId);
        if (observedAtTicks == null) {
            snapshot.getFailureQuarantine().quarantineEdge(
                    edgeIndex,
                    DEPARTURE_TICKS + HORIZON_TICKS,
                    "edge_down",
                    "ops"
            );
        } else {
            snapshot.getFailureQuarantine().quarantineEdge(
                    edgeIndex,
                    DEPARTURE_TICKS + HORIZON_TICKS,
                    observedAtTicks,
                    "edge_down",
                    "ops"
            );
        }
        return snapshot;
    }

    private int edgeIndex(TopologyModelSource source, String edgeId) {
        HashMap<String, Integer> nodeIndexById = new HashMap<>();
        for (int nodeIndex = 0; nodeIndex < source.getNodes().size(); nodeIndex++) {
            nodeIndexById.put(source.getNodes().get(nodeIndex).getNodeId(), nodeIndex);
        }
        ArrayList<Integer> orderedEdgeIndexes = new ArrayList<>(source.getEdges().size());
        for (int edgeIndex = 0; edgeIndex < source.getEdges().size(); edgeIndex++) {
            orderedEdgeIndexes.add(edgeIndex);
        }
        orderedEdgeIndexes.sort(Comparator
                .comparingInt((Integer edgeIndex) -> nodeIndexById.get(source.getEdges().get(edgeIndex).getOriginNodeId()))
                .thenComparingInt(Integer::intValue));
        for (int edgeIndex = 0; edgeIndex < orderedEdgeIndexes.size(); edgeIndex++) {
            TopologyModelSource.EdgeDefinition edge = source.getEdges().get(orderedEdgeIndexes.get(edgeIndex));
            if (edge.getEdgeId().equals(edgeId)) {
                return edgeIndex;
            }
        }
        throw new IllegalArgumentException("edge missing from source: " + edgeId);
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

    private TopologyModelSource highDegreeThresholdSource() {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("prior-threshold-high")
                .profileTimezone("UTC")
                .profile(flatProfile(1));
        for (int nodeId = 0; nodeId <= 10; nodeId++) {
            builder.node(node("N" + nodeId, nodeId, 0.0d));
        }
        builder.edge(edge("E01", "N0", "N1", 1.0f, 1));
        builder.edge(edge("E02", "N0", "N2", 1.0f, 1));
        builder.edge(edge("E03", "N0", "N3", 1.0f, 1));
        builder.edge(edge("E04", "N0", "N4", 1.0f, 1));
        builder.edge(edge("E05", "N0", "N5", 1.0f, 1));
        builder.edge(edge("E16", "N1", "N6", 1.0f, 1));
        builder.edge(edge("E17", "N1", "N7", 1.0f, 1));
        builder.edge(edge("E18", "N1", "N8", 1.0f, 1));
        builder.edge(edge("E19", "N1", "N9", 1.0f, 1));
        builder.edge(edge("E1A", "N1", "N10", 1.0f, 1));
        return builder.build();
    }

    private TopologyModelSource lowDegreeThresholdSource() {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("prior-threshold-low")
                .profileTimezone("UTC")
                .profile(flatProfile(1));
        for (int nodeId = 0; nodeId <= 17; nodeId++) {
            builder.node(node("N" + nodeId, nodeId, 0.0d));
        }
        builder.edge(edge("E01", "N0", "N1", 1.0f, 1));
        builder.edge(edge("E1B", "N1", "N11", 1.0f, 1));
        builder.edge(edge("ECD", "N12", "N13", 1.0f, 1));
        builder.edge(edge("ECE", "N12", "N14", 1.0f, 1));
        builder.edge(edge("ECF", "N12", "N15", 1.0f, 1));
        builder.edge(edge("ECG", "N12", "N16", 1.0f, 1));
        builder.edge(edge("ECH", "N12", "N17", 1.0f, 1));
        return builder.build();
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
