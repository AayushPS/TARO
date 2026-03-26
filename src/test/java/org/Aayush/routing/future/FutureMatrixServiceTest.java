package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Future Matrix Service Tests")
class FutureMatrixServiceTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Expected and robust matrix summaries are derived from one scenario bundle")
    void testScenarioAwareMatrixAggregation() {
        RoutingFixtureFactory.Fixture fixture = createAlternativeRouteFixture();
        RouteCore routeCore = createRouteCore(fixture);
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-1");

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-matrix-1")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.6d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("incident_persists")
                                .label("incident_persists")
                                .probability(0.4d)
                                .explanationTag("incident_persists")
                                .liveUpdate(LiveUpdate.of(2, 0.4f, 10_000L))
                                .build())
                        .build();

        InMemoryEphemeralMatrixResultStore store = new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK);
        FutureMatrixService service = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                store
        );

        FutureMatrixResultSet resultSet = service.evaluate(
                snapshot,
                FutureMatrixRequest.builder()
                        .matrixRequest(MatrixRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N2")
                                .targetExternalId("N3")
                                .departureTicks(0L)
                                .build())
                        .horizonTicks(3_600L)
                        .resultTtl(Duration.ofMinutes(10))
                        .build()
        );

        assertEquals("topo-1", resultSet.getTopologyVersion().getTopologyVersion());
        assertEquals("quarantine-topo-1:0", resultSet.getQuarantineSnapshotId());
        assertEquals(2, resultSet.getScenarioResults().size());
        assertEquals(1.0d, resultSet.getAggregate().getReachabilityProbabilities()[0][0], 1.0e-6d);
        assertEquals(1.0d, resultSet.getAggregate().getReachabilityProbabilities()[0][1], 1.0e-6d);
        assertEquals(2.0f, resultSet.getAggregate().getExpectedCosts()[0][0], 0.0001f);
        assertEquals(2.0f, resultSet.getAggregate().getP90Costs()[0][0], 0.0001f);
        assertEquals(2.4f, resultSet.getAggregate().getExpectedCosts()[0][1], 0.0001f);
        assertEquals(3.0f, resultSet.getAggregate().getP90Costs()[0][1], 0.0001f);
        assertEquals("Aggregated over per-scenario shortest-path costs for each matrix cell.",
                resultSet.getAggregate().getAggregationNote());
        assertTrue(store.get(resultSet.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Repeated execution preserves bundle order and per-scenario matrix outputs")
    void testRepeatedExecutionPreservesScenarioOrderAndOutputs() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-deterministic-matrix");
        long departureTicks = 0L;
        long validUntilTicks = 10_000L;

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-deterministic-matrix")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("blocked_branch")
                                .label("blocked_branch")
                                .probability(0.25d)
                                .explanationTag("blocked_branch")
                                .liveUpdate(LiveUpdate.of(2, 0.0f, validUntilTicks))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.75d)
                                .build())
                        .build();

        FutureMatrixService service = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        );
        FutureMatrixRequest request = matrixRequest("N0", "N3", departureTicks);

        FutureMatrixResultSet first = service.evaluate(snapshot, request);
        FutureMatrixResultSet second = service.evaluate(snapshot, request);

        assertEquals("bundle-deterministic-matrix", first.getScenarioBundle().getScenarioBundleId());
        assertEquals(first.getScenarioBundle().getScenarioBundleId(), second.getScenarioBundle().getScenarioBundleId());
        assertNotEquals(first.getResultSetId(), second.getResultSetId());
        assertEquals(
                List.of("blocked_branch", "baseline"),
                first.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList()
        );
        assertEquals(3.0f, first.getScenarioResults().get(0).getMatrix().getTotalCosts()[0][0], 0.0001f);
        assertEquals(2.0f, first.getScenarioResults().get(1).getMatrix().getTotalCosts()[0][0], 0.0001f);
        assertEquals(
                first.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList(),
                second.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                first.getScenarioResults().stream()
                        .map(result -> result.getMatrix().getTotalCosts()[0][0])
                        .toList(),
                second.getScenarioResults().stream()
                        .map(result -> result.getMatrix().getTotalCosts()[0][0])
                        .toList()
        );
    }

    @Test
    @DisplayName("Unreachable matrix scenarios are retained instead of being dropped")
    void testUnreachableScenarioResultsAreRetained() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-unreachable-matrix");
        long departureTicks = 0L;
        long validUntilTicks = 10_000L;

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-unreachable-matrix")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.6d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("all_paths_blocked")
                                .label("all_paths_blocked")
                                .probability(0.4d)
                                .explanationTag("all_paths_blocked")
                                .liveUpdate(LiveUpdate.of(2, 0.0f, validUntilTicks))
                                .liveUpdate(LiveUpdate.of(3, 0.0f, validUntilTicks))
                                .build())
                        .build();

        FutureMatrixResultSet resultSet = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, matrixRequest("N0", "N3", departureTicks));

        assertEquals(
                List.of("baseline", "all_paths_blocked"),
                resultSet.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList()
        );
        assertTrue(resultSet.getScenarioResults().get(0).getMatrix().getReachable()[0][0]);
        assertFalse(resultSet.getScenarioResults().get(1).getMatrix().getReachable()[0][0]);
        assertEquals(0.6d, resultSet.getAggregate().getReachabilityProbabilities()[0][0], 1.0e-6d);
    }

    @Test
    @DisplayName("Scenario execution inherits the active overlay without mutating the base matrix core")
    void testScenarioExecutionInheritsActiveOverlayWithoutMutatingBaseMatrix() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        routeCore.liveOverlayContract().upsert(LiveUpdate.of(1, 0.5f, 10_000L), 0L);
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-live-overlay-matrix");

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-live-overlay-matrix")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.5d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline_blocked")
                                .label("baseline_blocked")
                                .probability(0.5d)
                                .explanationTag("baseline_blocked")
                                .liveUpdate(LiveUpdate.of(2, 0.0f, 10_000L))
                                .build())
                        .build();

        FutureMatrixResultSet resultSet = new FutureMatrixService(
                new FutureMatrixEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, matrixRequest("N0", "N3", 0L));

        assertEquals(2.0f, resultSet.getScenarioResults().get(0).getMatrix().getTotalCosts()[0][0], 0.0001f);
        assertEquals(5.0f, resultSet.getScenarioResults().get(1).getMatrix().getTotalCosts()[0][0], 0.0001f);

        MatrixResponse baseMatrix = routeCore.matrix(MatrixRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N3")
                .departureTicks(0L)
                .build());
        assertEquals(2.0f, baseMatrix.getTotalCosts()[0][0], 0.0001f);
    }

    private RouteCore createRouteCore(RoutingFixtureFactory.Fixture fixture) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-v12")
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("changes-" + topologyId)
                .build();
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private FutureMatrixRequest matrixRequest(String sourceExternalId, String targetExternalId, long departureTicks) {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(3_600L)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private RoutingFixtureFactory.Fixture createAlternativeRouteFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        1.0d, 1.0d,
                        2.0d, 0.5d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }
}
