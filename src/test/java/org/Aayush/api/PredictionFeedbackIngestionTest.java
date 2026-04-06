package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@Tag("integration")
@DisplayName("Prediction Feedback Ingestion Tests")
class PredictionFeedbackIngestionTest extends AbstractTaroApiSpringTest {
    @Autowired
    private PredictionTelemetryStore predictionTelemetryStore;

    @Override
    protected void resetAdditionalState() {
        predictionTelemetryStore.clear();
    }

    @Test
    @DisplayName("Route feedback endpoint accepts partial then complete outcome updates")
    void testRouteFeedbackEndpointAcceptsPartialThenCompleteOutcomeUpdates() throws Exception {
        String resultSetId = createRouteResultSetId();

        MvcResult partialResult = mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "PARTIAL",
                                  "observedAtTicks": 420
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode partialJson = objectMapper.readTree(partialResult.getResponse().getContentAsString());
        assertEquals("ROUTE", partialJson.path("resultKind").asText());
        assertEquals(resultSetId, partialJson.path("resultSetId").asText());
        assertEquals("PARTIAL", partialJson.path("outcomeStatus").asText());
        assertFalse(partialJson.path("complete").asBoolean());

        apiMutableClock.set(FutureApiTestConfiguration.BASE_INSTANT.plusSeconds(30));

        MvcResult completeResult = mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "COMPLETE",
                                  "observedAtTicks": 480,
                                  "observedArrivalTicks": 540,
                                  "observedCostSeconds": 120.5,
                                  "observationCount": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode completeJson = objectMapper.readTree(completeResult.getResponse().getContentAsString());
        assertEquals("COMPLETE", completeJson.path("outcomeStatus").asText());
        assertTrue(completeJson.path("complete").asBoolean());

        PredictionTelemetryStore.ExportRow exportRow = predictionTelemetryStore.findExportRow(
                CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                resultSetId
        ).orElseThrow();
        assertTrue(exportRow.complete());
        assertEquals("COMPLETE", exportRow.outcomeStatus());
        assertEquals(480L, exportRow.observedAtTicks());
        assertEquals(540L, exportRow.observedArrivalTicks());
        assertEquals(120.5d, exportRow.observedCostSeconds());
        assertEquals(1, exportRow.observationCount());
    }

    @Test
    @DisplayName("Feedback endpoint rejects caller mismatch")
    void testFeedbackEndpointRejectsCallerMismatch() throws Exception {
        String resultSetId = createRouteResultSetId();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "PARTIAL",
                                  "observedAtTicks": 420
                                }
                                """))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.UNAUTHORIZED_RESULT_ACCESS.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("High-volume ingestion evicts oldest telemetry rows deterministically")
    void testHighVolumeIngestionEvictsOldestTelemetryRowsDeterministically() {
        Clock clock = Clock.fixed(FutureApiTestConfiguration.BASE_INSTANT, ZoneOffset.UTC);
        PredictionTelemetryStore store = new PredictionTelemetryStore(
                clock,
                new PredictionTelemetryStore.Config(2, Duration.ofHours(1))
        );
        TopologyRuntimeSnapshot snapshot = FutureApiTestConfiguration.initialSnapshot();
        TopologyVersion topologyVersion = snapshot.getTopologyVersion();

        store.recordRoutePrediction("caller-a", snapshot, routeResultSet("route-1", "bundle-1", topologyVersion));
        store.recordRouteFeedback(
                "caller-a",
                "route-1",
                new PredictionFeedbackRequest(
                        PredictionFeedbackRequest.OutcomeStatus.PARTIAL,
                        100L,
                        null,
                        null,
                        null
                )
        );
        store.recordRoutePrediction("caller-a", snapshot, routeResultSet("route-2", "bundle-2", topologyVersion));
        store.recordRouteFeedback(
                "caller-a",
                "route-2",
                new PredictionFeedbackRequest(
                        PredictionFeedbackRequest.OutcomeStatus.PARTIAL,
                        200L,
                        null,
                        null,
                        null
                )
        );
        store.recordRoutePrediction("caller-a", snapshot, routeResultSet("route-3", "bundle-3", topologyVersion));
        store.recordRouteFeedback(
                "caller-a",
                "route-3",
                new PredictionFeedbackRequest(
                        PredictionFeedbackRequest.OutcomeStatus.PARTIAL,
                        300L,
                        null,
                        null,
                        null
                )
        );

        assertFalse(store.findExportRow(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, "route-1").isPresent());
        assertTrue(store.findExportRow(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, "route-2").isPresent());
        assertTrue(store.findExportRow(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, "route-3").isPresent());
        assertEquals(2, store.size());
    }

    private String createRouteResultSetId() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0,
                                  "horizonTicks": 3600,
                                  "preferredObjective": "EXPECTED_ETA",
                                  "topKAlternatives": 2,
                                  "resultTtlSeconds": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        return json.path("resultSetId").asText();
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
