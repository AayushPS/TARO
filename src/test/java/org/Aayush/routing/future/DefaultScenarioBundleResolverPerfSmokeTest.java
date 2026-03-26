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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Scenario Bundle Resolver Perf Smoke Tests")
class DefaultScenarioBundleResolverPerfSmokeTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final int EDGE_COUNT = 256;
    private static final int WARMUP_LOOPS = 10;
    private static final int MEASURED_LOOPS = 80;
    private static final double MAX_AVG_MICROS = 10_000.0d;

    @Test
    @DisplayName("Perf smoke: dense recurring materialization remains practical and bounded")
    void testDenseRecurringMaterializationPerfSmoke() {
        long departureTicks = Instant.parse("2026-03-23T07:00:00Z").getEpochSecond();
        TopologyModelSource source = denseRecurringSource(EDGE_COUNT, departureTicks - 60L);
        TopologyRuntimeSnapshot snapshot = snapshot(source);
        CostEngine costEngine = costEngine(source);
        DefaultScenarioBundleResolver resolver = new DefaultScenarioBundleResolver();
        FutureRouteRequest request = FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N256")
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();

        snapshot.getFailureQuarantine().quarantineEdge(
                16,
                departureTicks + Duration.ofHours(2).toSeconds(),
                departureTicks - 60L,
                "edge_down",
                "ops"
        );

        for (int i = 0; i < WARMUP_LOOPS; i++) {
            resolver.resolve(
                    request,
                    costEngine,
                    TemporalTestContexts.calendarUtc(),
                    snapshot.getTopologyVersion(),
                    snapshot.getFailureQuarantine().snapshot(departureTicks),
                    FIXED_CLOCK
            );
        }

        long startedAtNanos = System.nanoTime();
        ScenarioBundle last = null;
        for (int i = 0; i < MEASURED_LOOPS; i++) {
            last = resolver.resolve(
                    request,
                    costEngine,
                    TemporalTestContexts.calendarUtc(),
                    snapshot.getTopologyVersion(),
                    snapshot.getFailureQuarantine().snapshot(departureTicks),
                    FIXED_CLOCK
            );
        }
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        double avgMicros = (double) elapsedNanos / (double) MEASURED_LOOPS / 1_000.0d;

        assertTrue(last != null);
        assertEquals(2, last.getScenarios().size());
        assertTrue(last.getScenarios().get(1).getLiveUpdates().size() <= 65,
                "mixed recurring+quarantine payload should remain tightly bounded in this smoke test");
        assertTrue(avgMicros < MAX_AVG_MICROS,
                "average scenario bundle materialization should stay below " + MAX_AVG_MICROS + "us in this smoke test");
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
                        .topologyVersion("perf-" + source.getModelVersion())
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + source.getModelVersion())
                        .changeSetHash("change-" + source.getModelVersion())
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

    private TopologyModelSource denseRecurringSource(int edgeCount, long lastObservedAtTicks) {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("perf-dense-recurring-" + edgeCount)
                .profileTimezone("UTC")
                .profile(periodicProfile(lastObservedAtTicks));
        for (int i = 0; i <= edgeCount; i++) {
            builder.node(node("N" + i, i));
        }
        for (int i = 0; i < edgeCount; i++) {
            builder.edge(edge("E" + i, "N" + i, "N" + (i + 1), 1.0f + (i % 5), 1));
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition periodicProfile(long lastObservedAtTicks) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(1)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC)
                .recurringConfidence(0.85f)
                .recurringObservationCount(48)
                .lastObservedAtTicks(lastObservedAtTicks);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? 4.0f : 1.0f);
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
        return new ProfileRecurrenceCalibrationStore(calibrationByProfileId);
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
        return new ProfileRecencyCalibrationStore(calibrationByProfileId);
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
}
