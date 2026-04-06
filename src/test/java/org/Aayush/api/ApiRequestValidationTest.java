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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@DisplayName("API Request Validation Tests")
class ApiRequestValidationTest extends AbstractTaroApiSpringTest {

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
