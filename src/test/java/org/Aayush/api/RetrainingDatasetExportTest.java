package org.Aayush.api;

import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Retraining Dataset Export Tests")
class RetrainingDatasetExportTest {
    @Test
    @DisplayName("Export rows stay joined and filterable by lineage")
    void testExportRowsStayJoinedAndFilterableByLineage() {
        Clock clock = Clock.fixed(FutureApiTestConfiguration.BASE_INSTANT, ZoneOffset.UTC);
        PredictionTelemetryStore store = new PredictionTelemetryStore(
                clock,
                new PredictionTelemetryStore.Config(8, Duration.ofHours(1))
        );

        TopologyRuntimeSnapshot snapshotA = FutureApiTestConfiguration.initialSnapshot();
        TopologyRuntimeSnapshot snapshotB = snapshotWithTopology(snapshotA, "topo-export-b");
        String traitHash = snapshotA.getRouteCore().resolvedTraitBundleContext().getTraitHash();

        store.recordRoutePrediction("caller-a", snapshotA, routeResultSet("route-a", "bundle-a", snapshotA.getTopologyVersion()));
        store.recordRouteFeedback(
                "caller-a",
                "route-a",
                new PredictionFeedbackRequest(
                        PredictionFeedbackRequest.OutcomeStatus.COMPLETE,
                        500L,
                        560L,
                        120.0d,
                        1
                )
        );

        store.recordRoutePrediction("caller-a", snapshotB, routeResultSet("route-b", "bundle-b", snapshotB.getTopologyVersion()));
        store.recordRouteFeedback(
                "caller-a",
                "route-b",
                new PredictionFeedbackRequest(
                        PredictionFeedbackRequest.OutcomeStatus.PARTIAL,
                        700L,
                        null,
                        null,
                        null
                )
        );

        List<PredictionTelemetryStore.ExportRow> filteredRows = store.exportRows(
                new PredictionTelemetryStore.ExportFilter(
                        "caller-a",
                        CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                        "topo-api",
                        "bundle-a",
                        traitHash,
                        true
                )
        );

        assertEquals(1, filteredRows.size());
        PredictionTelemetryStore.ExportRow filteredRow = filteredRows.getFirst();
        assertEquals("route-a", filteredRow.resultSetId());
        assertEquals("topo-api", filteredRow.topologyVersionId());
        assertEquals("bundle-a", filteredRow.scenarioBundleId());
        assertEquals(traitHash, filteredRow.traitHash());
        assertEquals("COMPLETE", filteredRow.outcomeStatus());
        assertEquals(120.0d, filteredRow.observedCostSeconds());

        List<PredictionTelemetryStore.ExportRow> allRows = store.exportRows(
                new PredictionTelemetryStore.ExportFilter(
                        "caller-a",
                        CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                        null,
                        null,
                        null,
                        false
                )
        );
        assertEquals(List.of("route-a", "route-b"), allRows.stream().map(PredictionTelemetryStore.ExportRow::resultSetId).toList());
    }

    private TopologyRuntimeSnapshot snapshotWithTopology(
            TopologyRuntimeSnapshot template,
            String topologyId
    ) {
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-" + topologyId)
                .topologyVersion(topologyId)
                .generatedAt(FutureApiTestConfiguration.BASE_INSTANT)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
        return TopologyRuntimeSnapshot.builder()
                .routeCore(template.getRouteCore())
                .topologyVersion(topologyVersion)
                .failureQuarantine(template.getFailureQuarantine())
                .build();
    }

    private FutureRouteResultSet routeResultSet(
            String resultSetId,
            String scenarioBundleId,
            TopologyVersion topologyVersion
    ) {
        Instant createdAt = FutureApiTestConfiguration.BASE_INSTANT;
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
                        .scenarioBundleId(scenarioBundleId)
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
                .expectedRoute(ScenarioRouteSelection.builder()
                        .expectedCost(100.0f)
                        .p90Cost(120.0f)
                        .build())
                .robustRoute(ScenarioRouteSelection.builder()
                        .expectedCost(110.0f)
                        .p90Cost(130.0f)
                        .build())
                .build();
    }
}
