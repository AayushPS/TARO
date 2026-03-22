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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Density Calibration Tests")
class DensityCalibrationTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Single-family serving emits a low-density calibration report without aggregate rescue")
    void testLowDensityServingReport() {
        TopologyRuntimeSnapshot snapshot = snapshot(lineFixture(), "density-low");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet resultSet = service.evaluate(snapshot, request("N0", "N2", 0L, 2));

        assertEquals(List.of("N0", "N1", "N2"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, resultSet.getRobustRoute().getRouteSelectionProvenance());

        CandidateDensityCalibrationReport report = resultSet.getCandidateDensityCalibrationReport();
        assertNotNull(report);
        assertEquals("b5-density-v2", report.getPolicyId());
        assertEquals(1, report.getScenarioCount());
        assertEquals(1, report.getScenarioOptimalRouteCount());
        assertEquals(1, report.getUniqueScenarioOptimalRouteCount());
        assertEquals(1, report.getUniqueCandidateRouteCount());
        assertEquals(0, report.getAggregateAddedCandidateCount());
        assertFalse(report.isExpectedRouteAggregateOnly());
        assertFalse(report.isRobustRouteAggregateOnly());
        assertEquals(1, report.getSelectedAlternativeCount());
        assertEquals(1.0d, report.getScenarioCoverageRatio(), 1.0e-9d);
        assertEquals(1.0d, report.getCandidateCoverageRatio(), 1.0e-9d);
        assertEquals(0.0d, report.getAggregateExpansionRatio(), 1.0e-9d);
        assertEquals(CandidateDensityClass.LOW_DENSITY, report.getDensityClass());
    }

    @Test
    @DisplayName("Duplicate scenario winners are calibrated as low density rather than healthy multi-family coverage")
    void testDuplicateScenarioWinnerCollapseStaysLowDensity() {
        TopologyRuntimeSnapshot snapshot = snapshot(lineFixture(), "density-collapsed");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(collapsedResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet resultSet = service.evaluate(snapshot, request("N0", "N2", 0L, 2));

        CandidateDensityCalibrationReport report = resultSet.getCandidateDensityCalibrationReport();
        assertEquals(2, report.getScenarioCount());
        assertEquals(2, report.getScenarioOptimalRouteCount());
        assertEquals(1, report.getUniqueScenarioOptimalRouteCount());
        assertEquals(1, report.getUniqueCandidateRouteCount());
        assertEquals(0, report.getAggregateAddedCandidateCount());
        assertEquals(0.5d, report.getScenarioCoverageRatio(), 1.0e-9d);
        assertEquals(1.0d, report.getCandidateCoverageRatio(), 1.0e-9d);
        assertEquals(0.0d, report.getAggregateExpansionRatio(), 1.0e-9d);
        assertEquals(CandidateDensityClass.LOW_DENSITY, report.getDensityClass());
    }

    @Test
    @DisplayName("Aggregate-only compromise winners emit a high-density rescue report")
    void testAggregateOnlyCompromiseRouteProducesDensityRescueReport() {
        TopologyRuntimeSnapshot snapshot = snapshot(compromiseFixture(), "density-compromise");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(compromiseResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet resultSet = service.evaluate(snapshot, request("N0", "N4", 0L, 3));

        assertEquals(List.of("N0", "N3", "N4"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getRobustRoute().getRouteSelectionProvenance());

        CandidateDensityCalibrationReport report = resultSet.getCandidateDensityCalibrationReport();
        assertNotNull(report);
        assertEquals(2, report.getScenarioCount());
        assertEquals(2, report.getScenarioOptimalRouteCount());
        assertEquals(2, report.getUniqueScenarioOptimalRouteCount());
        assertEquals(3, report.getUniqueCandidateRouteCount());
        assertEquals(1, report.getAggregateAddedCandidateCount());
        assertTrue(report.isExpectedRouteAggregateOnly());
        assertTrue(report.isRobustRouteAggregateOnly());
        assertEquals(3, report.getSelectedAlternativeCount());
        assertEquals(1.0d, report.getScenarioCoverageRatio(), 1.0e-9d);
        assertEquals(1.5d, report.getCandidateCoverageRatio(), 1.0e-9d);
        assertEquals(0.5d, report.getAggregateExpansionRatio(), 1.0e-9d);
        assertEquals(CandidateDensityClass.HIGH_DENSITY, report.getDensityClass());
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("baseline-bundle")
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

    private ScenarioBundleResolver compromiseResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("compromise-bundle")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
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

    private ScenarioBundleResolver collapsedResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("collapsed-bundle")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline-a")
                                .label("baseline-a")
                                .probability(0.5d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline-b")
                                .label("baseline-b")
                                .probability(0.5d)
                                .build())
                        .build();
    }

    private FutureRouteRequest request(String sourceExternalId, String targetExternalId, long departureTicks, int topKAlternatives) {
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

    private TopologyRuntimeSnapshot snapshot(RoutingFixtureFactory.Fixture fixture, String topologyId) {
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore(fixture))
                .topologyVersion(TopologyVersion.builder()
                        .modelVersion("density-model")
                        .topologyVersion(topologyId)
                        .generatedAt(FIXED_CLOCK.instant())
                        .sourceDataLineageHash("lineage-" + topologyId)
                        .changeSetHash("changes-" + topologyId)
                        .build())
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private RouteCore routeCore(RoutingFixtureFactory.Fixture fixture) {
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
