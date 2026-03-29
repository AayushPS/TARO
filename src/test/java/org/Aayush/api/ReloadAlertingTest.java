package org.Aayush.api;

import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.CandidateDensityCalibrationReport;
import org.Aayush.routing.future.CandidateDensityClass;
import org.Aayush.routing.future.FutureRouteScenarioResult;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.RouteSelectionProvenance;
import org.Aayush.routing.future.RouteShape;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.ReloadCompatibilityPolicy;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.heuristic.HeuristicType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Reload Alerting Tests")
class ReloadAlertingTest {
    @Test
    @DisplayName("Reload alerts differentiate healthy validation from validation failure")
    void testReloadAlertsDifferentiateHealthyValidationFromValidationFailure() {
        Clock clock = Clock.fixed(FutureApiTestConfiguration.BASE_INSTANT, ZoneOffset.UTC);
        OperationalMetricsService service = new OperationalMetricsService(
                clock,
                () -> null,
                () -> null,
                () -> null,
                () -> 0,
                OperationalMetricsService.Thresholds.defaults()
        );
        TopologyReloadCoordinator coordinator = new TopologyReloadCoordinator(
                FutureApiTestConfiguration.initialSnapshot(),
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(),
                service
        );

        coordinator.validateReload(FutureApiTestConfiguration.initialSnapshot());
        assertEquals("HEALTHY", service.metrics().alerts().reloadStatus());

        TopologyRuntimeSnapshot invalidSnapshot = TopologyRuntimeSnapshot.builder()
                .topologyVersion(topologyVersion("topo-invalid"))
                .failureQuarantine(new FailureQuarantine("quarantine-invalid"))
                .build();
        assertThrows(NullPointerException.class, () -> coordinator.validateReload(invalidSnapshot));

        OperationalMetricsResponse metrics = service.metrics();
        assertEquals("DEGRADED", metrics.alerts().reloadStatus());
        assertTrue(metrics.reload().validationFailureCount() >= 1L);
        assertTrue(metrics.reload().lastFailureReason().contains("snapshot.routeCore"));
    }

    @Test
    @DisplayName("Retained result pressure alert uses store usage ratios")
    void testRetainedResultPressureAlertUsesStoreUsageRatios() {
        Clock clock = Clock.fixed(FutureApiTestConfiguration.BASE_INSTANT, ZoneOffset.UTC);
        InMemoryEphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(
                clock,
                new InMemoryEphemeralRouteResultStore.Config(4L, 1_024_000L, 512_000L)
        );
        InMemoryEphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(clock);
        OperationalMetricsService service = new OperationalMetricsService(
                clock,
                FutureApiTestConfiguration::initialSnapshot,
                () -> routeStore,
                () -> matrixStore,
                () -> 0,
                new OperationalMetricsService.Thresholds(25.0d, 100.0d, 50.0d, 200.0d, 0.50d, 0.75d)
        );

        TopologyVersion topologyVersion = FutureApiTestConfiguration.initialSnapshot().getTopologyVersion();
        routeStore.put(routeResultSet("route-1", topologyVersion));
        routeStore.put(routeResultSet("route-2", topologyVersion));
        routeStore.put(routeResultSet("route-3", topologyVersion));

        OperationalMetricsResponse metrics = service.metrics();
        assertEquals("DEGRADED", metrics.alerts().retainedResultPressureStatus());
        assertTrue(metrics.routeStore().entryUsageRatio() >= 0.75d);
        assertEquals(3, metrics.routeStore().entryCount());
    }

    @Test
    @DisplayName("Route matrix latency and parity alerts expose rollback posture")
    void testRouteMatrixLatencyAndParityAlertsExposeRollbackPosture() {
        Clock clock = Clock.fixed(FutureApiTestConfiguration.BASE_INSTANT, ZoneOffset.UTC);
        OperationalMetricsService service = new OperationalMetricsService(
                clock,
                FutureApiTestConfiguration::initialSnapshot,
                () -> null,
                () -> null,
                () -> 0,
                new OperationalMetricsService.Thresholds(1.0d, 5.0d, 1.0d, 5.0d, 0.75d, 0.90d)
        );

        service.recordRouteEvaluation(Duration.ofMillis(10).toNanos(), true);
        service.recordMatrixEvaluation(Duration.ofMillis(8).toNanos(), true);
        service.markParityContractFailure("TemporalFidelityContractTest", "parity drift");

        OperationalMetricsResponse metrics = service.metrics();
        OperationalGovernanceResponse governance = service.governance();

        assertEquals("DEGRADED", metrics.alerts().routeLatencyStatus());
        assertEquals("DEGRADED", metrics.alerts().matrixLatencyStatus());
        assertEquals("DEGRADED", metrics.alerts().parityStatus());
        assertEquals("DEGRADED", metrics.alerts().overallStatus());
        assertTrue(governance.servingGovernance().rollbackTriggers().contains("route_latency_regression"));
        assertTrue(governance.servingGovernance().rollbackTriggers().contains("matrix_latency_regression"));
        assertTrue(governance.servingGovernance().rollbackTriggers().contains("parity_drift"));
        assertTrue(governance.servingGovernance().rollbackTriggers().contains("contract_test_failure"));
    }

