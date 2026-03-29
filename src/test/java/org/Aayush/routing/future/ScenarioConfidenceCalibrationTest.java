package org.Aayush.routing.future;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Scenario Confidence Calibration Tests")
class ScenarioConfidenceCalibrationTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final long DEPARTURE_TICKS = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
    private static final long HORIZON_TICKS = Duration.ofHours(2).toSeconds();

    @Test
    @DisplayName("Fresh recurring incident evidence produces higher scenario probability than stale equivalent evidence")
    void testFreshEvidenceProducesHigherIncidentScenarioProbabilityThanStaleEquivalent() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyModelSource freshSource = recurringIncidentSource("b6-confidence-fresh", 0.65f, 12, DEPARTURE_TICKS - 300L);
        TopologyModelSource staleSource = recurringIncidentSource(
                "b6-confidence-stale",
                0.65f,
                12,
                DEPARTURE_TICKS - Duration.ofDays(2).toSeconds()
        );

        ScenarioBundle fresh = resolve(resolver, freshSource, "b6-confidence-fresh");
        ScenarioBundle stale = resolve(resolver, staleSource, "b6-confidence-stale");

        ScenarioDefinition freshScenario = recurringScenario(fresh);
        ScenarioDefinition staleScenario = recurringScenario(stale);

        assertEquals(2, fresh.getScenarios().size());
        assertEquals(2, stale.getScenarios().size());
        assertTrue(freshScenario.getProbability() > staleScenario.getProbability());
        assertTrue(freshScenario.getProbabilityAudit().getFreshnessWeight() > staleScenario.getProbabilityAudit().getFreshnessWeight());
        assertEquals(freshScenario.getProbability(), freshScenario.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
        assertEquals(staleScenario.getProbability(), staleScenario.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
        assertEquals(1.0d, fresh.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertEquals(1.0d, stale.getScenarios().stream().mapToDouble(ScenarioDefinition::getProbability).sum(), 1.0e-9d);
        assertTrue(freshScenario.getProbability() <= 0.75d);
        assertTrue(staleScenario.getProbability() >= 0.15d);
    }

    @Test
    @DisplayName("Confidence tags and audits stay aligned with adjusted probability")
    void testConfidenceTagsAndProbabilityAuditStayAlignedWithAdjustedProbability() {
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        TopologyModelSource highSource = recurringIncidentSource("b6-confidence-high", 0.85f, 20, DEPARTURE_TICKS - 300L);
        TopologyModelSource lowSource = recurringIncidentSource("b6-confidence-low", 0.35f, 4, DEPARTURE_TICKS - 300L);

        ScenarioDefinition highScenario = recurringScenario(resolve(resolver, highSource, "b6-confidence-high"));
        ScenarioDefinition lowScenario = recurringScenario(resolve(resolver, lowSource, "b6-confidence-low"));

        assertTrue(highScenario.getExplanationTags().contains("recurrent_confidence_high"));
        assertTrue(lowScenario.getExplanationTags().contains("recurrent_confidence_low"));
        assertTrue(highScenario.getProbability() > lowScenario.getProbability());
        assertNotNull(highScenario.getProbabilityAudit());
        assertNotNull(lowScenario.getProbabilityAudit());
        assertEquals(highScenario.getProbability(), highScenario.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
        assertEquals(lowScenario.getProbability(), lowScenario.getProbabilityAudit().getAdjustedProbability(), 1.0e-9d);
        assertTrue(highScenario.getProbabilityAudit().getBaseProbability() > lowScenario.getProbabilityAudit().getBaseProbability());
        assertTrue(highScenario.getProbability() <= 0.75d);
        assertTrue(lowScenario.getProbability() >= 0.15d);
    }

    private ScenarioBundle resolve(DefaultScenarioBundleResolver resolver, TopologyModelSource source, String topologyId) {
        TopologyRuntimeSnapshot snapshot = snapshot(source, topologyId);
        return resolver.resolve(
                request(DEPARTURE_TICKS, HORIZON_TICKS),
                costEngine(source),
                TemporalTestContexts.calendarUtc(),
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(DEPARTURE_TICKS),
                FIXED_CLOCK
        );
    }

    private FutureRouteRequest request(long departureTicks, long horizonTicks) {
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

    private ScenarioDefinition recurringScenario(ScenarioBundle bundle) {
        return bundle.getScenarios().stream()
                .filter(scenario -> !"baseline".equals(scenario.getScenarioId()))
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

    private CostEngine costEngine(TopologyModelSource source) {
        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        return new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(32),
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
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E12", "N1", "N2"))
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
