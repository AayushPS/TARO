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
@DisplayName("Matrix API Contract Tests")
class MatrixApiContractTest extends AbstractTaroApiSpringTest {

    @Test
    @DisplayName("Matrix endpoint evaluates and returns retained summary metadata")
    void testMatrixEndpointEvaluatesAndReturnsRetainedSummary() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/matrix")
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

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String resultSetId = createJson.path("resultSetId").asText();
        assertTrue(createJson.path("retained").asBoolean());
        assertEquals("N0", createJson.path("summary").path("aggregate").path("sourceExternalIds").get(0).asText());
        assertEquals("N3", createJson.path("summary").path("aggregate").path("targetExternalIds").get(1).asText());

        MvcResult summaryResult = mockMvc.perform(get("/api/v1/matrix/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode summaryJson = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
        assertEquals(resultSetId, summaryJson.path("resultSetId").asText());
        assertEquals("bundle-api", summaryJson.path("scenarioBundleId").asText());
    }
}
