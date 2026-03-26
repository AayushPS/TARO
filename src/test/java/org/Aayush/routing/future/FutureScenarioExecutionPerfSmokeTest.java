package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("perf")
@Tag("integration")
@DisplayName("Future Scenario Execution Perf Smoke Tests")
class FutureScenarioExecutionPerfSmokeTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final int EDGE_COUNT = 192;
    private static final int WARMUP_LOOPS = 8;
    private static final int ROUTE_LOOPS = 40;
    private static final int MATRIX_LOOPS = 12;
    private static final double MAX_AVG_MICROS = 40_000.0d;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Perf smoke: scenario-specific route and matrix execution remain practical on a bounded corridor")
    void testScenarioExecutionPerfSmoke() {
        TopologyRuntimeSnapshot snapshot = snapshot(lineSource(EDGE_COUNT));
        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(perfResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(perfResolver(), FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        );

        FutureRouteRequest routeRequest = FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N192")
                        .departureTicks(0L)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .topKAlternatives(3)
                .resultTtl(Duration.ofMinutes(10))
                .build();
        FutureMatrixRequest matrixRequest = FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .sourceExternalId("N32")
                        .sourceExternalId("N64")
                        .targetExternalId("N128")
                        .targetExternalId("N160")
                        .targetExternalId("N192")
                        .departureTicks(0L)
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .build();

        for (int i = 0; i < WARMUP_LOOPS; i++) {
            routeService.evaluate(snapshot, routeRequest);
            matrixService.evaluate(snapshot, matrixRequest);
        }

        long startedAtNanos = System.nanoTime();
        FutureRouteResultSet lastRoute = null;
        FutureMatrixResultSet lastMatrix = null;
        for (int i = 0; i < ROUTE_LOOPS; i++) {
            lastRoute = routeService.evaluate(snapshot, routeRequest);
        }
        for (int i = 0; i < MATRIX_LOOPS; i++) {
            lastMatrix = matrixService.evaluate(snapshot, matrixRequest);
        }
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        double avgMicros = (double) elapsedNanos / (double) (ROUTE_LOOPS + MATRIX_LOOPS) / 1_000.0d;

        assertTrue(lastRoute != null);
        assertTrue(lastMatrix != null);
        assertEquals("bundle-scenario-execution-perf", lastRoute.getScenarioBundle().getScenarioBundleId());
        assertEquals(lastRoute.getScenarioBundle().getScenarioBundleId(), lastMatrix.getScenarioBundle().getScenarioBundleId());
        assertEquals(lastRoute.getScenarioBundle().getGeneratedAt(), lastMatrix.getScenarioBundle().getGeneratedAt());
        assertEquals(lastRoute.getScenarioBundle().getValidUntil(), lastMatrix.getScenarioBundle().getValidUntil());
        assertEquals(lastRoute.getScenarioBundle().getHorizonTicks(), lastMatrix.getScenarioBundle().getHorizonTicks());
        assertEquals(
                lastRoute.getScenarioBundle().getTopologyVersion().getTopologyVersion(),
                lastMatrix.getScenarioBundle().getTopologyVersion().getTopologyVersion()
        );
        assertEquals(
                lastRoute.getScenarioBundle().getQuarantineSnapshotId(),
                lastMatrix.getScenarioBundle().getQuarantineSnapshotId()
        );
        assertEquals(
                List.of("baseline", "corridor_slow_a", "corridor_slow_b", "corridor_slow_c"),
                lastRoute.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                lastRoute.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList(),
                lastMatrix.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList()
        );
        assertEquals(4, lastRoute.getScenarioResults().size());
        assertEquals(4, lastMatrix.getScenarioResults().size());
        assertTrue(lastRoute.getScenarioResults().stream().allMatch(result -> result.getRoute().isReachable()));
        assertTrue(lastMatrix.getScenarioResults().stream()
                .allMatch(result -> allCellsReachable(result.getMatrix().getReachable())));
        assertTrue(avgMicros < MAX_AVG_MICROS,
                "average scenario execution should stay below " + MAX_AVG_MICROS + "us in this smoke test");
    }

    private boolean allCellsReachable(boolean[][] reachable) {
        for (boolean[] row : reachable) {
            for (boolean cell : row) {
                if (!cell) {
                    return false;
                }
            }
        }
        return true;
    }

    private ScenarioBundleResolver perfResolver() {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-scenario-execution-perf")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.4d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("corridor_slow_a")
                                .label("corridor_slow_a")
                                .probability(0.25d)
                                .liveUpdate(LiveUpdate.of(24, 0.5f, 10_000L))
                                .liveUpdate(LiveUpdate.of(25, 0.5f, 10_000L))
                                .liveUpdate(LiveUpdate.of(26, 0.5f, 10_000L))
                                .liveUpdate(LiveUpdate.of(27, 0.5f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("corridor_slow_b")
                                .label("corridor_slow_b")
                                .probability(0.2d)
                                .liveUpdate(LiveUpdate.of(72, 0.4f, 10_000L))
                                .liveUpdate(LiveUpdate.of(73, 0.4f, 10_000L))
                                .liveUpdate(LiveUpdate.of(74, 0.4f, 10_000L))
                                .liveUpdate(LiveUpdate.of(75, 0.4f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("corridor_slow_c")
                                .label("corridor_slow_c")
                                .probability(0.15d)
                                .liveUpdate(LiveUpdate.of(120, 0.6f, 10_000L))
                                .liveUpdate(LiveUpdate.of(121, 0.6f, 10_000L))
                                .liveUpdate(LiveUpdate.of(122, 0.6f, 10_000L))
                                .liveUpdate(LiveUpdate.of(123, 0.6f, 10_000L))
                                .build())
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
                        .topologyVersion("perf-" + source.getModelVersion())
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + source.getModelVersion())
                        .changeSetHash("change-" + source.getModelVersion())
                        .build(),
                0L,
                null
        );
    }

    private TopologyModelSource lineSource(int edgeCount) {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("scenario-execution-perf-" + edgeCount)
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build());
        for (int i = 0; i <= edgeCount; i++) {
            builder.node(TopologyModelSource.NodeDefinition.builder()
                    .nodeId("N" + i)
                    .x((double) i)
                    .y(0.0d)
                    .build());
        }
        for (int i = 0; i < edgeCount; i++) {
            builder.edge(TopologyModelSource.EdgeDefinition.builder()
                    .edgeId("E" + i)
                    .originNodeId("N" + i)
                    .destinationNodeId("N" + (i + 1))
                    .baseWeight(1.0f + (i % 5))
                    .profileId(1)
                    .build());
        }
        return builder.build();
    }
}
