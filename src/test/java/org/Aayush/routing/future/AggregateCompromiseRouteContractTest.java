package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
@Tag("integration")
@DisplayName("Aggregate Compromise Route Contract Tests")
class AggregateCompromiseRouteContractTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Smoke: aggregate-best compromise route is selected by direct aggregate planning")
    void testAggregateBestCompromiseRouteSelection() {
        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(compromiseResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(
                snapshot(createRouteCore(compromiseFixture()), "c3-compromise-contract"),
                routeRequest("N0", "N4", 0L, 3)
        );

        assertEquals(List.of("N0", "N3", "N4"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getRobustRoute().getRouteSelectionProvenance());
        assertEquals(0.0d, resultSet.getExpectedRoute().getOptimalityProbability(), 1.0e-6d);
        assertEquals(0.0d, resultSet.getRobustRoute().getOptimalityProbability(), 1.0e-6d);
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N3", "N4"))));
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N1", "N4"))));
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N2", "N4"))));
    }

    @Test
    @DisplayName("Smoke: robust winner can differ from expected winner under the same scenario bundle")
    void testRobustWinnerCanDifferFromExpectedWinner() {
        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(divergenceResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(
                snapshot(createRouteCore(alternativeFixture()), "c3-divergence-contract"),
                routeRequest("N0", "N3", 0L, 2)
        );

        assertEquals(List.of("N0", "N1", "N3"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertNotEquals(
                resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds(),
                resultSet.getRobustRoute().getRoute().getPathExternalNodeIds()
        );
    }

    @Test
    @DisplayName("Smoke: aggregate-only winners keep zero optimality probability and stable repeat results")
    void testAggregateOnlyWinnerSemanticsStayStableAcrossRepeats() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(compromiseResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(createRouteCore(compromiseFixture()), "c3-repeat-contract");
        FutureRouteRequest request = routeRequest("N0", "N4", 0L, 3);

        FutureRouteResultSet first = service.evaluate(snapshot, request);
        FutureRouteResultSet second = service.evaluate(snapshot, request);

        assertEquals(0.0d, first.getExpectedRoute().getOptimalityProbability(), 1.0e-6d);
        assertEquals(0.0d, first.getRobustRoute().getOptimalityProbability(), 1.0e-6d);
        assertEquals(first.getExpectedRoute().getRouteSelectionProvenance(), second.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(first.getRobustRoute().getRouteSelectionProvenance(), second.getRobustRoute().getRouteSelectionProvenance());
        assertEquals(
                first.getExpectedRoute().getRoute().getPathExternalNodeIds(),
                second.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                first.getRobustRoute().getRoute().getPathExternalNodeIds(),
                second.getRobustRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(first.getScenarioBundle().getScenarioBundleId(), second.getScenarioBundle().getScenarioBundleId());
    }

    @Test
    @DisplayName("Smoke: dense and sparse candidate families emit distinct density calibration posture")
    void testDenseAndSparseCandidateFamiliesAreCalibratedDifferently() {
        FutureRouteService denseService = new FutureRouteService(
                new FutureRouteEvaluator(compromiseResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureRouteService sparseService = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet dense = denseService.evaluate(
                snapshot(createRouteCore(compromiseFixture()), "c3-density-dense"),
                routeRequest("N0", "N4", 0L, 3)
        );
        FutureRouteResultSet sparse = sparseService.evaluate(
                snapshot(createRouteCore(lineFixture()), "c3-density-sparse"),
                routeRequest("N0", "N2", 0L, 1)
        );

        assertEquals(CandidateDensityClass.HIGH_DENSITY, dense.getCandidateDensityCalibrationReport().getDensityClass());
        assertEquals(1, dense.getCandidateDensityCalibrationReport().getAggregateAddedCandidateCount());
        assertTrue(dense.getCandidateDensityCalibrationReport().isExpectedRouteAggregateOnly());
        assertTrue(dense.getCandidateDensityCalibrationReport().isRobustRouteAggregateOnly());
        assertEquals(CandidateDensityClass.LOW_DENSITY, sparse.getCandidateDensityCalibrationReport().getDensityClass());
        assertEquals(0, sparse.getCandidateDensityCalibrationReport().getAggregateAddedCandidateCount());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, sparse.getExpectedRoute().getRouteSelectionProvenance());
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c3-baseline-contract-bundle")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(1.0d)
                                .build())
                        .build();
    }

    private ScenarioBundleResolver divergenceResolver() {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c3-divergence-bundle")
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
    }

    private ScenarioBundleResolver compromiseResolver() {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c3-compromise-bundle")
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
    }

    private TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(TopologyVersion.builder()
                        .modelVersion("model-c3")
                        .topologyVersion(topologyId)
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + topologyId)
                        .changeSetHash("changes-" + topologyId)
                        .build())
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private FutureRouteRequest routeRequest(String sourceExternalId, String targetExternalId, long departureTicks, int topKAlternatives) {
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

    private RoutingFixtureFactory.Fixture lineFixture() {
        return RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
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

    private RoutingFixtureFactory.Fixture alternativeFixture() {
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

    private RoutingFixtureFactory.Fixture compromiseFixture() {
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
