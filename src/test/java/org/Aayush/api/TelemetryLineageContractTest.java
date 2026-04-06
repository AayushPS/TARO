package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;
import org.Aayush.routing.traits.registry.ResolvedTraitBundleContext;
import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@Tag("integration")
@DisplayName("Telemetry Lineage Contract Tests")
class TelemetryLineageContractTest extends AbstractTaroApiSpringTest {
    @Autowired
    private PredictionTelemetryStore predictionTelemetryStore;

    @Override
    protected void resetAdditionalState() {
        predictionTelemetryStore.clear();
    }

    @Test
    @DisplayName("Served predictions capture stable route and matrix lineage")
    void testServedPredictionsCaptureStableRouteAndMatrixLineage() throws Exception {
        String routeResultSetId = createRouteResultSetId();
        String matrixResultSetId = createMatrixResultSetId();

        PredictionTelemetryStore.ExportRow routeRow = predictionTelemetryStore.findExportRow(
                CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                routeResultSetId
        ).orElseThrow();
        PredictionTelemetryStore.ExportRow matrixRow = predictionTelemetryStore.findExportRow(
                CallerScopedRetainedResultRegistry.ResultKind.MATRIX,
                matrixResultSetId
        ).orElseThrow();

        ResolvedTraitBundleContext traitBundleContext =
                topologyReloadCoordinator.currentSnapshot().getRouteCore().resolvedTraitBundleContext();
        ResolvedExecutionProfileContext executionProfileContext =
                topologyReloadCoordinator.currentSnapshot().getRouteCore().executionProfileContext();

        assertEquals(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, routeRow.resultKind());
        assertEquals("topo-api", routeRow.topologyVersionId());
        assertEquals("model-api", routeRow.modelVersion());
        assertEquals("lineage-topo-api", routeRow.sourceDataLineageHash());
        assertEquals("change-topo-api", routeRow.changeSetHash());
        assertEquals("bundle-api", routeRow.scenarioBundleId());
        assertEquals(2, routeRow.scenarioCount());
        assertEquals(List.of("baseline", "incident_persists"), routeRow.scenarioIds());
        assertEquals(List.of("baseline", "incident_persists"), routeRow.scenarioLabels());
        assertEquals(List.of(0.6d, 0.4d), routeRow.scenarioProbabilities());
        assertEquals("EXPECTED_ETA", routeRow.preferredObjective());
        assertEquals(2, routeRow.topKAlternatives());
        assertEquals(0L, routeRow.departureTicks());
        assertEquals(3_600L, routeRow.horizonTicks());
        assertEquals(traitBundleContext.getTraitHash(), routeRow.traitHash());
        assertEquals(traitBundleContext.getBundleId(), routeRow.traitBundleId());
        assertEquals(executionProfileContext.getProfileId(), routeRow.executionProfileId());
        assertEquals("quarantine-topo-api:0", routeRow.quarantineSnapshotId());
        assertNotEquals("caller-a", routeRow.callerHash());
        assertNotNull(routeRow.predictedExpectedCostSeconds());
        assertNotNull(routeRow.predictedRobustCostSeconds());

        assertEquals(CallerScopedRetainedResultRegistry.ResultKind.MATRIX, matrixRow.resultKind());
        assertEquals("topo-api", matrixRow.topologyVersionId());
        assertEquals("bundle-api", matrixRow.scenarioBundleId());
        assertEquals(1, matrixRow.matrixSourceCount());
        assertEquals(2, matrixRow.matrixTargetCount());
        assertEquals(0L, matrixRow.departureTicks());
        assertEquals(3_600L, matrixRow.horizonTicks());
        assertEquals(traitBundleContext.getTraitHash(), matrixRow.traitHash());
        assertEquals(executionProfileContext.getProfileId(), matrixRow.executionProfileId());
        assertNotNull(matrixRow.predictedExpectedCostSeconds());
        assertNotNull(matrixRow.predictedRobustCostSeconds());
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

    private String createMatrixResultSetId() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/matrix")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sources": [{"externalId": "N0"}],
                                  "targets": [{"externalId": "N2"}, {"externalId": "N3"}],
                                  "departureTicks": 0,
                                  "horizonTicks": 3600,
                                  "resultTtlSeconds": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        return json.path("resultSetId").asText();
    }
}
