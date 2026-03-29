package org.Aayush.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Tag("integration")
@DisplayName("Demo Future Routing Bootstrap")
class DemoFutureRoutingBootstrapTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Main boots with a live demo topology snapshot and serves future route requests")
    void testMainBootsWithDemoTopologyAndRouteApi() throws Exception {
        MvcResult healthResult = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode healthJson = objectMapper.readTree(healthResult.getResponse().getContentAsString());
        assertEquals(true, healthJson.path("routeServiceConfigured").asBoolean());
        assertEquals(true, healthJson.path("matrixServiceConfigured").asBoolean());
        assertEquals("topo-demo", healthJson.path("activeTopologyVersion").asText());

        MvcResult routeResult = mockMvc.perform(post("/api/v1/route")
                        .header("X-Taro-Caller-Id", "bootstrap-demo")
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

        JsonNode routeJson = objectMapper.readTree(routeResult.getResponse().getContentAsString());
        assertNotNull(routeJson.path("resultSetId").asText());
        assertEquals(true, routeJson.path("retained").asBoolean());
        assertEquals("topo-demo", routeJson.path("topologyVersion").path("topologyVersion").asText());
        assertFalse(routeJson.path("summary").isMissingNode());
    }
}
