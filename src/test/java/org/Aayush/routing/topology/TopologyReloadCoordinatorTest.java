package org.Aayush.routing.topology;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.EphemeralMatrixResultStore;
import org.Aayush.routing.future.EphemeralRouteResultStore;
import org.Aayush.routing.future.CandidateDensityCalibrationReport;
import org.Aayush.routing.future.CandidateDensityClass;
import org.Aayush.routing.future.FutureMatrixAggregate;
import org.Aayush.routing.future.FutureMatrixRequest;
import org.Aayush.routing.future.FutureMatrixResultSet;
import org.Aayush.routing.future.FutureMatrixScenarioResult;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.RouteShape;
import org.Aayush.routing.future.RouteSelectionProvenance;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Topology Reload Coordinator Tests")
class TopologyReloadCoordinatorTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Strict compatibility policy invalidates stale retained result sets on reload")
    void testInvalidateStaleResultsOnReload() {
        RouteCore routeCore = createRouteCore();
        TopologyRuntimeSnapshot snapshotV1 = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion("topo-1"))
                .failureQuarantine(new FailureQuarantine("q-reload"))
                .build();
        TopologyRuntimeSnapshot snapshotV2 = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion("topo-2"))
                .failureQuarantine(new FailureQuarantine("q-reload"))
                .build();

        EphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        routeStore.put(dummyRouteResultSet("rs-strict-route", snapshotV1.getTopologyVersion()));
        EphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK);
        matrixStore.put(dummyMatrixResultSet("rs-strict-matrix", snapshotV1.getTopologyVersion()));

        TopologyReloadCoordinator coordinator = new TopologyReloadCoordinator(
                snapshotV1,
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.<TopologyBoundResultStore>of(routeStore, matrixStore)
        );
        coordinator.applyReload(snapshotV2);

        assertFalse(routeStore.get("rs-strict-route").isPresent());
        assertFalse(matrixStore.get("rs-strict-matrix").isPresent());
    }

    @Test
    @DisplayName("Retain-until-expiry policy keeps retained result sets readable across reload")
    void testRetainResultsAcrossReload() {
        RouteCore routeCore = createRouteCore();
        TopologyRuntimeSnapshot snapshotV1 = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion("topo-1"))
                .failureQuarantine(new FailureQuarantine("q-reload"))
                .build();
        TopologyRuntimeSnapshot snapshotV2 = TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion("topo-2"))
                .failureQuarantine(new FailureQuarantine("q-reload"))
                .build();

        EphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(FIXED_CLOCK);
        routeStore.put(dummyRouteResultSet("rs-retain-route", snapshotV1.getTopologyVersion()));
        EphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(FIXED_CLOCK);
        matrixStore.put(dummyMatrixResultSet("rs-retain-matrix", snapshotV1.getTopologyVersion()));

        TopologyReloadCoordinator coordinator = new TopologyReloadCoordinator(
                snapshotV1,
                ReloadCompatibilityPolicy.retainUntilExpiry(),
                List.<TopologyBoundResultStore>of(routeStore, matrixStore)
        );
        coordinator.applyReload(snapshotV2);

        assertTrue(routeStore.get("rs-retain-route").isPresent());
        assertTrue(matrixStore.get("rs-retain-matrix").isPresent());
    }

    private RouteCore createRouteCore() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                null,
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
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

    private TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion("model-v13")
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
    }

    private FutureRouteResultSet dummyRouteResultSet(String resultSetId, TopologyVersion topologyVersion) {
        RouteShape shape = RouteShape.builder()
                .reachable(true)
                .departureTicks(0L)
                .pathNode("N0")
                .pathNode("N1")
                .build();
        ScenarioRouteSelection selection = ScenarioRouteSelection.builder()
                .route(shape)
                .expectedCost(1.0f)
                .p50Cost(1.0f)
                .p90Cost(1.0f)
                .minCost(1.0f)
                .maxCost(1.0f)
                .minArrivalTicks(1L)
                .maxArrivalTicks(1L)
                .optimalityProbability(1.0d)
                .dominantScenarioId("baseline")
                .dominantScenarioLabel("baseline")
                .routeSelectionProvenance(RouteSelectionProvenance.SCENARIO_OPTIMAL)
                .build();
        ScenarioBundle bundle = ScenarioBundle.builder()
                .scenarioBundleId("bundle-" + resultSetId)
                .generatedAt(FIXED_CLOCK.instant())
                .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                .horizonTicks(3_600L)
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-reload:0")
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .build())
                .build();
        return FutureRouteResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(FIXED_CLOCK.instant())
                .expiresAt(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                .request(FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N1")
                                .departureTicks(0L)
                                .build())
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-reload:0")
                .scenarioBundle(bundle)
                .candidateDensityCalibrationReport(CandidateDensityCalibrationReport.builder()
                        .policyId("b5-density-v2")
                        .scenarioCount(1)
                        .scenarioOptimalRouteCount(1)
                        .uniqueScenarioOptimalRouteCount(1)
                        .uniqueCandidateRouteCount(1)
                        .aggregateAddedCandidateCount(0)
                        .expectedRouteAggregateOnly(false)
                        .robustRouteAggregateOnly(false)
                        .selectedAlternativeCount(1)
                        .scenarioCoverageRatio(1.0d)
                        .candidateCoverageRatio(1.0d)
                        .aggregateExpansionRatio(0.0d)
                        .densityClass(CandidateDensityClass.LOW_DENSITY)
                        .build())
                .expectedRoute(selection)
                .robustRoute(selection)
                .alternatives(List.of(selection))
                .scenarioResults(List.of())
                .build();
    }

    private FutureMatrixResultSet dummyMatrixResultSet(String resultSetId, TopologyVersion topologyVersion) {
        ScenarioBundle bundle = ScenarioBundle.builder()
                .scenarioBundleId("bundle-" + resultSetId)
                .generatedAt(FIXED_CLOCK.instant())
                .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                .horizonTicks(3_600L)
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-reload:0")
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .build())
                .build();
        return FutureMatrixResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(FIXED_CLOCK.instant())
                .expiresAt(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                .request(FutureMatrixRequest.builder()
                        .matrixRequest(MatrixRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N1")
                                .departureTicks(0L)
                                .build())
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-reload:0")
                .scenarioBundle(bundle)
                .aggregate(FutureMatrixAggregate.builder()
                        .sourceExternalIds(List.of("N0"))
                        .targetExternalIds(List.of("N1"))
                        .reachabilityProbabilities(new double[][]{{1.0d}})
                        .expectedCosts(new float[][]{{1.0f}})
                        .p50Costs(new float[][]{{1.0f}})
                        .p90Costs(new float[][]{{1.0f}})
                        .minCosts(new float[][]{{1.0f}})
                        .maxCosts(new float[][]{{1.0f}})
                        .minArrivalTicks(new long[][]{{1L}})
                        .maxArrivalTicks(new long[][]{{1L}})
                        .aggregationNote("dummy")
                        .build())
                .scenarioResult(FutureMatrixScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .matrix(org.Aayush.routing.core.MatrixResponse.builder()
                                .sourceExternalIds(List.of("N0"))
                                .targetExternalIds(List.of("N1"))
                                .reachable(new boolean[][]{{true}})
                                .totalCosts(new float[][]{{1.0f}})
                                .arrivalTicks(new long[][]{{1L}})
                                .implementationNote("dummy")
                                .build())
                        .build())
                .build();
    }
}
