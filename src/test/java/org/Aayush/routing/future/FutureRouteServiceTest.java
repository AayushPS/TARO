package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
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
@DisplayName("Future Route Service Tests")
class FutureRouteServiceTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Expected, robust, and top-K routes are derived from one scenario bundle")
    void testExpectedRobustAndTopKAggregation() {
        RoutingFixtureFactory.Fixture fixture = createAlternativeRouteFixture();
        RouteCore routeCore = createRouteCore(fixture);
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-v12")
                .topologyVersion("topo-1")
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-1")
                .changeSetHash("changes-1")
                .build();
        TopologyRuntimeSnapshot snapshot = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-topo-1"))
                .build();

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-1")
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

        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                store
        );

        FutureRouteResultSet resultSet = service.evaluate(
                snapshot,
                FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N3")
                                .departureTicks(0L)
                                .build())
                        .horizonTicks(3_600L)
                        .topKAlternatives(2)
                        .resultTtl(Duration.ofMinutes(10))
                        .build()
        );

        assertEquals("topo-1", resultSet.getTopologyVersion().getTopologyVersion());
        assertEquals("quarantine-topo-1:0", resultSet.getQuarantineSnapshotId());
        assertEquals(List.of("N0", "N1", "N3"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2.6f, resultSet.getExpectedRoute().getExpectedCost(), 0.0001f);
        assertEquals(3.0f, resultSet.getRobustRoute().getP90Cost(), 0.0001f);
        assertEquals(0.6d, resultSet.getExpectedRoute().getOptimalityProbability(), 1.0e-9d);
        assertEquals(0.6d, resultSet.getExpectedRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertEquals("baseline", resultSet.getExpectedRoute().getDominantScenarioId());
        assertTrue(resultSet.getExpectedRoute().getExpectedRegret() > 0.0f);
        assertEquals(resultSet.getExpectedRoute().getMinArrivalTicks(), resultSet.getExpectedRoute().getEtaBandLowerArrivalTicks());
        assertEquals(resultSet.getExpectedRoute().getMaxArrivalTicks(), resultSet.getExpectedRoute().getEtaBandUpperArrivalTicks());
        assertEquals(0.4d, resultSet.getRobustRoute().getOptimalityProbability(), 1.0e-9d);
        assertEquals(0.4d, resultSet.getRobustRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertEquals("incident_persists", resultSet.getRobustRoute().getDominantScenarioId());
        assertTrue(resultSet.getRobustRoute().getExplanationTags().contains("incident_persists"));
        assertTrue(resultSet.getRobustRoute().getExpectedRegret() > resultSet.getExpectedRoute().getExpectedRegret());
        assertEquals(resultSet.getRobustRoute().getMinArrivalTicks(), resultSet.getRobustRoute().getEtaBandLowerArrivalTicks());
        assertEquals(resultSet.getRobustRoute().getMaxArrivalTicks(), resultSet.getRobustRoute().getEtaBandUpperArrivalTicks());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, resultSet.getRobustRoute().getRouteSelectionProvenance());
        assertEquals(2, resultSet.getAlternatives().size());
        assertEquals(List.of("N0", "N1", "N3"), resultSet.getAlternatives().get(0).getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), resultSet.getAlternatives().get(1).getRoute().getPathExternalNodeIds());
        assertEquals(2, resultSet.getScenarioResults().size());
        assertEquals(CandidateDensityClass.HIGH_DENSITY, resultSet.getCandidateDensityCalibrationReport().getDensityClass());
        assertEquals(1.0d, resultSet.getCandidateDensityCalibrationReport().getScenarioCoverageRatio(), 1.0e-9d);
        assertEquals(1.0d, resultSet.getCandidateDensityCalibrationReport().getCandidateCoverageRatio(), 1.0e-9d);
        assertEquals(0.0d, resultSet.getCandidateDensityCalibrationReport().getAggregateExpansionRatio(), 1.0e-9d);
        assertTrue(store.get(resultSet.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Expected and robust winners can be aggregate-only compromise routes")
    void testAggregateOnlyCompromiseRouteSelection() {
        RoutingFixtureFactory.Fixture fixture = createCompromiseRouteFixture();
        RouteCore routeCore = createRouteCore(fixture);
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-compromise");

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-compromise")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("b_slow")
                                .label("b_slow")
                                .probability(0.5d)
                                .explanationTag("b_slow")
                                .liveUpdate(LiveUpdate.of(1, 0.1f, 10_000L))
                                .liveUpdate(LiveUpdate.of(4, 0.1f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("a_slow")
                                .label("a_slow")
                                .probability(0.5d)
                                .explanationTag("a_slow")
                                .liveUpdate(LiveUpdate.of(0, 0.1f, 10_000L))
                                .liveUpdate(LiveUpdate.of(3, 0.1f, 10_000L))
                                .build())
                        .build();

        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                store
        );

        FutureRouteResultSet resultSet = service.evaluate(
                snapshot,
                FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N4")
                                .departureTicks(0L)
                                .build())
                        .horizonTicks(3_600L)
                        .topKAlternatives(3)
                        .resultTtl(Duration.ofMinutes(10))
                        .build()
        );

        assertEquals(List.of("N0", "N3", "N4"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(40.0f, resultSet.getExpectedRoute().getExpectedCost(), 0.0001f);
        assertEquals(40.0f, resultSet.getRobustRoute().getP90Cost(), 0.0001f);
        assertEquals(0.0d, resultSet.getExpectedRoute().getOptimalityProbability(), 1.0e-6d);
        assertEquals(0.0d, resultSet.getRobustRoute().getOptimalityProbability(), 1.0e-6d);
        assertEquals("b_slow", resultSet.getExpectedRoute().getDominantScenarioId());
        assertEquals(0.5d, resultSet.getExpectedRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertEquals(0.5d, resultSet.getRobustRoute().getDominantScenarioProbability(), 1.0e-9d);
        assertTrue(resultSet.getExpectedRoute().getExplanationTags().contains("b_slow"));
        assertTrue(resultSet.getExpectedRoute().getExpectedRegret() > 0.0f);
        assertTrue(resultSet.getRobustRoute().getExpectedRegret() > 0.0f);
        assertEquals(resultSet.getExpectedRoute().getMinArrivalTicks(), resultSet.getExpectedRoute().getEtaBandLowerArrivalTicks());
        assertEquals(resultSet.getExpectedRoute().getMaxArrivalTicks(), resultSet.getExpectedRoute().getEtaBandUpperArrivalTicks());
        assertEquals(resultSet.getRobustRoute().getMinArrivalTicks(), resultSet.getRobustRoute().getEtaBandLowerArrivalTicks());
        assertEquals(resultSet.getRobustRoute().getMaxArrivalTicks(), resultSet.getRobustRoute().getEtaBandUpperArrivalTicks());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getRobustRoute().getRouteSelectionProvenance());
        assertTrue(resultSet.getCandidateDensityCalibrationReport().isExpectedRouteAggregateOnly());
        assertTrue(resultSet.getCandidateDensityCalibrationReport().isRobustRouteAggregateOnly());
        assertEquals(1, resultSet.getCandidateDensityCalibrationReport().getAggregateAddedCandidateCount());
        assertEquals(CandidateDensityClass.HIGH_DENSITY, resultSet.getCandidateDensityCalibrationReport().getDensityClass());
        assertEquals(1.0d, resultSet.getCandidateDensityCalibrationReport().getScenarioCoverageRatio(), 1.0e-9d);
        assertEquals(1.5d, resultSet.getCandidateDensityCalibrationReport().getCandidateCoverageRatio(), 1.0e-9d);
        assertEquals(0.5d, resultSet.getCandidateDensityCalibrationReport().getAggregateExpansionRatio(), 1.0e-9d);
        assertEquals(3, resultSet.getAlternatives().size());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getAlternatives().get(0).getRoute().getPathExternalNodeIds());
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N1", "N4"))));
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N2", "N4"))));
        assertTrue(store.get(resultSet.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Repeated execution preserves bundle order and per-scenario route outputs")
    void testRepeatedExecutionPreservesScenarioOrderAndOutputs() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-deterministic-route");
        long departureTicks = 0L;
        long validUntilTicks = 10_000L;

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-deterministic-route")
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

        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureRouteRequest request = routeRequest("N0", "N3", departureTicks, 2);

        FutureRouteResultSet first = service.evaluate(snapshot, request);
        FutureRouteResultSet second = service.evaluate(snapshot, request);

        assertEquals("bundle-deterministic-route", first.getScenarioBundle().getScenarioBundleId());
        assertEquals(first.getScenarioBundle().getScenarioBundleId(), second.getScenarioBundle().getScenarioBundleId());
        assertNotEquals(first.getResultSetId(), second.getResultSetId());
        assertEquals(
                List.of("blocked_branch", "baseline"),
                first.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                List.of(List.of("N0", "N2", "N3"), List.of("N0", "N1", "N3")),
                first.getScenarioResults().stream().map(result -> result.getRoute().getPathExternalNodeIds()).toList()
        );
        assertEquals(
                first.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList(),
                second.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertEquals(
                first.getScenarioResults().stream().map(result -> result.getRoute().getPathExternalNodeIds()).toList(),
                second.getScenarioResults().stream().map(result -> result.getRoute().getPathExternalNodeIds()).toList()
        );
        assertEquals(
                first.getExpectedRoute().getRoute().getPathExternalNodeIds(),
                second.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                first.getRobustRoute().getRoute().getPathExternalNodeIds(),
                second.getRobustRoute().getRoute().getPathExternalNodeIds()
        );
    }

    @Test
    @DisplayName("Unreachable scenarios are retained in scenario order instead of being dropped")
    void testUnreachableScenarioResultsAreRetained() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-unreachable-route");
        long departureTicks = 0L;
        long validUntilTicks = 10_000L;

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-unreachable-route")
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

        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, routeRequest("N0", "N3", departureTicks, 2));

        assertEquals(
                List.of("baseline", "all_paths_blocked"),
                resultSet.getScenarioResults().stream().map(FutureRouteScenarioResult::getScenarioId).toList()
        );
        assertTrue(resultSet.getScenarioResults().get(0).getRoute().isReachable());
        assertFalse(resultSet.getScenarioResults().get(1).getRoute().isReachable());
        assertEquals(List.of("N0", "N1", "N3"), resultSet.getScenarioResults().get(0).getRoute().getPathExternalNodeIds());
        assertEquals("baseline", resultSet.getExpectedRoute().getDominantScenarioId());
    }

    @Test
    @DisplayName("Scenario execution inherits the active overlay without mutating the base route core")
    void testScenarioExecutionInheritsActiveOverlayWithoutMutatingBaseRoute() {
        RouteCore routeCore = createRouteCore(createAlternativeRouteFixture());
        routeCore.liveOverlayContract().upsert(LiveUpdate.of(1, 0.5f, 10_000L), 0L);
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, "topo-live-overlay-route");

        ScenarioBundleResolver resolver = (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("bundle-live-overlay-route")
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

        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(resolver, FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(snapshot, routeRequest("N0", "N3", 0L, 2));

        assertEquals(List.of("N0", "N1", "N3"), resultSet.getScenarioResults().get(0).getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), resultSet.getScenarioResults().get(1).getRoute().getPathExternalNodeIds());
        assertEquals(2.0f, resultSet.getScenarioResults().get(0).getRoute().getTotalCost(), 0.0001f);
        assertEquals(5.0f, resultSet.getScenarioResults().get(1).getRoute().getTotalCost(), 0.0001f);

        RouteResponse baseRoute = routeCore.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N3")
                .departureTicks(0L)
                .build());
        assertEquals(List.of("N0", "N1", "N3"), baseRoute.getPathExternalNodeIds());
        assertEquals(2.0f, baseRoute.getTotalCost(), 0.0001f);
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

    private FutureRouteRequest routeRequest(
            String sourceExternalId,
            String targetExternalId,
            long departureTicks,
            int topKAlternatives
    ) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(3_600L)
                .topKAlternatives(topKAlternatives)
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

    private RoutingFixtureFactory.Fixture createCompromiseRouteFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 3, 4, 5, 6, 6},
                new int[]{1, 2, 3, 4, 4, 4},
                new int[]{0, 0, 0, 1, 2, 3},
                new float[]{5.0f, 5.0f, 20.0f, 5.0f, 5.0f, 20.0f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, 0.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d
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
