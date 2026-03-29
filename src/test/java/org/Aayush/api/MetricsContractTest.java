package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.app.Main;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {Main.class, FutureApiTestConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Tag("integration")
@DisplayName("Metrics Contract Tests")
class MetricsContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FutureApiTestConfiguration.ApiMutableClock apiMutableClock;

    @Autowired
    private TopologyReloadCoordinator topologyReloadCoordinator;

    @BeforeEach
    void resetApiState() {
        apiMutableClock.set(FutureApiTestConfiguration.BASE_INSTANT);
        topologyReloadCoordinator.applyReload(FutureApiTestConfiguration.initialSnapshot());
    }

    @Test
    @DisplayName("Metrics endpoint reports serving reload and pressure signals")
    void testMetricsEndpointReportsServingReloadAndPressureSignals() throws Exception {
        String routeResultSetId = createRouteResultSetId();
        String matrixResultSetId = createMatrixResultSetId();

        mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", routeResultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/matrix/results/{resultSetId}/summary", matrixResultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", routeResultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "PARTIAL",
                                  "observedAtTicks": 420
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult metricsResult = mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(metricsResult.getResponse().getContentAsString());
        String overallStatus = json.path("alerts").path("overallStatus").asText();
        String routeLatencyStatus = json.path("alerts").path("routeLatencyStatus").asText();
        String matrixLatencyStatus = json.path("alerts").path("matrixLatencyStatus").asText();

        assertEquals(overallStatus, json.path("status").asText());
        assertEquals("topo-api", json.path("activeTopologyVersion").asText());
        assertTrue(json.path("callerScopedResultCount").asInt() >= 2);

        assertTrue(json.path("routeEvaluations").path("requestCount").asLong() >= 1L);
        assertTrue(json.path("matrixEvaluations").path("requestCount").asLong() >= 1L);
        assertTrue(json.path("routeLookups").path("requestCount").asLong() >= 1L);
        assertTrue(json.path("matrixLookups").path("requestCount").asLong() >= 1L);
        assertTrue(json.path("routeFeedback").path("requestCount").asLong() >= 1L);

        assertTrue(json.path("routeStore").path("entryCount").asInt() >= 1);
        assertTrue(json.path("matrixStore").path("entryCount").asInt() >= 1);
        assertTrue(json.path("routeStore").path("maxEntries").asLong() > 0L);
        assertTrue(json.path("matrixStore").path("maxEntries").asLong() > 0L);

        assertTrue(json.path("reload").path("validationSuccessCount").asLong() >= 1L);
        assertTrue(json.path("reload").path("appliedReloadCount").asLong() >= 1L);
        assertEquals("topo-api", json.path("reload").path("lastSuccessfulTopologyVersion").asText());

        assertEquals("HEALTHY", json.path("alerts").path("reloadStatus").asText());
        assertEquals("HEALTHY", json.path("alerts").path("retainedResultPressureStatus").asText());
        assertEquals("HEALTHY", json.path("alerts").path("parityStatus").asText());
        assertTrue(isRecognizedStatus(overallStatus));
        assertTrue(isRecognizedStatus(routeLatencyStatus));
        assertTrue(isRecognizedStatus(matrixLatencyStatus));
        assertEquals(
                Math.max(
                        Math.max(severity(json.path("alerts").path("reloadStatus").asText()), severity(json.path("alerts").path("retainedResultPressureStatus").asText())),
                        Math.max(
                                Math.max(severity(routeLatencyStatus), severity(matrixLatencyStatus)),
                                severity(json.path("alerts").path("parityStatus").asText())
                        )
                ),
                severity(overallStatus)
        );
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
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString()).path("resultSetId").asText();
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
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString()).path("resultSetId").asText();
    }

    private boolean isRecognizedStatus(String status) {
        return severity(status) >= 0;
    }

    private int severity(String status) {
        return switch (status) {
            case "HEALTHY" -> 0;
            case "WARN" -> 1;
            case "DEGRADED" -> 2;
            default -> -1;
        };
    }
}
