package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.app.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {Main.class, FutureApiTestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "taro.demo-topology.enabled=false")
@AutoConfigureMockMvc
@DisplayName("API Request Validation Tests")
class ApiRequestValidationTest {
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
    @DisplayName("Invalid route payload returns structured bad request")
    void testInvalidRoutePayloadReturnsStructuredBadRequest() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.INVALID_REQUEST.name(), json.path("code").asText());
        assertTrue(json.path("message").asText().contains("source"));
    }

    @Test
    @DisplayName("Invalid objective returns structured bad request")
    void testInvalidObjectiveReturnsStructuredBadRequest() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0,
                                  "preferredObjective": "FASTESTISH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.INVALID_REQUEST.name(), json.path("code").asText());
        assertTrue(json.path("message").asText().contains("preferredObjective"));
    }

    @Test
    @DisplayName("Missing caller header returns structured bad request")
    void testMissingCallerHeaderReturnsStructuredBadRequest() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.INVALID_REQUEST.name(), json.path("code").asText());
        assertTrue(json.path("message").asText().contains(FutureRoutingApiFacade.CALLER_HEADER));
    }
}
