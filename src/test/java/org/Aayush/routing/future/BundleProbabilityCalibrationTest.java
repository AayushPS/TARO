package org.Aayush.routing.future;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.Aayush.routing.profile.ProfileRecencyCalibrationStore;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bundle Probability Calibration Tests")
class BundleProbabilityCalibrationTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final long DEPARTURE_TICKS = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
    private static final long HORIZON_TICKS = Duration.ofHours(2).toSeconds();

    @Test
    @DisplayName("Recurring bundles keep normalized probability mass and audit-aligned labels")
    void testRecurringBundleKeepsNormalizedProbabilityMassAndAuditAlignment() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        ScenarioBundle bundle = resolveRecurring(
                resolver,
                recurringIncidentSource("b6-bundle-recurring", 0.65f, 12, DEPARTURE_TICKS - 300L),
                "b6-bundle-recurring"
        );

        assertEquals(2, bundle.getScenarios().size());
        assertEquals(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)), bundle.getValidUntil());
        assertEquals(HORIZON_TICKS, bundle.getHorizonTicks());
        assertEquals(1.0d, bundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);

        ScenarioDefinition baseline = bundle.getScenarios().getFirst();
        ScenarioDefinition recurring = bundle.getScenarios().get(1);
        assertEquals("baseline", baseline.getScenarioId());
        assertEquals("baseline", baseline.getLabel());
        assertEquals("recurring_incident_peak", recurring.getScenarioId());
        assertEquals("recurring_incident_peak", recurring.getLabel());
        assertEquals(1.0d - recurring.getProbability(), baseline.getProbability(), 1.0e-9d);
        assertTrue(baseline.getExplanationTags().contains("recurring_incident_risk"));
        assertTrue(baseline.getExplanationTags().contains("recurrent_confidence_medium"));
        assertTrue(recurring.getExplanationTags().contains("recurrent_confidence_medium"));
        assertTrue(recurring.getExplanationTags().contains("recurrent_calibration_explicit"));
        assertTrue(recurring.getExplanationTags().contains("degree_prior_neutral"));
        assertTrue(recurring.getExplanationTags().contains("homophily_high"));

        assertNotNull(recurring.getProbabilityAudit());
        assertEquals("profile_recency", recurring.getProbabilityAudit().getEvidenceSource());
        assertEquals(recurring.getProbability(), recurring.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
        assertNotNull(recurring.getProbabilityAudit().getStructuralPriorAudit());
        assertEquals(0.0d, recurring.getProbabilityAudit().getStructuralPriorAudit().getAppliedAdjustment(), 1.0e-9d);
    }

    @Test
    @DisplayName("Quarantine bundles keep normalized probability mass and structural audits on both scenarios")
    void testQuarantineBundleKeepsNormalizedProbabilityMassAndStructuralAudits() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        ScenarioBundle bundle = resolveQuarantined(
                resolver,
                structuralHubSource(),
                "b6-bundle-quarantine",
                "E12",
                DEPARTURE_TICKS - 60L
        );

        assertEquals(2, bundle.getScenarios().size());
        assertEquals(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)), bundle.getValidUntil());
        assertEquals(1.0d, bundle.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);

        ScenarioDefinition incidentPersists = bundle.getScenarios().getFirst();
        ScenarioDefinition clearingFast = bundle.getScenarios().get(1);
        assertEquals("incident_persists", incidentPersists.getScenarioId());
        assertEquals("incident_persists", incidentPersists.getLabel());
        assertEquals("clearing_fast", clearingFast.getScenarioId());
        assertEquals("clearing_fast", clearingFast.getLabel());
        assertTrue(incidentPersists.getExplanationTags().contains("structural_prior_present"));
        assertTrue(incidentPersists.getExplanationTags().contains("degree_prior_high"));
        assertTrue(incidentPersists.getExplanationTags().contains("homophily_high"));
        assertTrue(clearingFast.getExplanationTags().contains("recovery_expected"));

        assertNotNull(incidentPersists.getProbabilityAudit());
        assertNotNull(clearingFast.getProbabilityAudit());
        assertEquals("quarantine", incidentPersists.getProbabilityAudit().getEvidenceSource());
        assertEquals("quarantine", clearingFast.getProbabilityAudit().getEvidenceSource());
        assertEquals(
                incidentPersists.getProbability(),
                incidentPersists.getProbabilityAudit().getAdjustedProbability(),
                1.0e-9d
        );
        assertEquals(
                clearingFast.getProbability(),
                clearingFast.getProbabilityAudit().getAdjustedProbability(),
                1.0e-9d
        );
        assertNotNull(incidentPersists.getProbabilityAudit().getStructuralPriorAudit());
        assertNotNull(clearingFast.getProbabilityAudit().getStructuralPriorAudit());
        assertEquals(
                incidentPersists.getProbabilityAudit().getStructuralPriorAudit().getPolicyId(),
                clearingFast.getProbabilityAudit().getStructuralPriorAudit().getPolicyId()
        );
        assertEquals(
                incidentPersists.getProbabilityAudit().getStructuralPriorAudit().getAffectedEdgeCount(),
                clearingFast.getProbabilityAudit().getStructuralPriorAudit().getAffectedEdgeCount()
        );
        assertTrue(incidentPersists.getProbabilityAudit().getStructuralPriorAudit().getAppliedAdjustment() > 0.0d);
    }

    @Test
    @DisplayName("Sparse top-K serving keeps a low-degree alternative reachable when arterial evidence is present")
    void testSparseTopKServingKeepsLowDegreeAlternativeReachable() {
        TopologyModelSource source = sparseArterialSource();
        TopologyRuntimeSnapshot snapshot = snapshot(source, "b6-bundle-sparse-topk");
        snapshot.getFailureQuarantine().quarantineEdge(
                edgeIndex(source, "E12"),
                DEPARTURE_TICKS + HORIZON_TICKS,
                DEPARTURE_TICKS - 60L,
                "edge_down",
                "ops"
        );

        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet resultSet = service.evaluate(
                snapshot,
                FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N5")
                                .departureTicks(DEPARTURE_TICKS)
                                .build())
                        .horizonTicks(HORIZON_TICKS)
                        .resultTtl(Duration.ofMinutes(10))
                        .topKAlternatives(2)
                        .build()
        );

        assertEquals(2, resultSet.getScenarioBundle().getScenarios().size());
        assertTrue(resultSet.getScenarioResults().stream()
                .anyMatch(result -> result.getRoute().getPathExternalNodeIds().equals(java.util.List.of("N0", "N1", "N2", "N5"))));
        assertTrue(resultSet.getScenarioResults().stream()
                .anyMatch(result -> result.getRoute().getPathExternalNodeIds().equals(java.util.List.of("N0", "N3", "N4", "N5"))));
    }

    private ScenarioBundle resolveRecurring(
            DefaultScenarioBundleResolver resolver,
            TopologyModelSource source,
            String topologyId
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        return resolver.resolve(
                request("N0", "N2"),
                costEngine(source),
                TemporalTestContexts.calendarUtc(),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(DEPARTURE_TICKS),
                FIXED_CLOCK
        );
    }

    private ScenarioBundle resolveQuarantined(
            DefaultScenarioBundleResolver resolver,
            TopologyModelSource source,
            String topologyId,
            String affectedEdgeId,
            long observedAtTicks
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        snapshot.getFailureQuarantine().quarantineEdge(
                edgeIndex(source, affectedEdgeId),
                DEPARTURE_TICKS + HORIZON_TICKS,
                observedAtTicks,
                "edge_down",
                "ops"
        );
        return resolver.resolve(
                request("N0", "N2"),
                costEngine(source),
                TemporalTestContexts.calendarUtc(),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(DEPARTURE_TICKS),
                FIXED_CLOCK
        );
    }

    private FutureRouteRequest request(String sourceNodeId, String targetNodeId) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceNodeId)
                        .targetExternalId(targetNodeId)
                        .departureTicks(DEPARTURE_TICKS)
                        .build())
                .horizonTicks(HORIZON_TICKS)
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
                recurrenceCalibrationStore(source),
                recencyCalibrationStore(source)
        );
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

    private TopologyModelSource recurringIncidentSource(
            String modelVersion,
            float recurringConfidence,
            int observationCount,
            long lastObservedAtTicks
    ) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder profile = TopologyModelSource.ProfileDefinition.builder()
                .profileId(1)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT)
                .recurringConfidence(recurringConfidence)
                .recurringObservationCount(observationCount)
                .lastObservedAtTicks(lastObservedAtTicks);
        for (int hour = 0; hour < 24; hour++) {
            profile.bucket(hour == 8 ? 2.0f : 1.0f);
        }
        return TopologyModelSource.builder()
                .modelVersion(modelVersion)
                .profileTimezone("UTC")
                .profile(profile.build())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1))
                .edge(edge("E12", "N1", "N2", 1))
                .build();
    }

    private TopologyModelSource structuralHubSource() {
        return TopologyModelSource.builder()
                .modelVersion("b6-bundle-structural-hub")
                .profileTimezone("UTC")
                .profile(profile(1))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .node(node("N3", 1.0d, 1.0d))
                .node(node("N4", 2.0d, 1.0d))
                .node(node("N5", 2.0d, -1.0d))
                .edge(edge("E01", "N0", "N1", 1))
                .edge(edge("E12", "N1", "N2", 1))
                .edge(edge("E13", "N1", "N3", 1))
                .edge(edge("E24", "N2", "N4", 1))
                .edge(edge("E25", "N2", "N5", 1))
                .build();
    }

    private TopologyModelSource sparseArterialSource() {
        return TopologyModelSource.builder()
                .modelVersion("b6-bundle-sparse-arterial")
                .profileTimezone("UTC")
                .profile(profile(1))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .node(node("N3", 1.0d, 1.0d))
                .node(node("N4", 2.0d, 1.0d))
                .node(node("N5", 3.0d, 0.5d))
                .node(node("N6", 1.0d, -1.0d))
                .node(node("N7", 2.0d, -1.0d))
                .edge(edge("E01", "N0", "N1", 4_000.0f, 1))
                .edge(edge("E12", "N1", "N2", 1.0f, 1))
                .edge(edge("E25", "N2", "N5", 1.0f, 1))
                .edge(edge("E03", "N0", "N3", 1_500.0f, 1))
                .edge(edge("E34", "N3", "N4", 1_500.0f, 1))
                .edge(edge("E45", "N4", "N5", 1_500.0f, 1))
                .edge(edge("E16", "N1", "N6", 1.0f, 1))
                .edge(edge("E27", "N2", "N7", 1.0f, 1))
                .build();
    }

    private ProfileRecurrenceCalibrationStore recurrenceCalibrationStore(TopologyModelSource source) {
        Map<Integer, ProfileRecurrenceCalibrationStore.ProfileRecurrenceCalibration> calibrationByProfileId = new HashMap<>();
        for (TopologyModelSource.ProfileDefinition profile : source.getProfiles()) {
            if (profile.getRecurringSignalFlavor() == null) {
                continue;
            }
            calibrationByProfileId.put(
                    profile.getProfileId(),
                    new ProfileRecurrenceCalibrationStore.ProfileRecurrenceCalibration(
                            profile.getRecurringObservationCount(),
                            profile.getRecurringConfidence(),
                            profile.getRecurringSignalFlavor(),
                            ProfileRecurrenceCalibrationStore.CalibrationSource.EXPLICIT_SOURCE
                    )
            );
        }
        return calibrationByProfileId.isEmpty()
                ? ProfileRecurrenceCalibrationStore.empty()
                : new ProfileRecurrenceCalibrationStore(calibrationByProfileId);
    }

    private ProfileRecencyCalibrationStore recencyCalibrationStore(TopologyModelSource source) {
        Map<Integer, ProfileRecencyCalibrationStore.ProfileRecencyCalibration> calibrationByProfileId = new HashMap<>();
        for (TopologyModelSource.ProfileDefinition profile : source.getProfiles()) {
            if (profile.getLastObservedAtTicks() == null) {
                continue;
            }
            calibrationByProfileId.put(
                    profile.getProfileId(),
                    new ProfileRecencyCalibrationStore.ProfileRecencyCalibration(
                            profile.getLastObservedAtTicks(),
                            ProfileRecencyCalibrationStore.CalibrationSource.EXPLICIT_SOURCE
                    )
            );
        }
        return calibrationByProfileId.isEmpty()
                ? ProfileRecencyCalibrationStore.empty()
                : new ProfileRecencyCalibrationStore(calibrationByProfileId);
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

    private TopologyModelSource.EdgeDefinition edge(
            String edgeId,
            String originNodeId,
            String destinationNodeId,
            int profileId
    ) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(1.0f)
                .profileId(profileId)
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
