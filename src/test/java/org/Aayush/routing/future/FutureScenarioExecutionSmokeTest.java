package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
@Tag("integration")
@DisplayName("Future Scenario Execution Smoke Tests")
class FutureScenarioExecutionSmokeTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Smoke: route and matrix execution preserve scenario order, divergence, and unreachable retention")
    void testRouteAndMatrixExecutionSmoke() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "scenario-execution-smoke");
        InMemoryEphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        InMemoryEphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK);

        FutureRouteService routeService = new FutureRouteService(
                new FutureRouteEvaluator(smokeResolver(), FIXED_CLOCK),
                routeStore
        );
        FutureMatrixService matrixService = new FutureMatrixService(
                new FutureMatrixEvaluator(smokeResolver(), FIXED_CLOCK),
                matrixStore
        );

        FutureRouteResultSet routeResult = routeService.evaluate(snapshot, routeRequest());
        FutureMatrixResultSet matrixResult = matrixService.evaluate(snapshot, matrixRequest());
        List<ScenarioDefinition> bundleScenarios = routeResult.getScenarioBundle().getScenarios();

        assertEquals(routeResult.getScenarioBundle().getScenarioBundleId(), matrixResult.getScenarioBundle().getScenarioBundleId());
        assertEquals(routeResult.getTopologyVersion(), matrixResult.getTopologyVersion());
        assertEquals(routeResult.getQuarantineSnapshotId(), matrixResult.getQuarantineSnapshotId());
        assertEquals(routeResult.getTopologyVersion(), routeResult.getScenarioBundle().getTopologyVersion());
        assertEquals(routeResult.getQuarantineSnapshotId(), routeResult.getScenarioBundle().getQuarantineSnapshotId());
        assertEquals(routeRequest().getHorizonTicks(), routeResult.getScenarioBundle().getHorizonTicks());
        assertEquals(routeResult.getScenarioBundle().getGeneratedAt(), matrixResult.getScenarioBundle().getGeneratedAt());
        assertEquals(routeResult.getScenarioBundle().getValidUntil(), matrixResult.getScenarioBundle().getValidUntil());
        assertEquals(routeResult.getScenarioBundle().getHorizonTicks(), matrixResult.getScenarioBundle().getHorizonTicks());
        assertEquals(
                routeResult.getScenarioBundle().getTopologyVersion().getTopologyVersion(),
                matrixResult.getScenarioBundle().getTopologyVersion().getTopologyVersion()
        );
        assertEquals(
                routeResult.getScenarioBundle().getQuarantineSnapshotId(),
                matrixResult.getScenarioBundle().getQuarantineSnapshotId()
        );
        assertEquals(
                List.of("blocked_branch", "baseline", "all_paths_blocked"),
                routeResult.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                bundleScenarios.stream().map(ScenarioDefinition::getScenarioId).toList(),
                routeResult.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                routeResult.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList(),
                matrixResult.getScenarioResults().stream().map(FutureMatrixScenarioResult::getScenarioId).toList()
        );
        for (int index = 0; index < bundleScenarios.size(); index++) {
            assertRouteMetadataMatchesBundle(bundleScenarios.get(index), routeResult.getScenarioResults().get(index));
            assertMatrixMetadataMatchesBundle(bundleScenarios.get(index), matrixResult.getScenarioResults().get(index));
        }

        assertEquals(List.of("N0", "N2", "N3"), routeResult.getScenarioResults().get(0).getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), routeResult.getScenarioResults().get(1).getRoute().getPathExternalNodeIds());
        assertFalse(routeResult.getScenarioResults().get(2).getRoute().isReachable());

        assertEquals(3.0f, matrixResult.getScenarioResults().get(0).getMatrix().getTotalCosts()[0][0], 0.0001f);
        assertEquals(2.0f, matrixResult.getScenarioResults().get(1).getMatrix().getTotalCosts()[0][0], 0.0001f);
        assertFalse(matrixResult.getScenarioResults().get(2).getMatrix().getReachable()[0][0]);
        assertEquals(0.8d, matrixResult.getAggregate().getReachabilityProbabilities()[0][0], 1.0e-6d);

        assertTrue(routeStore.get(routeResult.getResultSetId()).isPresent());
        assertTrue(matrixStore.get(matrixResult.getResultSetId()).isPresent());
    }

    private void assertRouteMetadataMatchesBundle(
            ScenarioDefinition expected,
            FutureRouteScenarioResult actual
    ) {
        assertEquals(expected.getScenarioId(), actual.getScenarioId());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getProbability(), actual.getProbability(), 1.0e-9d);
        assertIterableEquals(expected.getExplanationTags(), actual.getExplanationTags());
    }

    private void assertMatrixMetadataMatchesBundle(
            ScenarioDefinition expected,
            FutureMatrixScenarioResult actual
    ) {
        assertEquals(expected.getScenarioId(), actual.getScenarioId());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getProbability(), actual.getProbability(), 1.0e-9d);
        assertIterableEquals(expected.getExplanationTags(), actual.getExplanationTags());
    }

    private ScenarioBundleResolver smokeResolver() {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-scenario-execution-smoke")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("blocked_branch")
                                .label("blocked_branch")
                                .probability(0.3d)
                                .explanationTag("blocked_branch")
                                .liveUpdate(LiveUpdate.of(2, 0.0f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.5d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("all_paths_blocked")
                                .label("all_paths_blocked")
                                .probability(0.2d)
                                .explanationTag("all_paths_blocked")
                                .liveUpdate(LiveUpdate.of(2, 0.0f, 10_000L))
                                .liveUpdate(LiveUpdate.of(3, 0.0f, 10_000L))
                                .build())
                        .build();
    }

    private FutureRouteRequest routeRequest() {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(0L)
                        .build())
                .horizonTicks(3_600L)
                .topKAlternatives(2)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private FutureMatrixRequest matrixRequest() {
        return FutureMatrixRequest.builder()
                .matrixRequest(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(0L)
                        .build())
                .horizonTicks(3_600L)
                .resultTtl(Duration.ofMinutes(10))
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
