package org.Aayush.routing.future;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.testutil.TemporalTestContexts;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Degree-Aware Scenario Prior Tests")
class DegreeAwareScenarioPriorTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final long DEPARTURE_TICKS = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
    private static final long HORIZON_TICKS = Duration.ofHours(2).toSeconds();
    private static final double HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY = 0.45d;
    private static final double LOW_DEGREE_HISTORICAL_BUCKET_FREQUENCY = 0.43d;

    @Test
    @DisplayName("Evidence-present high-degree corridor prior outranks the low-degree alternative without dropping below the historical bucket floor")
    void testEvidencePresentHighDegreeIncidentPriorOutranksLowDegreeAlternative() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        ScenarioBundle highBundle = resolveWithRecentEvidence(
                resolver,
                highDegreeSource(),
                "b6-degree-high-fresh",
                "E01"
        );
        ScenarioBundle lowBundle = resolveWithRecentEvidence(
                resolver,
                lowDegreeSource(),
                "b6-degree-low-fresh",
                "E01"
        );

        ScenarioDefinition highIncident = incidentScenario(highBundle);
        ScenarioDefinition lowIncident = incidentScenario(lowBundle);

        ScenarioProbabilityAudit highAudit = highIncident.getProbabilityAudit();
        ScenarioProbabilityAudit lowAudit = lowIncident.getProbabilityAudit();
        assertNotNull(highAudit);
        assertNotNull(lowAudit);
        assertNotNull(highAudit.getStructuralPriorAudit());
        assertNotNull(lowAudit.getStructuralPriorAudit());

        assertTrue(
                highIncident.getProbability() >= HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY - 0.05d,
                "arterial bucket should not fall materially below its historical corridor-bucket frequency under fresh evidence"
        );
        assertTrue(
                highIncident.getProbability() >= lowIncident.getProbability() + 0.10d,
                "fresh evidence should keep the high-degree arterial at least ten points above the comparable low-degree alternative"
        );
        assertTrue(highAudit.getStructuralPriorAudit().getAppliedAdjustment() > 0.0d);
        assertTrue(lowAudit.getStructuralPriorAudit().getAppliedAdjustment() < 0.0d);
        assertTrue(highIncident.getExplanationTags().contains("degree_prior_high"));
        assertTrue(lowIncident.getExplanationTags().contains("degree_prior_low"));
        assertTrue(highIncident.getExplanationTags().contains("homophily_high"));
        assertTrue(lowIncident.getExplanationTags().contains("homophily_high"));
        assertTrue(highAudit.getStructuralPriorAudit().getNormalizedDegreeScore()
                > lowAudit.getStructuralPriorAudit().getNormalizedDegreeScore());
        assertTrue(highAudit.getStructuralPriorAudit().getCenteredDegreeSignal() > 0.99d);
        assertTrue(lowAudit.getStructuralPriorAudit().getCenteredDegreeSignal() < -0.99d);
        assertEquals(1.0d, highBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, lowBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, highIncident.getProbability() + recoveryScenario(highBundle).getProbability(), 1.0e-9d);
    }

    @Test
    @DisplayName("No recent evidence returns both corridor buckets toward their historical baseline without excess arterial bias")
    void testNoRecentEvidenceReturnsTowardBaseProbabilityWithoutExcessArterialBias() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();

        ScenarioBundle highBundle = resolveWithoutRecentEvidence(
                resolver,
                highDegreeSource(),
                "b6-degree-high-none",
                "E01"
        );
        ScenarioBundle lowBundle = resolveWithoutRecentEvidence(
                resolver,
                lowDegreeSource(),
                "b6-degree-low-none",
                "E01"
        );

        ScenarioDefinition highIncident = incidentScenario(highBundle);
        ScenarioDefinition lowIncident = incidentScenario(lowBundle);

        ScenarioProbabilityAudit highAudit = highIncident.getProbabilityAudit();
        ScenarioProbabilityAudit lowAudit = lowIncident.getProbabilityAudit();
        assertNotNull(highAudit);
        assertNotNull(lowAudit);
        assertNull(highAudit.getObservedAtTicks());
        assertNull(lowAudit.getObservedAtTicks());

        double highDeviation = Math.abs(highIncident.getProbability() - HIGH_DEGREE_HISTORICAL_BUCKET_FREQUENCY);
        double lowDeviation = Math.abs(lowIncident.getProbability() - LOW_DEGREE_HISTORICAL_BUCKET_FREQUENCY);

        assertTrue(highDeviation <= 0.05d, "arterial no-evidence deviation should stay within five points of its historical bucket");
        assertTrue(lowDeviation <= 0.05d, "low-degree no-evidence deviation should stay within five points of its historical bucket");
        assertTrue(
                highDeviation <= lowDeviation + 0.03d,
                "arterial deviation should not exceed the low-degree deviation by more than three points after evidence disappears"
        );
        assertTrue(highAudit.getStructuralPriorAudit().getAppliedAdjustment() > lowAudit.getStructuralPriorAudit().getAppliedAdjustment());
        assertTrue(highIncident.getExplanationTags().contains("degree_prior_high"));
        assertTrue(lowIncident.getExplanationTags().contains("degree_prior_low"));
        assertEquals(1.0d, highBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, lowBundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, highIncident.getProbability() + recoveryScenario(highBundle).getProbability(), 1.0e-9d);
    }

    private ScenarioBundle resolveWithRecentEvidence(
            DefaultScenarioBundleResolver resolver,
            TopologyModelSource source,
            String topologyId,
            String affectedEdgeId
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        int affectedEdgeIndex = edgeIndex(source, affectedEdgeId);
        snapshot.getFailureQuarantine().quarantineEdge(
                affectedEdgeIndex,
                DEPARTURE_TICKS + HORIZON_TICKS,
                DEPARTURE_TICKS - 60L,
                "edge_down",
                "ops"
        );
        return resolver.resolve(
                request(),
                costEngine(source),
                TemporalTestContexts.calendarUtc(),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(DEPARTURE_TICKS),
                FIXED_CLOCK
        );
    }

    private ScenarioBundle resolveWithoutRecentEvidence(
            DefaultScenarioBundleResolver resolver,
            TopologyModelSource source,
            String topologyId,
            String affectedEdgeId
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        int affectedEdgeIndex = edgeIndex(source, affectedEdgeId);
        snapshot.getFailureQuarantine().quarantineEdge(
                affectedEdgeIndex,
                DEPARTURE_TICKS + HORIZON_TICKS,
                "edge_down",
                "ops"
        );
        return resolver.resolve(
                request(),
                costEngine(source),
                TemporalTestContexts.calendarUtc(),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(DEPARTURE_TICKS),
                FIXED_CLOCK
        );
    }

    private FutureRouteRequest request() {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private ScenarioDefinition incidentScenario(ScenarioBundle bundle) {
        return bundle.getScenarios().stream()
                .filter(scenario -> "incident_persists".equals(scenario.getScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private ScenarioDefinition recoveryScenario(ScenarioBundle bundle) {
        return bundle.getScenarios().stream()
                .filter(scenario -> "clearing_fast".equals(scenario.getScenarioId()))
                .findFirst()
                .orElseThrow();
    }

    private int edgeIndex(TopologyModelSource source, String edgeId) {
        Map<String, Integer> nodeIndexById = new HashMap<>();
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

    private CostEngine costEngine(TopologyModelSource source) {
        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        return new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(64),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                3_600,
                CostEngine.TemporalSamplingPolicy.INTERPOLATED,
                edgeId -> "edge " + edgeId,
                CostEngine.ProfileValidationMode.DAY_MASK_AWARE_WEEKLY,
                null,
                null
        );
    }

    private TopologyModelSource highDegreeSource() {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("b6-high-degree")
                .profileTimezone("UTC")
                .profile(profile(1));
        for (int nodeId = 0; nodeId <= 10; nodeId++) {
            builder.node(node("N" + nodeId, nodeId, 0.0d));
        }
        builder.edge(edge("E01", "N0", "N1"));
        builder.edge(edge("E02", "N0", "N2"));
        builder.edge(edge("E03", "N0", "N3"));
        builder.edge(edge("E04", "N0", "N4"));
        builder.edge(edge("E05", "N0", "N5"));
        builder.edge(edge("E16", "N1", "N6"));
        builder.edge(edge("E17", "N1", "N7"));
        builder.edge(edge("E18", "N1", "N8"));
        builder.edge(edge("E19", "N1", "N9"));
        builder.edge(edge("E1A", "N1", "N10"));
        return builder.build();
    }

    private TopologyModelSource lowDegreeSource() {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("b6-low-degree")
                .profileTimezone("UTC")
                .profile(profile(1));
        for (int nodeId = 0; nodeId <= 17; nodeId++) {
            builder.node(node("N" + nodeId, nodeId, 0.0d));
        }
        builder.edge(edge("E01", "N0", "N1"));
        builder.edge(edge("E1B", "N1", "N11"));
        builder.edge(edge("ECD", "N12", "N13"));
        builder.edge(edge("ECE", "N12", "N14"));
        builder.edge(edge("ECF", "N12", "N15"));
        builder.edge(edge("ECG", "N12", "N16"));
        builder.edge(edge("ECH", "N12", "N17"));
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId) {
        return TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .bucket(1.0f)
                .multiplier(1.0f)
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
