package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.MatrixRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        ScenarioBundleResolver resolver = (request, edgeGraph, resolvedTopologyVersion, quarantineSnapshot, clock) ->
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
