package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@DisplayName("Route API Contract Tests")
class RouteApiContractTest extends AbstractTaroApiSpringTest {

    @Test
    @DisplayName("Route endpoint evaluates and returns retained summary metadata")
    void testRouteEndpointEvaluatesAndReturnsRetainedSummary() throws Exception {
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
        String resultSetId = json.path("resultSetId").asText();
        assertTrue(json.path("retained").asBoolean());
        assertEquals(resultSetId, json.path("summary").path("resultSetId").asText());
        assertEquals("bundle-api", json.path("summary").path("scenarioBundleId").asText());
        assertEquals("topo-api", json.path("topologyVersion").path("topologyVersion").asText());
    }

    @Test
    @DisplayName("Health endpoint reports active topology and API readiness")
    void testHealthEndpointReportsActiveTopology() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals("UP", json.path("status").asText());
        assertEquals("topo-api", json.path("activeTopologyVersion").asText());
        assertEquals("quarantine-topo-api:0", json.path("quarantineSnapshotId").asText());
    }
}
