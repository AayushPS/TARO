package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.overlay.LiveUpdate;
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
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("perf")
@Tag("integration")
@DisplayName("C3 Aggregate Objective Perf Smoke Tests")
class C3AggregateObjectivePerfSmokeTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final int SEGMENT_COUNT = 16;
    private static final int WARMUP_LOOPS = 6;
    private static final int ROUTE_LOOPS = 40;
    private static final double MAX_AVG_MICROS = 40_000.0d;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Perf smoke: direct aggregate objective planning stays stable on a branching ladder")
    void testAggregateObjectivePerfSmoke() {
        PerfTopology topology = buildPerfTopology(SEGMENT_COUNT);
        TopologyRuntimeSnapshot snapshot = snapshot(topology.source());
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(perfResolver(topology), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureRouteRequest request = FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("S")
                        .targetExternalId("T")
                        .departureTicks(0L)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .topKAlternatives(4)
                .resultTtl(Duration.ofMinutes(10))
                .build();

        for (int i = 0; i < WARMUP_LOOPS; i++) {
            service.evaluate(snapshot, request);
        }

        FutureRouteResultSet reference = service.evaluate(snapshot, request);
        long startedAtNanos = System.nanoTime();
        FutureRouteResultSet last = null;
        for (int i = 0; i < ROUTE_LOOPS; i++) {
            last = service.evaluate(snapshot, request);
        }
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        double avgMicros = (double) elapsedNanos / (double) ROUTE_LOOPS / 1_000.0d;

        assertTrue(last != null);
        assertEquals("c3-aggregate-perf-bundle", reference.getScenarioBundle().getScenarioBundleId());
        assertEquals(reference.getScenarioBundle().getScenarioBundleId(), last.getScenarioBundle().getScenarioBundleId());
        assertTrue(reference.getExpectedRoute().getRoute().isReachable());
        assertTrue(reference.getRobustRoute().getRoute().isReachable());
        assertEquals(
                reference.getExpectedRoute().getRoute().getPathExternalNodeIds(),
                last.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                reference.getRobustRoute().getRoute().getPathExternalNodeIds(),
                last.getRobustRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                reference.getCandidateDensityCalibrationReport().getDensityClass(),
                last.getCandidateDensityCalibrationReport().getDensityClass()
        );
        assertEquals(6, reference.getScenarioResults().size());
        assertTrue(reference.getScenarioResults().stream().allMatch(result -> result.getRoute().isReachable()));
        assertTrue(!reference.getAlternatives().isEmpty());
        assertTrue(avgMicros < MAX_AVG_MICROS,
                "average C3 aggregate objective evaluation should stay below " + MAX_AVG_MICROS + "us");
    }

    private ScenarioBundleResolver perfResolver(PerfTopology topology) {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) -> {
            long validUntilTicks = request.getDepartureTicks() + request.getHorizonTicks();
            return ScenarioBundle.builder()
                    .scenarioBundleId("c3-aggregate-perf-bundle")
                    .generatedAt(FIXED_CLOCK.instant())
                    .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                    .horizonTicks(request.getHorizonTicks())
                    .topologyVersion(resolvedTopologyVersion)
                    .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("baseline")
                            .label("baseline")
                            .probability(0.25d)
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("lane_a_mid")
                            .label("lane_a_mid")
                            .probability(0.20d)
                            .liveUpdates(updatesForRange(topology.laneAEdges(), 4, 10, 0.45f, validUntilTicks))
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("lane_a_tail")
                            .label("lane_a_tail")
                            .probability(0.15d)
                            .liveUpdates(updatesForRange(topology.laneAEdges(), 10, 16, 0.40f, validUntilTicks))
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("lane_b_mid")
                            .label("lane_b_mid")
                            .probability(0.20d)
                            .liveUpdates(updatesForRange(topology.laneBEdges(), 5, 11, 0.50f, validUntilTicks))
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("lane_c_early")
                            .label("lane_c_early")
                            .probability(0.10d)
                            .liveUpdates(updatesForRange(topology.laneCEdges(), 2, 7, 0.55f, validUntilTicks))
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("mixed_tail")
                            .label("mixed_tail")
                            .probability(0.10d)
                            .liveUpdates(mergeUpdates(
                                    updatesForRange(topology.laneAEdges(), 11, 16, 0.45f, validUntilTicks),
                                    updatesForRange(topology.laneBEdges(), 8, 14, 0.55f, validUntilTicks)
                            ))
                            .build())
                    .build();
        };
    }

    private List<LiveUpdate> updatesForRange(
            int[] edgeIds,
            int startInclusive,
            int endExclusive,
            float speedFactor,
            long validUntilTicks
    ) {
        ArrayList<LiveUpdate> updates = new ArrayList<>(Math.max(0, endExclusive - startInclusive));
        for (int index = startInclusive; index < endExclusive && index < edgeIds.length; index++) {
            updates.add(LiveUpdate.of(edgeIds[index], speedFactor, validUntilTicks));
        }
        return List.copyOf(updates);
    }

    private List<LiveUpdate> mergeUpdates(List<LiveUpdate> lhs, List<LiveUpdate> rhs) {
        ArrayList<LiveUpdate> merged = new ArrayList<>(lhs.size() + rhs.size());
        merged.addAll(lhs);
        merged.addAll(rhs);
        return List.copyOf(merged);
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source) {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                TopologyRuntimeTemplate.builder()
                        .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                        .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                        .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                        .bucketSizeSeconds(3_600)
                        .liveOverlayCapacity(128)
                        .build()
        );
        return runtimeFactory.buildSnapshot(
                source,
                TopologyVersion.builder()
                        .modelVersion(source.getModelVersion())
                        .topologyVersion("perf-" + source.getModelVersion())
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + source.getModelVersion())
                        .changeSetHash("changes-" + source.getModelVersion())
                        .build(),
                0L,
                null
        );
    }

    private PerfTopology buildPerfTopology(int segmentCount) {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("c3-aggregate-perf-" + segmentCount)
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build());

        builder.node(TopologyModelSource.NodeDefinition.builder().nodeId("S").x(0.0d).y(0.0d).build());
        for (int step = 0; step <= segmentCount; step++) {
            builder.node(TopologyModelSource.NodeDefinition.builder().nodeId(nodeId("A", step)).x(step + 1.0d).y(1.0d).build());
            builder.node(TopologyModelSource.NodeDefinition.builder().nodeId(nodeId("B", step)).x(step + 1.0d).y(0.0d).build());
            builder.node(TopologyModelSource.NodeDefinition.builder().nodeId(nodeId("C", step)).x(step + 1.0d).y(-1.0d).build());
        }
        builder.node(TopologyModelSource.NodeDefinition.builder().nodeId("T").x(segmentCount + 2.0d).y(0.0d).build());

        ArrayList<Integer> laneAEdges = new ArrayList<>(segmentCount + 1);
        ArrayList<Integer> laneBEdges = new ArrayList<>(segmentCount + 1);
        ArrayList<Integer> laneCEdges = new ArrayList<>(segmentCount + 1);
        int nextEdgeIndex = 0;

        nextEdgeIndex = addEdge(builder, nextEdgeIndex, "S_A0", "S", nodeId("A", 0), 0.8f);
        nextEdgeIndex = addEdge(builder, nextEdgeIndex, "S_B0", "S", nodeId("B", 0), 0.9f);
        nextEdgeIndex = addEdge(builder, nextEdgeIndex, "S_C0", "S", nodeId("C", 0), 1.0f);

        for (int step = 0; step < segmentCount; step++) {
            laneAEdges.add(nextEdgeIndex);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "A_" + step, nodeId("A", step), nodeId("A", step + 1), 1.0f);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "A_B_" + step, nodeId("A", step), nodeId("B", step + 1), 0.45f);

            laneBEdges.add(nextEdgeIndex);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "B_" + step, nodeId("B", step), nodeId("B", step + 1), 1.15f);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "B_A_" + step, nodeId("B", step), nodeId("A", step + 1), 0.45f);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "B_C_" + step, nodeId("B", step), nodeId("C", step + 1), 0.45f);

            laneCEdges.add(nextEdgeIndex);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "C_" + step, nodeId("C", step), nodeId("C", step + 1), 1.30f);
            nextEdgeIndex = addEdge(builder, nextEdgeIndex, "C_B_" + step, nodeId("C", step), nodeId("B", step + 1), 0.45f);
        }

        laneAEdges.add(nextEdgeIndex);
        nextEdgeIndex = addEdge(builder, nextEdgeIndex, "A_T", nodeId("A", segmentCount), "T", 1.0f);
        laneBEdges.add(nextEdgeIndex);
        nextEdgeIndex = addEdge(builder, nextEdgeIndex, "B_T", nodeId("B", segmentCount), "T", 1.15f);
        laneCEdges.add(nextEdgeIndex);
        addEdge(builder, nextEdgeIndex, "C_T", nodeId("C", segmentCount), "T", 1.30f);

        return new PerfTopology(
                builder.build(),
                toIntArray(laneAEdges),
                toIntArray(laneBEdges),
                toIntArray(laneCEdges)
        );
    }

    private int addEdge(
            TopologyModelSource.TopologyModelSourceBuilder builder,
            int edgeIndex,
            String edgeId,
            String originNodeId,
            String destinationNodeId,
            float baseWeight
    ) {
        builder.edge(TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(baseWeight)
                .profileId(1)
                .build());
        return edgeIndex + 1;
    }

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private String nodeId(String lane, int step) {
        return lane + step;
    }

    private record PerfTopology(
            TopologyModelSource source,
            int[] laneAEdges,
            int[] laneBEdges,
            int[] laneCEdges
    ) {
    }
}
