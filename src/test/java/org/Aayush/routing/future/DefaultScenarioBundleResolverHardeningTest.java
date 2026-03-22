package org.Aayush.routing.future;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
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
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Scenario Bundle Resolver Hardening Tests")
class DefaultScenarioBundleResolverHardeningTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Non-day-mask-aware temporal contexts suppress recurring scenarios entirely")
    void testLinearTemporalContextSuppressesRecurringScenario() {
        long departureTicks = departureTicks();
        TopologyModelSource source = singleEdgePeriodicSource(
                1,
                4.0f,
                0.75f,
                18,
                ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                departureTicks - 60L
        );

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N1",
                departureTicks,
                TemporalTestContexts.linear(),
                CostEngine.TemporalSamplingPolicy.INTERPOLATED,
                null
        );

        assertEquals(1, bundle.getScenarios().size());
        assertEquals("baseline", bundle.getScenarios().getFirst().getScenarioId());
    }

    @Test
    @DisplayName("Derived periodic calibration stays auditable even without explicit recency metadata")
    void testDerivedCalibrationFallsBackToMetadataAndUnknownAgeFloor() {
        long departureTicks = departureTicks();
        TopologyModelSource source = derivedPeriodicSource(5.0f);

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N1",
                departureTicks,
                TemporalTestContexts.calendarUtc(),
                CostEngine.TemporalSamplingPolicy.DISCRETE,
                null
        );

        assertEquals(2, bundle.getScenarios().size());
        ScenarioDefinition scenario = bundle.getScenarios().get(1);
        assertEquals("periodic_peak", scenario.getScenarioId());
        assertTrue(scenario.getExplanationTags().contains("recurrent_calibration_derived"));
        assertNotNull(scenario.getProbabilityAudit());
        assertNull(scenario.getProbabilityAudit().getObservedAtTicks());
        assertEquals(0.20d, scenario.getProbabilityAudit().getFreshnessWeight(), 1.0e-9d);
    }

    @Test
    @DisplayName("Recurring audit reports the freshest contributing observation instead of the highest-severity edge")
    void testRecurringAuditUsesFreshestContributingObservationAcrossProfiles() {
        long departureTicks = departureTicks();
        long staleObservedAtTicks = departureTicks - Duration.ofHours(12).toSeconds();
        long freshObservedAtTicks = departureTicks - 60L;
        TopologyModelSource source = mixedRecencySource(staleObservedAtTicks, freshObservedAtTicks);

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N2",
                departureTicks,
                TemporalTestContexts.calendarUtc(),
                CostEngine.TemporalSamplingPolicy.INTERPOLATED,
                null
        );

        ScenarioDefinition scenario = bundle.getScenarios().get(1);
        assertEquals("periodic_peak", scenario.getScenarioId());
        assertEquals(freshObservedAtTicks, scenario.getProbabilityAudit().getObservedAtTicks());
        assertTrue(scenario.getLiveUpdates().stream().map(LiveUpdate::edgeId).toList().containsAll(List.of(0, 1)));
    }

    @Test
    @DisplayName("Overlapping quarantine and recurring updates on the same edge stay blocked for the longer union window")
    void testOverlappingSharedEdgeUpdatesRemainBlocked() {
        long departureTicks = departureTicks();
        long quarantineValidUntilTicks = departureTicks + Duration.ofHours(3).toSeconds();
        TopologyModelSource source = singleEdgePeriodicSource(
                1,
                4.0f,
                0.80f,
                20,
                ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                departureTicks - 60L
        );

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N1",
                departureTicks,
                TemporalTestContexts.calendarUtc(),
                CostEngine.TemporalSamplingPolicy.INTERPOLATED,
                snapshot -> snapshot.getFailureQuarantine().quarantineEdge(0, quarantineValidUntilTicks, departureTicks - 30L, "edge_down", "ops")
        );

        LiveUpdate merged = incidentPersists(bundle).getLiveUpdates().getFirst();
        assertEquals(0, merged.edgeId());
        assertEquals(0.0f, merged.speedFactor(), 0.0f);
        assertEquals(quarantineValidUntilTicks, merged.validUntilTicks());
    }

    @Test
    @DisplayName("Non-overlapping shared-edge updates keep the immediate quarantine window when only one override can be emitted")
    void testNonOverlappingSharedEdgeUpdatesKeepImmediateQuarantineWindow() {
        long departureTicks = departureTicks();
        long quarantineValidUntilTicks = departureTicks + Duration.ofMinutes(30).toSeconds();
        TopologyModelSource source = singleEdgePeriodicSource(
                1,
                4.0f,
                0.80f,
                20,
                ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                departureTicks - 60L
        );

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N1",
                departureTicks,
                TemporalTestContexts.calendarUtc(),
                CostEngine.TemporalSamplingPolicy.INTERPOLATED,
                snapshot -> snapshot.getFailureQuarantine().quarantineEdge(0, quarantineValidUntilTicks, departureTicks - 30L, "edge_down", "ops")
        );

        LiveUpdate merged = incidentPersists(bundle).getLiveUpdates().getFirst();
        assertEquals(0, merged.edgeId());
        assertEquals(0.0f, merged.speedFactor(), 0.0f);
        assertEquals(quarantineValidUntilTicks, merged.validUntilTicks());
    }

    @Test
    @DisplayName("High-confidence explicit signals keep the high-confidence tag in discrete sampling mode")
    void testHighConfidenceExplicitSignalKeepsHighConfidenceTagInDiscreteMode() {
        long departureTicks = departureTicks();
        TopologyModelSource source = singleEdgePeriodicSource(
                1,
                4.0f,
                0.95f,
                36,
                ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                departureTicks - 60L
        );

        ScenarioBundle bundle = resolve(
                source,
                "N0",
                "N1",
                departureTicks,
                TemporalTestContexts.calendarUtc(),
                CostEngine.TemporalSamplingPolicy.DISCRETE,
                null
        );

        ScenarioDefinition scenario = bundle.getScenarios().get(1);
        assertTrue(scenario.getExplanationTags().contains("recurrent_confidence_high"));
    }

    private ScenarioDefinition incidentPersists(ScenarioBundle bundle) {
        return bundle.getScenarios().getFirst();
    }

    private ScenarioBundle resolve(
            TopologyModelSource source,
            String sourceNodeId,
            String targetNodeId,
            long departureTicks,
            ResolvedTemporalContext temporalContext,
            CostEngine.TemporalSamplingPolicy temporalSamplingPolicy,
            SnapshotMutation snapshotMutation
    ) {
        TopologyRuntimeSnapshot snapshot = snapshot(source);
        if (snapshotMutation != null) {
            snapshotMutation.apply(snapshot);
        }
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        return resolver.resolve(
                FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId(sourceNodeId)
                                .targetExternalId(targetNodeId)
                                .departureTicks(departureTicks)
                                .build())
                        .horizonTicks(Duration.ofHours(2).toSeconds())
                        .resultTtl(Duration.ofMinutes(10))
                        .build(),
                costEngine(source, temporalSamplingPolicy),
                temporalContext,
                snapshot.getTopologyVersion(),
                snapshot.getFailureQuarantine().snapshot(departureTicks),
                FIXED_CLOCK
        );
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source) {
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
        return runtimeFactory.buildSnapshot(source, topologyVersion(source.getModelVersion()), 0L, null);
    }

    private CostEngine costEngine(TopologyModelSource source, CostEngine.TemporalSamplingPolicy temporalSamplingPolicy) {
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
                temporalSamplingPolicy,
                edgeId -> "edge " + edgeId,
                CostEngine.ProfileValidationMode.DAY_MASK_AWARE_WEEKLY,
                recurrenceCalibrationStore(source),
                recencyCalibrationStore(source)
        );
    }

    private TopologyVersion topologyVersion(String modelVersion) {
        return TopologyVersion.builder()
                .modelVersion(modelVersion)
                .topologyVersion("resolver-hardening-topology-" + modelVersion)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + modelVersion)
                .changeSetHash("change-" + modelVersion)
                .build();
    }

    private TopologyModelSource singleEdgePeriodicSource(
            int profileId,
            float peakMultiplier,
            float recurringConfidence,
            int recurringObservationCount,
            ProfileRecurrenceCalibrationStore.SignalFlavor signalFlavor,
            long lastObservedAtTicks
    ) {
        return TopologyModelSource.builder()
                .modelVersion("single-periodic-" + peakMultiplier + "-" + recurringConfidence)
                .profileTimezone("UTC")
                .profile(explicitPeriodicProfile(
                        profileId,
                        peakMultiplier,
                        recurringConfidence,
                        recurringObservationCount,
                        signalFlavor,
                        lastObservedAtTicks
                ))
                .node(node("N0", 0.0d))
                .node(node("N1", 1.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, profileId))
                .build();
    }

    private TopologyModelSource mixedRecencySource(long staleObservedAtTicks, long freshObservedAtTicks) {
        return TopologyModelSource.builder()
                .modelVersion("mixed-recency-source")
                .profileTimezone("UTC")
                .profile(explicitPeriodicProfile(
                        1,
                        3.0f,
                        0.80f,
                        20,
                        ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                        staleObservedAtTicks
                ))
                .profile(explicitPeriodicProfile(
                        2,
                        3.0f,
                        0.80f,
                        20,
                        ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                        freshObservedAtTicks
                ))
                .node(node("N0", 0.0d))
                .node(node("N1", 1.0d))
                .node(node("N2", 2.0d))
                .edge(edge("E01", "N0", "N1", 2.0f, 1))
                .edge(edge("E12", "N1", "N2", 1.0f, 2))
                .build();
    }

    private TopologyModelSource derivedPeriodicSource(float peakMultiplier) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(1)
                .dayMask(0x1F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? peakMultiplier : 1.0f);
        }
        return TopologyModelSource.builder()
                .modelVersion("derived-periodic-" + peakMultiplier)
                .profileTimezone("UTC")
                .profile(builder.build())
                .node(node("N0", 0.0d))
                .node(node("N1", 1.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .build();
    }

    private TopologyModelSource.ProfileDefinition explicitPeriodicProfile(
            int profileId,
            float peakMultiplier,
            float recurringConfidence,
            int recurringObservationCount,
            ProfileRecurrenceCalibrationStore.SignalFlavor signalFlavor,
            long lastObservedAtTicks
    ) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(signalFlavor)
                .recurringConfidence(recurringConfidence)
                .recurringObservationCount(recurringObservationCount)
                .lastObservedAtTicks(lastObservedAtTicks);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? peakMultiplier : 1.0f);
        }
        return builder.build();
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

    private TopologyModelSource.NodeDefinition node(String nodeId, double x) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(0.0d)
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

    private long departureTicks() {
        return Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
    }

    @FunctionalInterface
    private interface SnapshotMutation {
        void apply(TopologyRuntimeSnapshot snapshot);
    }
}
