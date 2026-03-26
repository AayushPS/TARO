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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Alternative Confidence Contract Tests")
class AlternativeConfidenceContractTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Distinct expected and robust winners retain confidence tags, regret, and ETA bands")
    void testDistinctExpectedAndRobustWinnersRetainConfidenceAndEtaBandFields() {
        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(highLowConfidenceResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(
                snapshot(createRouteCore(alternativeFixture()), "c4-confidence-divergence"),
                routeRequest("N0", "N3", 0L, 2)
        );

        ScenarioRouteSelection expected = resultSet.getExpectedRoute();
        ScenarioRouteSelection robust = resultSet.getRobustRoute();

        assertEquals(List.of("N0", "N1", "N3"), expected.getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), robust.getRoute().getPathExternalNodeIds());
        assertEquals(0.85d, expected.getOptimalityProbability(), 1.0e-9d);
        assertEquals(0.85d, expected.getDominantScenarioProbability(), 1.0e-9d);
        assertTrue(expected.getExplanationTags().contains("recurrent_confidence_high"));
        assertTrue(expected.getExpectedRegret() > 0.0f);
        assertEquals(expected.getMinArrivalTicks(), expected.getEtaBandLowerArrivalTicks());
        assertEquals(expected.getMaxArrivalTicks(), expected.getEtaBandUpperArrivalTicks());

        assertEquals(0.15d, robust.getOptimalityProbability(), 1.0e-9d);
        assertEquals(0.15d, robust.getDominantScenarioProbability(), 1.0e-9d);
        assertTrue(robust.getExplanationTags().contains("recurrent_confidence_low"));
        assertTrue(robust.getExpectedRegret() > expected.getExpectedRegret());
        assertEquals(robust.getMinArrivalTicks(), robust.getEtaBandLowerArrivalTicks());
        assertEquals(robust.getMaxArrivalTicks(), robust.getEtaBandUpperArrivalTicks());

        assertEquals(2, resultSet.getAlternatives().size());
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(expected.getRoute().getPathExternalNodeIds())));
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(robust.getRoute().getPathExternalNodeIds())));
    }

    @Test
    @DisplayName("Aggregate-only winners keep zero optimality probability and positive regret")
    void testAggregateOnlyWinnerRetainsZeroOptimalityProbabilityAndPositiveRegret() {
        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(compromiseResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(
                snapshot(createRouteCore(compromiseFixture()), "c4-aggregate-only"),
                routeRequest("N0", "N4", 0L, 3)
        );

        ScenarioRouteSelection expected = resultSet.getExpectedRoute();
        ScenarioRouteSelection robust = resultSet.getRobustRoute();

        assertEquals(List.of("N0", "N3", "N4"), expected.getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), robust.getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, expected.getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, robust.getRouteSelectionProvenance());
        assertEquals(0.0d, expected.getOptimalityProbability(), 1.0e-9d);
        assertEquals(0.0d, robust.getOptimalityProbability(), 1.0e-9d);
        assertTrue(expected.getExpectedRegret() > 0.0f);
        assertTrue(robust.getExpectedRegret() > 0.0f);
        assertEquals(0.5d, expected.getDominantScenarioProbability(), 1.0e-9d);
        assertEquals(0.5d, robust.getDominantScenarioProbability(), 1.0e-9d);
        assertEquals("b_slow", expected.getDominantScenarioId());
        assertTrue(expected.getExplanationTags().contains("b_slow"));
        assertEquals(expected.getMinArrivalTicks(), expected.getEtaBandLowerArrivalTicks());
        assertEquals(expected.getMaxArrivalTicks(), expected.getEtaBandUpperArrivalTicks());
    }

    @Test
    @DisplayName("Near-identical alternatives deduplicate without dropping the distinct aggregate winner")
    void testNearIdenticalAlternativesDeduplicateWithoutDroppingDistinctWinner() {
        FutureRouteResultSet resultSet = new FutureRouteService(
                new FutureRouteEvaluator(nearIdenticalAlternativeResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        ).evaluate(
                snapshot(createRouteCore(nearIdenticalFixture()), "c4-near-identical"),
                routeRequest("N0", "N13", 0L, 3)
        );

        List<String> chainPath = chainPathNodes();
        List<String> branchPath = List.of("N0", "N14", "N15", "N16", "N17", "N13");

        assertEquals(branchPath, resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(branchPath, resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2, resultSet.getAlternatives().size());
        assertEquals(1L, resultSet.getAlternatives().stream()
                .filter(selection -> selection.getRoute().getPathExternalNodeIds().equals(chainPath))
                .count());
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(branchPath)));
    }

    private ScenarioBundleResolver highLowConfidenceResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c4-confidence-bundle")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline_high")
                                .label("baseline_high")
                                .probability(0.85d)
                                .explanationTag("recurrent_confidence_high")
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("incident_low")
                                .label("incident_low")
                                .probability(0.15d)
                                .explanationTag("recurrent_confidence_low")
                                .liveUpdate(LiveUpdate.of(2, 0.4f, 10_000L))
                                .build())
                        .build();
    }

    private ScenarioBundleResolver compromiseResolver() {
        return (request, baseCostEngine, temporalContext, resolvedTopologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c4-compromise-bundle")
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

    private ScenarioBundleResolver nearIdenticalAlternativeResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("c4-near-identical-bundle")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("main_corridor")
                                .label("main_corridor")
                                .probability(0.25d)
                                .liveUpdate(LiveUpdate.of(8, 0.1f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("parallel_corridor")
                                .label("parallel_corridor")
                                .probability(0.25d)
                                .liveUpdate(LiveUpdate.of(7, 0.1f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("branch_corridor")
                                .label("branch_corridor")
                                .probability(0.5d)
                                .liveUpdate(LiveUpdate.of(7, 0.1f, 10_000L))
                                .liveUpdate(LiveUpdate.of(8, 0.1f, 10_000L))
                                .build())
                        .build();
    }

    private TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(TopologyVersion.builder()
                        .modelVersion("model-c4")
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

    private RoutingFixtureFactory.Fixture nearIdenticalFixture() {
        return RoutingFixtureFactory.createFixture(
                18,
                new int[]{0, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 15, 16, 17, 18, 19},
                new int[]{1, 14, 2, 3, 4, 5, 6, 7, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 13},
                new int[]{0, 0, 1, 2, 3, 4, 5, 6, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17},
                new float[]{1.0f, 3.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 3.0f, 3.0f, 3.0f, 3.0f},
                new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                nearIdenticalCoordinates(),
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private double[] nearIdenticalCoordinates() {
        double[] coordinates = new double[18 * 2];
        for (int i = 0; i <= 13; i++) {
            coordinates[i * 2] = i;
            coordinates[(i * 2) + 1] = 0.0d;
        }
        coordinates[28] = 3.0d;
        coordinates[29] = 2.0d;
        coordinates[30] = 6.0d;
        coordinates[31] = 2.0d;
        coordinates[32] = 9.0d;
        coordinates[33] = 2.0d;
        coordinates[34] = 12.0d;
        coordinates[35] = 2.0d;
        return coordinates;
    }

    private List<String> chainPathNodes() {
        ArrayList<String> nodes = new ArrayList<>(14);
        for (int i = 0; i <= 13; i++) {
            nodes.add("N" + i);
        }
        return List.copyOf(nodes);
    }
}