    private FutureRouteResultSet routeResultSet(String resultSetId, TopologyVersion topologyVersion) {
        Instant createdAt = FutureApiTestConfiguration.BASE_INSTANT;
        ScenarioRouteSelection expected = routeSelection(
                "baseline",
                1.0d,
                List.of("N0", "N1", "N3"),
                100.0f,
                120L,
                RouteSelectionProvenance.SCENARIO_OPTIMAL,
                List.of("baseline")
        );
        ScenarioRouteSelection alternative = routeSelection(
                "baseline",
                1.0d,
                List.of("N0", "N2", "N3"),
                110.0f,
                130L,
                RouteSelectionProvenance.AGGREGATE_OBJECTIVE,
                List.of("baseline")
        );
        return FutureRouteResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(createdAt)
                .expiresAt(createdAt.plus(Duration.ofMinutes(10)))
                .request(FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N3")
                                .departureTicks(0L)
                                .build())
                        .horizonTicks(3_600L)
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("quarantine-" + topologyVersion.getTopologyVersion())
                .scenarioBundle(ScenarioBundle.builder()
                        .scenarioBundleId("bundle-" + resultSetId)
                        .generatedAt(createdAt)
                        .validUntil(createdAt.plus(Duration.ofMinutes(10)))
                        .horizonTicks(3_600L)
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId("quarantine-" + topologyVersion.getTopologyVersion())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                        .probability(1.0d)
                        .build())
                .build())
                .candidateDensityCalibrationReport(CandidateDensityCalibrationReport.builder()
                        .policyId("b5-density-v2")
                        .scenarioCount(1)
                        .scenarioOptimalRouteCount(1)
                        .uniqueScenarioOptimalRouteCount(1)
                        .uniqueCandidateRouteCount(2)
                        .aggregateAddedCandidateCount(1)
                        .expectedRouteAggregateOnly(false)
                        .robustRouteAggregateOnly(true)
                        .selectedAlternativeCount(1)
                        .scenarioCoverageRatio(1.0d)
                        .candidateCoverageRatio(2.0d)
                        .aggregateExpansionRatio(1.0d)
                        .densityClass(CandidateDensityClass.HIGH_DENSITY)
                        .build())
                .expectedRoute(expected)
                .robustRoute(alternative)
                .alternative(alternative)
                .scenarioResult(FutureRouteScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .route(routeResponse(List.of("N0", "N1", "N3"), 100.0f, 120L))
                        .build())
                .build();
    }

    private ScenarioRouteSelection routeSelection(
            String scenarioId,
            double probability,
            List<String> path,
            float cost,
            long arrivalTicks,
            RouteSelectionProvenance provenance,
            List<String> explanationTags
    ) {
        return ScenarioRouteSelection.builder()
                .route(RouteShape.builder()
                        .reachable(true)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .pathExternalNodeIds(path)
                        .build())
                .expectedCost(cost)
                .p50Cost(cost)
                .p90Cost(cost + 1.0f)
                .minCost(cost)
                .maxCost(cost + 1.0f)
                .minArrivalTicks(arrivalTicks)
                .maxArrivalTicks(arrivalTicks + 1L)
                .optimalityProbability(probability)
                .expectedRegret(provenance == RouteSelectionProvenance.AGGREGATE_OBJECTIVE ? 0.5f : 0.0f)
                .etaBandLowerArrivalTicks(arrivalTicks)
                .etaBandUpperArrivalTicks(arrivalTicks + 1L)
                .dominantScenarioId(scenarioId)
                .dominantScenarioProbability(probability)
                .dominantScenarioLabel(scenarioId)
                .routeSelectionProvenance(provenance)
                .explanationTags(explanationTags)
                .build();
    }

    private RouteResponse routeResponse(List<String> path, float totalCost, long arrivalTicks) {
        return RouteResponse.builder()
                .reachable(true)
                .departureTicks(0L)
                .arrivalTicks(arrivalTicks)
                .totalCost(totalCost)
                .settledStates(path.size())
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .pathExternalNodeIds(path)
                .build();
    }

    private TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion("model-" + topologyId)
                .topologyVersion(topologyId)
                .generatedAt(FutureApiTestConfiguration.BASE_INSTANT)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
    }
}
