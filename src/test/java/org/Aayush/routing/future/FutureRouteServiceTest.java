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
        assertEquals(2, resultSet.getAlternatives().size());
        assertEquals(List.of("N0", "N1", "N3"), resultSet.getAlternatives().get(0).getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), resultSet.getAlternatives().get(1).getRoute().getPathExternalNodeIds());
        assertEquals(2, resultSet.getScenarioResults().size());
        assertTrue(store.get(resultSet.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Expected and robust winners can be aggregate-only compromise routes")
    void testAggregateOnlyCompromiseRouteSelection() {
        RoutingFixtureFactory.Fixture fixture = createCompromiseRouteFixture();
        RouteCore routeCore = createRouteCore(fixture);
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-v12")
                .topologyVersion("topo-compromise")
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-compromise")
                .changeSetHash("changes-compromise")
                .build();
        TopologyRuntimeSnapshot snapshot = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-topo-compromise"))
                .build();

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
        assertEquals(3, resultSet.getAlternatives().size());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getAlternatives().get(0).getRoute().getPathExternalNodeIds());
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N1", "N4"))));
        assertTrue(resultSet.getAlternatives().stream()
                .anyMatch(selection -> selection.getRoute().getPathExternalNodeIds().equals(List.of("N0", "N2", "N4"))));
        assertTrue(store.get(resultSet.getResultSetId()).isPresent());
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
